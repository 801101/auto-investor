package com.won.autoinvestor.trading.order;

import com.won.autoinvestor.broker.domain.AccountBalance;
import com.won.autoinvestor.broker.domain.CurrentPrice;
import com.won.autoinvestor.trading.config.InvestmentProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class OrderSizingService {

    private final InvestmentProperties investmentProperties;

    public OrderSizingService(InvestmentProperties investmentProperties) {
        this.investmentProperties = investmentProperties;
    }

    public OrderSizingResult calculateBuyQuantity(CurrentPrice currentPrice) {
        return calculateBuyQuantity(
                currentPrice,
                new AccountBalance(new BigDecimal("999999999999"), new BigDecimal("999999999999")),
                BigDecimal.ZERO
        );
    }

    public OrderSizingResult calculateBuyQuantity(CurrentPrice currentPrice,
                                                  AccountBalance accountBalance,
                                                  BigDecimal currentTotalHoldingQuantity) {
        if (currentPrice == null || currentPrice.price() == null || currentPrice.price().signum() <= 0) {
            return OrderSizingResult.skipped("INVALID_CURRENT_PRICE");
        }
        if (accountBalance == null || accountBalance.cashBalance() == null || accountBalance.cashBalance().signum() <= 0) {
            return OrderSizingResult.skipped("INSUFFICIENT_BALANCE");
        }

        if ("SHARE".equalsIgnoreCase(investmentProperties.getOrderUnitType())) {
            BigDecimal quantity = investmentProperties.getUnitShares().setScale(0, RoundingMode.DOWN);
            if (quantity.signum() <= 0) {
                return OrderSizingResult.skipped("ZERO_QUANTITY");
            }
            BigDecimal expectedAmount = quantity.multiply(currentPrice.price());
            if (accountBalance.cashBalance().compareTo(expectedAmount) < 0) {
                return OrderSizingResult.skipped("INSUFFICIENT_BALANCE");
            }
            if (remainingHoldingCapacity(currentTotalHoldingQuantity).compareTo(BigDecimal.ZERO) >= 0
                    && remainingHoldingCapacity(currentTotalHoldingQuantity).compareTo(quantity) < 0) {
                return OrderSizingResult.skipped("MAX_HOLDINGS_INSUFFICIENT");
            }
            return OrderSizingResult.orderable(quantity, expectedAmount);
        }

        BigDecimal usableAmount = investmentProperties.getUnitAmount().min(accountBalance.cashBalance());
        BigDecimal quantity = usableAmount
                .divide(currentPrice.price(), 0, RoundingMode.DOWN);
        if (quantity.signum() <= 0) {
            return OrderSizingResult.skipped("SKIPPED_INSUFFICIENT_ORDER_AMOUNT");
        }

        BigDecimal remainingCapacity = remainingHoldingCapacity(currentTotalHoldingQuantity);
        if (remainingCapacity.compareTo(BigDecimal.ZERO) == 0) {
            return OrderSizingResult.skipped("MAX_HOLDINGS_REACHED");
        }
        if (remainingCapacity.compareTo(BigDecimal.ZERO) > 0) {
            quantity = quantity.min(remainingCapacity);
        }
        if (quantity.signum() <= 0) {
            return OrderSizingResult.skipped("ZERO_ORDER_QUANTITY");
        }
        return OrderSizingResult.orderable(quantity, quantity.multiply(currentPrice.price()));
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
}
