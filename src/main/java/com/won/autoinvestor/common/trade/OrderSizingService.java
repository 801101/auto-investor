package com.won.autoinvestor.common.trade;

import com.won.autoinvestor.common.util.MapUtils;
import com.won.autoinvestor.common.config.InvestmentProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Service
public class OrderSizingService {

    private final InvestmentProperties investmentProperties;

    public OrderSizingService(InvestmentProperties investmentProperties) {
        this.investmentProperties = investmentProperties;
    }

    public Map<String, Object> calculateBuyQuantity(Map<String, Object> currentPrice,
                                                    Map<String, Object> accountBalance,
                                                    BigDecimal currentTotalHoldingQuantity) {
        BigDecimal price = MapUtils.decimal(currentPrice, "price");
        BigDecimal cashBalance = MapUtils.decimal(accountBalance, "cashBalance");
        if (currentPrice == null || price.signum() <= 0) {
            return skipped("INVALID_CURRENT_PRICE");
        }
        if (accountBalance == null || cashBalance.signum() <= 0) {
            return skipped("INSUFFICIENT_BALANCE");
        }

        if ("SHARE".equalsIgnoreCase(investmentProperties.getOrderUnitType())) {
            BigDecimal quantity = investmentProperties.getUnitShares().setScale(0, RoundingMode.DOWN);
            if (quantity.signum() <= 0) {
                return skipped("ZERO_QUANTITY");
            }
            BigDecimal expectedAmount = quantity.multiply(price);
            if (cashBalance.compareTo(expectedAmount) < 0) {
                return skipped("INSUFFICIENT_BALANCE");
            }
            if (remainingHoldingCapacity(currentTotalHoldingQuantity).compareTo(BigDecimal.ZERO) >= 0
                    && remainingHoldingCapacity(currentTotalHoldingQuantity).compareTo(quantity) < 0) {
                return skipped("MAX_HOLDINGS_INSUFFICIENT");
            }
            return orderable(quantity, expectedAmount);
        }

        BigDecimal usableAmount = investmentProperties.getUnitAmount().min(cashBalance);
        BigDecimal quantity;
        if ("OVERSEAS".equalsIgnoreCase(investmentProperties.getMarketType())) {
            quantity = usableAmount.divide(price, 8, RoundingMode.DOWN).stripTrailingZeros();
        } else {
            quantity = usableAmount.divide(price, 0, RoundingMode.DOWN);
        }
        if (quantity.signum() <= 0) {
            return skipped("SKIPPED_INSUFFICIENT_ORDER_AMOUNT");
        }

        BigDecimal remainingCapacity = remainingHoldingCapacity(currentTotalHoldingQuantity);
        if (remainingCapacity.compareTo(BigDecimal.ZERO) == 0) {
            return skipped("MAX_HOLDINGS_REACHED");
        }
        if (remainingCapacity.compareTo(BigDecimal.ZERO) > 0) {
            quantity = quantity.min(remainingCapacity);
        }
        if (quantity.signum() <= 0) {
            return skipped("ZERO_ORDER_QUANTITY");
        }
        return orderable(quantity, quantity.multiply(price));
    }

    private BigDecimal remainingHoldingCapacity(BigDecimal currentTotalHoldingQuantity) {
        int maxHoldings = investmentProperties.getMaxHoldings();
        if (maxHoldings == 0) {
            return new BigDecimal("-1");
        }
        BigDecimal current = currentTotalHoldingQuantity == null ? BigDecimal.ZERO : currentTotalHoldingQuantity;
        BigDecimal remaining = BigDecimal.valueOf(maxHoldings).subtract(current);
        return remaining.max(BigDecimal.ZERO);
    }

    private Map<String, Object> orderable(BigDecimal quantity, BigDecimal expectedAmount) {
        return MapUtils.map("orderable", true, "quantity", quantity, "expectedAmount", expectedAmount, "reason", "ORDERABLE");
    }

    private Map<String, Object> skipped(String reason) {
        return MapUtils.map("orderable", false, "quantity", BigDecimal.ZERO, "expectedAmount", BigDecimal.ZERO, "reason", reason);
    }
}
