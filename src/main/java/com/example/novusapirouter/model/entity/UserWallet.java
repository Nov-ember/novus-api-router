package com.example.novusapirouter.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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
@TableName("user_wallet")
public class UserWallet {
    @TableId(type = IdType.INPUT)
    private Long userId;
    private BigDecimal availableBalance;
    private BigDecimal frozenAmount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
