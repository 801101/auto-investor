package com.won.autoinvestor.trading.order;

import com.won.autoinvestor.broker.domain.AccountBalance;
import com.won.autoinvestor.pilot.mapper.PilotMapper;
import com.won.autoinvestor.trading.config.InvestmentProperties;
import com.won.autoinvestor.trading.config.SafetyProperties;
import com.won.autoinvestor.trading.service.AccountSyncStateService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;

@Service
public class OrderSafetyService {

    private final PilotMapper pilotMapper;
    private final InvestmentProperties investmentProperties;
    private final SafetyProperties safetyProperties;
    private final AccountSyncStateService accountSyncStateService;

    public OrderSafetyService(PilotMapper pilotMapper,
                              InvestmentProperties investmentProperties,
                              SafetyProperties safetyProperties,
                              AccountSyncStateService accountSyncStateService,
                              Clock clock) {
        this.pilotMapper = pilotMapper;
        this.investmentProperties = investmentProperties;
        this.safetyProperties = safetyProperties;
        this.accountSyncStateService = accountSyncStateService;
    }

    public OrderSafetyService(PilotMapper pilotMapper, InvestmentProperties investmentProperties) {
        this(pilotMapper, investmentProperties, new SafetyProperties(),
                new AccountSyncStateService(), Clock.system(ZoneId.of("Asia/Seoul")));
    }

    public OrderSafetyResult validateBuy(String stockCode,
                                         BigDecimal orderQuantity,
                                         BigDecimal orderAmount,
                                         AccountBalance accountBalance,
                                         boolean marketOpen) {
        if (!marketOpen) {
            return OrderSafetyResult.blocked("MARKET_CLOSED");
        }
        if (safetyProperties.isKillSwitchEnabled()) {
            return OrderSafetyResult.blocked("KILL_SWITCH_ENABLED");
        }
        if (safetyProperties.isRejectOrderWhenBalanceSyncFailed() && !accountSyncStateService.isLastSyncSuccessful()) {
            return OrderSafetyResult.blocked("BALANCE_SYNC_FAILED");
        }
        if (orderQuantity == null || orderQuantity.signum() <= 0) {
            return OrderSafetyResult.blocked("ZERO_QUANTITY");
        }
        if (orderAmount == null || accountBalance == null || accountBalance.cashBalance().compareTo(orderAmount) < 0) {
            return OrderSafetyResult.blocked("INSUFFICIENT_CASH");
        }
        int maxHoldingStocks = maxHoldingStocks();
        BigDecimal totalQuantityAfterBuy = parseAmount(pilotMapper.sumActiveHoldingQuantity())
                .add(parseAmount(pilotMapper.sumOpenBuyOrderQuantity()))
                .add(orderQuantity);
        if (maxHoldingStocks > 0 && totalQuantityAfterBuy.compareTo(BigDecimal.valueOf(maxHoldingStocks)) > 0) {
            return OrderSafetyResult.blocked("MAX_HOLDINGS_REACHED");
        }
        if (!investmentProperties.isAllowDuplicateStock() && pilotMapper.countActivePositionByStockCode(stockCode) > 0) {
            return OrderSafetyResult.blocked("DUPLICATE_HOLDING");
        }
        if (pilotMapper.countOpenOrderByStockCode(stockCode) > 0) {
            return OrderSafetyResult.blocked("DUPLICATE_OPEN_ORDER");
        }
        return OrderSafetyResult.allowed();
    }

    public OrderSafetyResult validateSell(String stockCode, BigDecimal orderQuantity) {
        if (safetyProperties.isKillSwitchEnabled()) {
            return OrderSafetyResult.blocked("KILL_SWITCH_ENABLED");
        }
        if (safetyProperties.isRejectOrderWhenBalanceSyncFailed() && !accountSyncStateService.isLastSyncSuccessful()) {
            return OrderSafetyResult.blocked("BALANCE_SYNC_FAILED");
        }
        if (orderQuantity == null || orderQuantity.signum() <= 0) {
            return OrderSafetyResult.blocked("ZERO_QUANTITY");
        }
        if (pilotMapper.countOpenOrderByStockCode(stockCode) > 0) {
            return OrderSafetyResult.blocked("DUPLICATE_OPEN_ORDER");
        }
        return OrderSafetyResult.allowed();
    }

    private int maxHoldingStocks() {
        return investmentProperties.getMaxHoldings();
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value);
    }
}
