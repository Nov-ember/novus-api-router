package com.example.novusapirouter.server.service;

import com.example.novusapirouter.common.exception.RouterException;
import com.example.novusapirouter.common.property.RouterProperties;
import com.example.novusapirouter.model.dto.ChatCompletionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelRouter {

    private static final DefaultRedisScript<String> SESSION_AFFINITY_SCRIPT = new DefaultRedisScript<>();

    static {
        SESSION_AFFINITY_SCRIPT.setLocation(new ClassPathResource("lua/session-affinity.lua"));
        SESSION_AFFINITY_SCRIPT.setResultType(String.class);
    }

    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final String SESSION_AFFINITY_REDIS_KEY_PREFIX = "router:affinity:";
    private static final String PREFIX_ROUTING_REDIS_KEY_PREFIX = "router:prefix:";

    private final StringRedisTemplate redisTemplate;
    private final RouterProperties routerProperties;
    private final ObjectMapper objectMapper;

    public RouterProperties.Route selectRoute(Long apiKeyId, String modelAlias, String sessionId, List<ChatCompletionRequest.Message> messages) {
        if (sessionId != null && !SESSION_ID_PATTERN.matcher(sessionId).matches()) {
            throw new RouterException(
                    HttpStatus.BAD_REQUEST,
                    "X-Session-Id 格式不支持",
                    "invalid_request_error",
                    "invalid_session_id",
                    "X-Session-Id"
            );
        }

        RouterProperties.ModelConfig modelConfig = routerProperties.getModelAliases().get(modelAlias);
        if (modelConfig == null) {
            throw new RouterException(
                    HttpStatus.NOT_FOUND,
                    "模型不存在",
                    "invalid_request_error",
                    "model_not_found",
                    "model"
            );
        }

        List<RouterProperties.Route> routes = modelConfig.getRoutes();

        String fingerprint = createPrefixFingerprint(messages);
        RouterProperties.Route candidate = findPrefixRoute(apiKeyId, modelAlias, fingerprint, routes);

        if (candidate == null) {
            candidate = routes.get(ThreadLocalRandom.current().nextInt(routes.size()));
        }

        if (sessionId == null) {
            return candidate;
        }

        Long sessionAffinityTtlSeconds = routerProperties.getSessionAffinityTtlSeconds();
        String redisKey = getSessionAffinityRedisKey(apiKeyId, modelAlias, sessionId);

        List<String> args = new ArrayList<>();
        args.add(sessionAffinityTtlSeconds.toString());
        args.add(candidate.getId().toString());
        for (RouterProperties.Route route : routes) {
            args.add(route.getId().toString());
        }

        long selectedId;
        try {
            selectedId = Long.parseLong(redisTemplate.execute(SESSION_AFFINITY_SCRIPT, List.of(redisKey), args.toArray()));
        } catch (DataAccessException e) {
            throw new RouterException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "路由服务暂时不可用，请稍后重试",
                    "service_unavailable_error",
                    "routing_unavailable",
                    null
            );
        }

        return routes.stream()
                .filter(route -> route.getId().equals(selectedId))
                .findFirst()
                .orElseThrow();
    }

    public void savePrefixRouteMapping(Long apiKeyId, String modelAlias, List<ChatCompletionRequest.Message> messages, Long routeId) {
        String fingerprint = createPrefixFingerprint(messages);
        if (fingerprint == null) {
            return;
        }
        String redisKey = getPrefixRoutingRedisKey(apiKeyId, modelAlias, fingerprint);
        try {
            redisTemplate.opsForValue().set(redisKey, routeId.toString(), Duration.ofSeconds(routerProperties.getPrefixRoutingTtlSeconds()));
        } catch (DataAccessException e) {
            log.warn("前缀路由记录失败，apiKeyId={}，modelAlias={}，routeId={}", apiKeyId, modelAlias, routeId, e);
        }
    }

    private String getSessionAffinityRedisKey(Long apiKeyId, String modelAlias, String sessionId) {
        return SESSION_AFFINITY_REDIS_KEY_PREFIX + apiKeyId + ":" + modelAlias + ":" + sessionId;
    }

    private String createPrefixFingerprint(List<ChatCompletionRequest.Message> messages) {
        List<ChatCompletionRequest.Message> prefix = extractPrefix(messages);
        if (prefix.isEmpty()) {
            return null;
        }
        String prefixJson = objectMapper.writeValueAsString(prefix);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(prefixJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("运行环境不支持 SHA-256", e);
        }
    }

    private List<ChatCompletionRequest.Message> extractPrefix(List<ChatCompletionRequest.Message> messages) {
        int remaining = routerProperties.getPrefixLengthChars();
        List<ChatCompletionRequest.Message> prefix = new ArrayList<>();

        for (ChatCompletionRequest.Message message : messages) {
            String content = message.getContent();

            if (content.length() >= remaining) {
                prefix.add(new ChatCompletionRequest.Message(message.getRole(), content.substring(0, remaining)));
                return prefix;
            }

            prefix.add(message);
            remaining -= content.length();
        }

        return List.of();
    }

    private RouterProperties.Route findPrefixRoute(Long apiKeyId, String modelAlias, String fingerprint, List<RouterProperties.Route> routes) {
        if (fingerprint == null) {
            return null;
        }
        String redisKey = getPrefixRoutingRedisKey(apiKeyId, modelAlias, fingerprint);
        String routeId;
        try {
            routeId = redisTemplate.opsForValue().get(redisKey);
        } catch (DataAccessException e) {
            log.warn("前缀路由查询失败，apiKeyId={}，modelAlias={}", apiKeyId, modelAlias, e);
            return null;
        }

        if (routeId == null) {
            return null;
        }

        return routes.stream()
                .filter(route -> route.getId().toString().equals(routeId))
                .findFirst()
                .orElse(null);
    }

    private String getPrefixRoutingRedisKey(Long apiKeyId, String modelAlias, String fingerprint) {
        return PREFIX_ROUTING_REDIS_KEY_PREFIX + apiKeyId + ":" + modelAlias + ":" + fingerprint;
    }
}