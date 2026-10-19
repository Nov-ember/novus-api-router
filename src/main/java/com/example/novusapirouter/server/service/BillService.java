package com.example.novusapirouter.server.service;

import com.example.novusapirouter.common.context.ApiKeyIdentity;
import com.example.novusapirouter.model.dto.BillStatisticsQueryDTO;
import com.example.novusapirouter.model.vo.BillStatisticsVO;


public interface BillService {
    Long reserve(ApiKeyIdentity apiKeyIdentity, String modelAlias, Integer inputBudgetTokens, Integer maxOutputTokens);

    void settle(Long billId, Integer inputTokens, Integer cachedInputTokens, Integer outputTokens);

    void release(Long billId);

    BillStatisticsVO getStatistics(BillStatisticsQueryDTO queryDTO);
}
