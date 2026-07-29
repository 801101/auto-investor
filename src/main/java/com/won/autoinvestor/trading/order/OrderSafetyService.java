package com.won.autoinvestor.trading.order;

import com.won.autoinvestor.broker.domain.AccountBalance;
import com.won.autoinvestor.pilot.mapper.PilotMapper;
import com.won.autoinvestor.trading.config.InvestmentProperties;
import com.won.autoinvestor.trading.config.RiskProperties;
import com.won.autoinvestor.trading.config.SafetyProperties;
import com.won.autoinvestor.trading.service.AccountSyncStateService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class OrderSafetyService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final PilotMapper pilotMapper;
    private final InvestmentProperties investmentProperties;
    private final RiskProperties riskProperties;
    private final SafetyProperties safetyProperties;
    private final AccountSyncStateService accountSyncStateService;
    private final Clock clock;

    public OrderSafetyService(PilotMapper pilotMapper,
                              InvestmentProperties investmentProperties,
                              RiskProperties riskProperties,
                              SafetyProperties safetyProperties,
                              AccountSyncStateService accountSyncStateService,
                              Clock clock) {
        this.pilotMapper = pilotMapper;
        this.investmentProperties = investmentProperties;
        this.riskProperties = riskProperties;
        this.safetyProperties = safetyProperties;
        this.accountSyncStateService = accountSyncStateService;
        this.clock = clock;
    }

    public OrderSafetyService(PilotMapper pilotMapper, InvestmentProperties investmentProperties) {
        this(pilotMapper, investmentProperties, new RiskProperties(), new SafetyProperties(),
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
        if (orderAmount.compareTo(riskProperties.getMaxSingleOrderAmount()) > 0) {
            return OrderSafetyResult.blocked("MAX_SINGLE_ORDER_AMOUNT_EXCEEDED");
        }
        if (accountBalance.cashBalance().subtract(orderAmount).compareTo(riskProperties.getMinimumCashReserve()) < 0) {
            return OrderSafetyResult.blocked("MINIMUM_CASH_RESERVE");
        }
        if (todayOrderCount() >= riskProperties.getMaxDailyOrderCount()) {
            return OrderSafetyResult.blocked("MAX_DAILY_ORDER_COUNT_EXCEEDED");
        }
        BigDecimal activeInvestedAmount = parseAmount(pilotMapper.sumActiveInvestedAmount());
        if (activeInvestedAmount.add(orderAmount).compareTo(riskProperties.getMaxTotalInvestedAmount()) > 0) {
            return OrderSafetyResult.blocked("MAX_TOTAL_INVESTED_AMOUNT_EXCEEDED");
        }
        if (riskProperties.getConsecutiveErrorStopCount() > 0
                && pilotMapper.countRecentFailedOrders() >= riskProperties.getConsecutiveErrorStopCount()) {
            return OrderSafetyResult.blocked("CIRCUIT_BREAKER_OPEN");
        }
        int maxHoldingStocks = maxHoldingStocks();
        if (maxHoldingStocks > 0 && pilotMapper.countActivePositions() >= maxHoldingStocks) {
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
        if (investmentProperties.getMaxHoldingStocks() > 0) {
            return investmentProperties.getMaxHoldingStocks();
        }
        return investmentProperties.getMaxHoldings();
    }

    private int todayOrderCount() {
        LocalDate today = LocalDate.now(clock);
        OffsetDateTime from = today.atStartOfDay(clock.getZone()).toOffsetDateTime();
        OffsetDateTime to = today.plusDays(1).atStartOfDay(clock.getZone()).toOffsetDateTime();
        return pilotMapper.countOrdersRequestedBetween(from.format(TIME_FORMATTER), to.format(TIME_FORMATTER));
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value);
    }
}
