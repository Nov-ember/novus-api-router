package com.example.novusapirouter.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.novusapirouter.common.context.ApiKeyIdentity;
import com.example.novusapirouter.common.context.UserContext;
import com.example.novusapirouter.common.exception.BusinessException;
import com.example.novusapirouter.common.exception.RouterException;
import com.example.novusapirouter.common.property.RouterProperties;
import com.example.novusapirouter.model.dto.ApiKeyNameDTO;
import com.example.novusapirouter.model.entity.ApiKey;
import com.example.novusapirouter.model.entity.User;
import com.example.novusapirouter.model.vo.ApiKeyVO;
import com.example.novusapirouter.server.mapper.ApiKeyMapper;
import com.example.novusapirouter.server.mapper.UserMapper;
import com.example.novusapirouter.server.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApiKeyServiceImpl implements ApiKeyService {

    private static final String KEY_PREFIX = "sk-novus-";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int SUFFIX_LENGTH = 4;

    private final ApiKeyMapper apiKeyMapper;
    private final UserMapper userMapper;
    private final RouterProperties routerProperties;

    @Override
    @Transactional
    public String createApiKey(ApiKeyNameDTO apiKeyNameDTO) {
        if (userMapper.update(new LambdaUpdateWrapper<User>()
                .eq(User::getId, UserContext.getUid())
                .lt(User::getActiveKeyCount, routerProperties.getMaxActiveKeysPerUser())
                .setIncrBy(User::getActiveKeyCount, 1)) != 1) {
            throw new BusinessException("API key 数量已达上限");
        }

        String key = generateKey();

        ApiKey apiKey = ApiKey.builder()
                .userId(UserContext.getUid())
                .name(apiKeyNameDTO.getName())
                .keyHash(hashKey(key))
                .keySuffix(key.substring(key.length() - SUFFIX_LENGTH))
                .status(ApiKey.ACTIVE)
                .build();

        if (apiKeyMapper.insert(apiKey) != 1) {
            throw new BusinessException("API key 创建失败");
        }

        return key;
    }

    @Override
    public List<ApiKeyVO> listApiKeys() {
        return apiKeyMapper.selectList(new LambdaQueryWrapper<ApiKey>()
                        .eq(ApiKey::getUserId, UserContext.getUid())
                        .eq(ApiKey::getStatus, ApiKey.ACTIVE)
                        .orderByDesc(ApiKey::getCreateTime)
                        .orderByDesc(ApiKey::getId)).stream()
                .map(this::convertApiKeyToApiKeyVO)
                .toList();
    }

    @Override
    public void renameApiKey(Long id, ApiKeyNameDTO apiKeyNameDTO) {
        ApiKey apiKey = apiKeyMapper.selectById(id);
        checkApiKeyExists(apiKey);
        checkOwnerShip(apiKey.getUserId());

        if (apiKeyMapper.update(new LambdaUpdateWrapper<ApiKey>()
                .eq(ApiKey::getId, id)
                .eq(ApiKey::getUserId, UserContext.getUid())
                .eq(ApiKey::getStatus, ApiKey.ACTIVE)
                .set(ApiKey::getName, apiKeyNameDTO.getName())) != 1) {
            throw new BusinessException("API Key 名称更新失败");
        }
    }

    @Override
    @Transactional
    public void revokeApiKey(Long id) {
        ApiKey apiKey = apiKeyMapper.selectById(id);
        checkApiKeyExists(apiKey);
        checkOwnerShip(apiKey.getUserId());

        if (apiKeyMapper.update(new LambdaUpdateWrapper<ApiKey>()
                .eq(ApiKey::getId, id)
                .eq(ApiKey::getUserId, UserContext.getUid())
                .eq(ApiKey::getStatus, ApiKey.ACTIVE)
                .set(ApiKey::getStatus, ApiKey.REVOKED)) != 1) {
            return;
        }

        if (userMapper.update(new LambdaUpdateWrapper<User>()
                .eq(User::getId, UserContext.getUid())
                .gt(User::getActiveKeyCount, 0)
                .setDecrBy(User::getActiveKeyCount, 1)) != 1) {
            throw new BusinessException("用户 API Key 计数更新失败");
        }

    }

    @Override
    public ApiKeyIdentity checkApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey) || !apiKey.startsWith(KEY_PREFIX)) {
            throw invalidApiKey();
        }

        ApiKey accessKey = apiKeyMapper.selectOne(new LambdaQueryWrapper<ApiKey>()
                        .select(ApiKey::getId, ApiKey::getUserId)
                        .eq(ApiKey::getKeyHash, hashKey(apiKey))
                        .eq(ApiKey::getStatus, ApiKey.ACTIVE)
        );
        
        if (accessKey == null) {
            throw invalidApiKey();
        }

        return new ApiKeyIdentity(accessKey.getUserId(), accessKey.getId());
    }

    private String generateKey() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hashKey(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("运行环境不支持 SHA-256", e);
        }
    }

    private RouterException invalidApiKey() {
        return new RouterException(
                HttpStatus.UNAUTHORIZED,
                "API Key 无效",
                "invalid_request_error",
                "invalid_api_key",
                null
        );
    }

    private ApiKeyVO convertApiKeyToApiKeyVO(ApiKey apiKey) {
        return new ApiKeyVO(
                apiKey.getId(),
                apiKey.getName(),
                createMaskedKey(apiKey.getKeySuffix()),
                apiKey.getStatus(),
                apiKey.getCreateTime(),
                apiKey.getUpdateTime()
        );
    }

    private String createMaskedKey(String keySuffix) {
        return KEY_PREFIX + "*****" + keySuffix;
    }

    private void checkApiKeyExists(ApiKey apiKey) {
        if (apiKey == null) {
            throw new BusinessException("API key 不存在");
        }
    }

    private void checkOwnerShip(Long userId) {
        if (!UserContext.getUid().equals(userId)) {
            throw new BusinessException("没有操作权限");
        }
    }
}
