package com.won.autoinvestor.common.trade;

import com.won.autoinvestor.common.util.MapUtils;
import com.won.autoinvestor.pilot.PilotMapper;
import com.won.autoinvestor.common.config.InvestmentProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Map;

@Service
public class OrderSafetyService {

    private final PilotMapper tradingMapper;
    private final InvestmentProperties investmentProperties;
    private final AccountSyncStateService accountSyncStateService;

    @Autowired
    public OrderSafetyService(PilotMapper tradingMapper,
                              InvestmentProperties investmentProperties,
                              AccountSyncStateService accountSyncStateService,
                              Clock clock) {
        this.tradingMapper = tradingMapper;
        this.investmentProperties = investmentProperties;
        this.accountSyncStateService = accountSyncStateService;
    }

    public OrderSafetyService(PilotMapper tradingMapper, InvestmentProperties investmentProperties) {
        this(tradingMapper, investmentProperties, new AccountSyncStateService(), Clock.system(ZoneId.of("Asia/Seoul")));
    }

    public Map<String, Object> validateBuy(String stockCode,
                                           BigDecimal orderQuantity,
                                           BigDecimal orderAmount,
                                           Map<String, Object> accountBalance,
                                           boolean marketOpen) {
        if (!marketOpen) {
            return blocked("MARKET_CLOSED");
        }
        if (!accountSyncStateService.isLastSyncSuccessful()) {
            return blocked("BALANCE_SYNC_FAILED");
        }
        if (orderQuantity == null || orderQuantity.signum() <= 0) {
            return blocked("ZERO_QUANTITY");
        }
        if (orderAmount == null || accountBalance == null || MapUtils.decimal(accountBalance, "cashBalance").compareTo(orderAmount) < 0) {
            return blocked("INSUFFICIENT_CASH");
        }
        int maxHoldingSlots = maxHoldingSlots();
        int usedSlotsAfterBuy = tradingMapper.countActiveHeldAndOpenBuySlots() + 1;
        if (maxHoldingSlots > 0 && usedSlotsAfterBuy > maxHoldingSlots) {
            return blocked("MAX_HOLDINGS_REACHED");
        }
        Map<String, Object> maxHoldingsPerStockResult = validateMaxHoldingsPerStock(stockCode, orderQuantity, orderAmount);
        if (!MapUtils.bool(maxHoldingsPerStockResult, "orderAllowed")) {
            return maxHoldingsPerStockResult;
        }
        return allowed();
    }

    private int maxHoldingSlots() {
        return investmentProperties.getMaxHoldings();
    }

    private Map<String, Object> validateMaxHoldingsPerStock(String stockCode, BigDecimal orderQuantity, BigDecimal orderAmount) {
        int maxHoldingsPerStock = investmentProperties.getMaxHoldingsPerStock();
        if (maxHoldingsPerStock == 0) {
            return allowed();
        }

        if ("SHARE".equalsIgnoreCase(investmentProperties.getOrderUnitType())) {
            BigDecimal currentSameStockQuantity = parseAmount(tradingMapper.sumActiveHoldingQuantityByStockCode(MapUtils.map("stockCode", stockCode)))
                    .add(parseAmount(tradingMapper.sumOpenBuyOrderQuantityByStockCode(MapUtils.map("stockCode", stockCode))));
            BigDecimal allowedQuantity = investmentProperties.getUnitShares().multiply(BigDecimal.valueOf(maxHoldingsPerStock));
            if (currentSameStockQuantity.add(orderQuantity).compareTo(allowedQuantity) > 0) {
                return blocked("MAX_HOLDINGS_PER_STOCK_REACHED");
            }
            return allowed();
        }

        BigDecimal currentSameStockAmount = parseAmount(tradingMapper.sumActiveInvestedAmountByStockCode(MapUtils.map("stockCode", stockCode)))
                .add(parseAmount(tradingMapper.sumOpenBuyOrderAmountByStockCode(MapUtils.map("stockCode", stockCode))));
        BigDecimal allowedAmount = investmentProperties.getUnitAmount().multiply(BigDecimal.valueOf(maxHoldingsPerStock));
        if (currentSameStockAmount.add(orderAmount).compareTo(allowedAmount) > 0) {
            return blocked("MAX_HOLDINGS_PER_STOCK_REACHED");
        }
        return allowed();
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value);
    }

    private Map<String, Object> allowed() {
        return MapUtils.map("orderAllowed", true, "reason", "ALLOWED");
    }

    private Map<String, Object> blocked(String reason) {
        return MapUtils.map("orderAllowed", false, "reason", reason);
    }
}
