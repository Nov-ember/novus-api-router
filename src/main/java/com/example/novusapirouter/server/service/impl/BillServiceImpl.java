package com.example.novusapirouter.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.novusapirouter.common.context.ApiKeyIdentity;
import com.example.novusapirouter.common.context.UserContext;
import com.example.novusapirouter.common.exception.BusinessException;
import com.example.novusapirouter.common.exception.RouterException;
import com.example.novusapirouter.common.property.RouterProperties;
import com.example.novusapirouter.model.dto.BillStatisticsQueryDTO;
import com.example.novusapirouter.model.entity.Bill;
import com.example.novusapirouter.model.entity.UserWallet;
import com.example.novusapirouter.model.vo.BillStatisticsVO;
import com.example.novusapirouter.server.mapper.BillMapper;
import com.example.novusapirouter.server.mapper.WalletMapper;
import com.example.novusapirouter.server.service.BillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

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
        BigDecimal cachedInputPricePerMillion = modelConfig.getCachedInputPricePerMillion();
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
                .cachedInputPricePerMillion(cachedInputPricePerMillion)
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
    public void settle(Long billId, Integer inputTokens, Integer cachedInputTokens, Integer outputTokens) {
        if (inputTokens == null || cachedInputTokens == null || outputTokens == null
                || inputTokens < 0 || cachedInputTokens < 0 || outputTokens < 0
                || cachedInputTokens > inputTokens) {
            throw new RouterException(
                    HttpStatus.BAD_GATEWAY,
                    "上游返回的 token 用量不合法",
                    "server_error",
                    "invalid_upstream_usage",
                    null
            );
        }

        Bill bill = billMapper.selectById(billId);
        checkBillExists(bill);

        BigDecimal actualAmount = bill.getInputPricePerMillion().multiply(BigDecimal.valueOf(inputTokens - cachedInputTokens))
                .add(bill.getCachedInputPricePerMillion().multiply(BigDecimal.valueOf(cachedInputTokens)))
                .add(bill.getOutputPricePerMillion().multiply(BigDecimal.valueOf(outputTokens)))
                .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP);

        BigDecimal billFrozenAmount = bill.getFrozenAmount();
        BigDecimal refund = billFrozenAmount.subtract(actualAmount);

        if (billMapper.update(new LambdaUpdateWrapper<Bill>()
                .eq(Bill::getId, billId)
                .eq(Bill::getStatus, Bill.RESERVED)
                .set(Bill::getStatus, Bill.SETTLED)
                .set(Bill::getInputTokens, inputTokens)
                .set(Bill::getCachedInputTokens, cachedInputTokens)
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

    @Override
    public BillStatisticsVO getStatistics(BillStatisticsQueryDTO queryDTO) {
        if (!queryDTO.getStartTime().isBefore(queryDTO.getEndTime())) {
            throw new BusinessException("startTime 必须早于 endTime");
        }

        List<Bill> bills = billMapper.selectList(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getUserId, UserContext.getUid())
                .eq(Bill::getStatus, Bill.SETTLED)
                .ge(Bill::getCreateTime, queryDTO.getStartTime())
                .lt(Bill::getCreateTime, queryDTO.getEndTime())
                .eq(queryDTO.getApiKeyId() != null,
                        Bill::getApiKeyId, queryDTO.getApiKeyId())
                .eq(queryDTO.getModelAlias() != null,
                        Bill::getModelAlias, queryDTO.getModelAlias()));

        long totalInputTokens = 0;
        long cachedInputTokens = 0;
        long outputTokens = 0;
        BigDecimal actualAmount = BigDecimal.ZERO;

        for (Bill bill : bills) {
            totalInputTokens += bill.getInputTokens();
            cachedInputTokens += bill.getCachedInputTokens();
            outputTokens += bill.getOutputTokens();
            actualAmount = actualAmount.add(bill.getActualAmount());
        }

        long uncachedInputTokens = totalInputTokens - cachedInputTokens;
        double cacheHitRate = totalInputTokens == 0 ? 0 : (double) cachedInputTokens / totalInputTokens;

        return new BillStatisticsVO(
                (long) bills.size(),
                uncachedInputTokens,
                cachedInputTokens,
                outputTokens,
                cacheHitRate,
                actualAmount
        );
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
