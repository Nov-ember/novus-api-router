package com.example.novusapirouter.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@TableName("bill")
public class Bill {
    public static final String RESERVED = "RESERVED";
    public static final String SETTLED = "SETTLED";
    public static final String RELEASED = "RELEASED";

    @TableId
    private Long id;
    private Long userId;
    private Long apiKeyId;
    private String modelAlias;
    private Integer inputTokens;
    private Integer outputTokens;
    private BigDecimal frozenAmount;
    private BigDecimal inputPricePerMillion;
    private BigDecimal outputPricePerMillion;
    private BigDecimal actualAmount;
    private String status;
    private LocalDateTime createTime;
}
