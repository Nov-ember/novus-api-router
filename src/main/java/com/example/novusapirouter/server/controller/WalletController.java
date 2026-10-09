package com.example.novusapirouter.server.controller;

import com.example.novusapirouter.common.result.Result;
import com.example.novusapirouter.server.service.WalletService;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
public class WalletController {
    private final WalletService walletService;

    @GetMapping
    public Result<BigDecimal> getBalance() {
        BigDecimal balance = walletService.getBalance();
        return Result.success(balance);
    }

    @PostMapping("/{userId}")
    public Result<Void> addTestBalance(@PathVariable Long userId,
                                       @NotNull @Positive @Digits (integer = 6, fraction = 2) @RequestBody BigDecimal amount) {
        walletService.addTestBalance(userId, amount);
        return Result.success();
    }

}
