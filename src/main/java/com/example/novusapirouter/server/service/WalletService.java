package com.example.novusapirouter.server.service;

import java.math.BigDecimal;

public interface WalletService {
    BigDecimal getBalance();

    void addTestBalance(Long userId, BigDecimal amount);
}

