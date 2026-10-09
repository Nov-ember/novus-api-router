package com.example.novusapirouter.server.service;

import com.example.novusapirouter.common.context.ApiKeyIdentity;


public interface BillService {
    Long reserve(ApiKeyIdentity apiKeyIdentity, String modelAlias, Integer inputBudgetTokens, Integer maxOutputTokens);

    void settle(Long billId, Integer inputTokens, Integer outputTokens);

    void release(Long billId);
}
