package com.example.novusapirouter.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class BillStatisticsVO {
    private Long requestCount;
    private Long uncachedInputTokens;
    private Long cachedInputTokens;
    private Long outputTokens;
    private Double cacheHitRate;
    private BigDecimal actualAmount;
}
