package com.example.novusapirouter.server.service;

import com.example.novusapirouter.common.context.ApiKeyIdentity;
import com.example.novusapirouter.model.dto.ApiKeyNameDTO;
import com.example.novusapirouter.model.vo.ApiKeyVO;

import java.util.List;

public interface ApiKeyService {
    String createApiKey(ApiKeyNameDTO apiKeyNameDTO);

    List<ApiKeyVO> listApiKeys();

    void renameApiKey(Long id, ApiKeyNameDTO apiKeyNameDTO);

    void revokeApiKey(Long id);

    ApiKeyIdentity checkApiKey(String apiKey);
}
