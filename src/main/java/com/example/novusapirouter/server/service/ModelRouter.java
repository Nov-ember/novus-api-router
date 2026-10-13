package com.example.novusapirouter.server.service;

import com.example.novusapirouter.common.exception.RouterException;
import com.example.novusapirouter.common.property.RouterProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ModelRouter {

    private static final DefaultRedisScript<String> AFFINITY_SCRIPT = new DefaultRedisScript<>();

    static {
        AFFINITY_SCRIPT.setLocation(new ClassPathResource("lua/session-affinity.lua"));
        AFFINITY_SCRIPT.setResultType(String.class);
    }

    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final String REDIS_KEY_PREFIX = "router:affinity:";

    private final StringRedisTemplate redisTemplate;
    private final RouterProperties routerProperties;

    public RouterProperties.Route selectRoute(Long apiKeyId, String modelAlias, String sessionId) {
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

        RouterProperties.Route candidate = routes.get(ThreadLocalRandom.current().nextInt(routes.size()));
        if (sessionId == null) {
            return candidate;
        }

        Long sessionAffinityTtlSeconds = routerProperties.getSessionAffinityTtlSeconds();
        String redisKey = getRedisKey(apiKeyId, modelAlias, sessionId);

        List<String> args = new ArrayList<>();
        args.add(sessionAffinityTtlSeconds.toString());
        args.add(candidate.getId().toString());
        for (RouterProperties.Route route : routes) {
            args.add(route.getId().toString());
        }

        long selectedId;
        try {
            selectedId = Long.parseLong(redisTemplate.execute(AFFINITY_SCRIPT, List.of(redisKey), args.toArray()));
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

    private String getRedisKey(Long apiKeyId, String modelAlias, String sessionId) {
        return REDIS_KEY_PREFIX + apiKeyId + ":" + sessionId + ":" + modelAlias;
    }
}