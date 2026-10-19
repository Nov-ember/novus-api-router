package com.example.novusapirouter.server.service;

import com.example.novusapirouter.common.context.ApiKeyIdentity;


public interface BillService {
    Long reserve(ApiKeyIdentity apiKeyIdentity, String modelAlias, String platform, Integer inputBudgetTokens, Integer maxOutputTokens);

    void settle(Long billId, Integer inputTokens, Integer cachedInputTokens, Integer outputTokens);

    void release(Long billId);
}
