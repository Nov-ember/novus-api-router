package com.example.novusapirouter.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.novusapirouter.common.context.ApiKeyIdentity;
import com.example.novusapirouter.common.exception.RouterException;
import com.example.novusapirouter.common.property.RouterProperties;
import com.example.novusapirouter.model.entity.Bill;
import com.example.novusapirouter.model.entity.UserWallet;
import com.example.novusapirouter.server.mapper.BillMapper;
import com.example.novusapirouter.server.mapper.WalletMapper;
import com.example.novusapirouter.server.service.BillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class BillServiceImpl implements BillService {

    private final RouterProperties routerProperties;
    private final WalletMapper walletMapper;
    private final BillMapper billMapper;

    @Override
    @Transactional
    public Long reserve(ApiKeyIdentity apiKeyIdentity, String modelAlias, Integer inputBudgetTokens, Integer maxOutputTokens) {
        RouterProperties.ModelConfig modelConfig = routerProperties.getModelAliases().get(modelAlias);
        BigDecimal inputPricePerMillion = modelConfig.getInputPricePerMillion();
        BigDecimal outputPricePerMillion = modelConfig.getOutputPricePerMillion();
        BigDecimal reserved = inputPricePerMillion.multiply(BigDecimal.valueOf(inputBudgetTokens))
                .add(outputPricePerMillion.multiply(BigDecimal.valueOf(maxOutputTokens)))
                .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.CEILING);

        if (walletMapper.update(new LambdaUpdateWrapper<UserWallet>()
                .eq(UserWallet::getUserId, apiKeyIdentity.getUserId())
                .ge(UserWallet::getAvailableBalance, reserved)
                .setDecrBy(UserWallet::getAvailableBalance, reserved)
                .setIncrBy(UserWallet::getFrozenAmount, reserved)) != 1) {
            throw new RouterException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "余额不足",
                    "insufficient_quota",
                    "credit_balance_exhausted",
                    null
            );
        }

        Bill bill = Bill.builder()
                .userId(apiKeyIdentity.getUserId())
                .apiKeyId(apiKeyIdentity.getApiKeyId())
                .modelAlias(modelAlias)
                .frozenAmount(reserved)
                .inputPricePerMillion(inputPricePerMillion)
                .outputPricePerMillion(outputPricePerMillion)
                .status(Bill.RESERVED)
                .build();

        if (billMapper.insert(bill) != 1) {
            throw new RouterException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "账单创建失败",
                    "server_error",
                    "billing_error",
                    null
            );
        }
        return bill.getId();
    }

    @Override
    @Transactional
    public void settle(Long billId, Integer inputTokens, Integer outputTokens) {
        Bill bill = billMapper.selectById(billId);
        checkBillExists(bill);

        BigDecimal actualAmount = bill.getInputPricePerMillion().multiply(BigDecimal.valueOf(inputTokens))
                .add(bill.getOutputPricePerMillion().multiply(BigDecimal.valueOf(outputTokens)))
                .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP);

        BigDecimal billFrozenAmount = bill.getFrozenAmount();
        BigDecimal refund = billFrozenAmount.subtract(actualAmount);

        if (billMapper.update(new LambdaUpdateWrapper<Bill>()
                .eq(Bill::getId, billId)
                .eq(Bill::getStatus, Bill.RESERVED)
                .set(Bill::getStatus, Bill.SETTLED)
                .set(Bill::getInputTokens, inputTokens)
                .set(Bill::getOutputTokens, outputTokens)
                .set(Bill::getActualAmount, actualAmount)) != 1) {
            throw new RouterException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "账单处理失败",
                    "server_error",
                    "billing_error",
                    null
            );
        }

        if (walletMapper.update(new LambdaUpdateWrapper<UserWallet>()
                .eq(UserWallet::getUserId, bill.getUserId())
                .ge(UserWallet::getFrozenAmount, billFrozenAmount)
                .ge(refund.signum() < 0, UserWallet::getAvailableBalance, refund.negate())
                .setIncrBy(UserWallet::getAvailableBalance, refund)
                .setDecrBy(UserWallet::getFrozenAmount, billFrozenAmount)) != 1) {
            throw new RouterException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "账单结算失败",
                    "server_error",
                    "billing_error",
                    null
            );
        }
    }

    @Override
    @Transactional
    public void release(Long billId) {
        Bill bill = billMapper.selectById(billId);
        checkBillExists(bill);

        if (billMapper.update(new LambdaUpdateWrapper<Bill>()
                .eq(Bill::getId, billId)
                .eq(Bill::getStatus, Bill.RESERVED)
                .set(Bill::getStatus, Bill.RELEASED)
                .set(Bill::getActualAmount, BigDecimal.ZERO)) != 1) {
            return;
        }

        BigDecimal billFrozenAmount = bill.getFrozenAmount();

        if (walletMapper.update(new LambdaUpdateWrapper<UserWallet>()
                .eq(UserWallet::getUserId, bill.getUserId())
                .ge(UserWallet::getFrozenAmount, billFrozenAmount)
                .setIncrBy(UserWallet::getAvailableBalance, billFrozenAmount)
                .setDecrBy(UserWallet::getFrozenAmount, billFrozenAmount)) != 1) {
            throw new RouterException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "账单释放失败",
                    "server_error",
                    "billing_error",
                    null
            );
        }
    }

    private void checkBillExists(Bill bill) {
        if (bill == null) {
            throw new RouterException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "账单不存在",
                    "server_error",
                    "billing_error",
                    null
            );
        }
    }
}
