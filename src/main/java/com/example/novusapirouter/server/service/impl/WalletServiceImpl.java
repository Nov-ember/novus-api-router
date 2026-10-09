package com.example.novusapirouter.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.novusapirouter.common.context.UserContext;
import com.example.novusapirouter.common.exception.BusinessException;
import com.example.novusapirouter.common.property.WalletProperties;
import com.example.novusapirouter.model.entity.UserWallet;
import com.example.novusapirouter.server.mapper.WalletMapper;
import com.example.novusapirouter.server.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletMapper walletMapper;
    private final WalletProperties walletProperties;

    @Override
    public BigDecimal getBalance() {
        Long userId = UserContext.getUid();
        UserWallet wallet = walletMapper.selectById(userId);
        return wallet.getAvailableBalance().add(wallet.getFrozenAmount());
    }

    @Override
    public void addTestBalance(Long userId, BigDecimal amount) {
        Long operatorId = UserContext.getUid();
        if (!walletProperties.getAdminUserIds().contains(operatorId)) {
            throw new BusinessException("没有操作权限");
        }
        if (walletMapper.update(new LambdaUpdateWrapper<UserWallet>()
                .eq(UserWallet::getUserId, userId)
                .setIncrBy(UserWallet::getAvailableBalance, amount)) != 1) {
            throw new BusinessException("增加测试余额失败");
        }
    }
}
