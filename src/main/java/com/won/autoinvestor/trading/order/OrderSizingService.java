package com.won.autoinvestor.trading.order;

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
        if (currentPrice == null || currentPrice.price() == null || currentPrice.price().signum() <= 0) {
            return OrderSizingResult.skipped("INVALID_CURRENT_PRICE");
        }

        if ("SHARE".equalsIgnoreCase(investmentProperties.getOrderUnitType())) {
            BigDecimal quantity = investmentProperties.getUnitShares().setScale(0, RoundingMode.DOWN);
            if (quantity.signum() <= 0) {
                return OrderSizingResult.skipped("ZERO_QUANTITY");
            }
            return OrderSizingResult.orderable(quantity, quantity.multiply(currentPrice.price()));
        }

        BigDecimal quantity = investmentProperties.getUnitAmount()
                .divide(currentPrice.price(), 0, RoundingMode.DOWN);
        if (quantity.signum() <= 0) {
            return OrderSizingResult.skipped("SKIPPED_INSUFFICIENT_ORDER_AMOUNT");
        }
        return OrderSizingResult.orderable(quantity, quantity.multiply(currentPrice.price()));
    }
}
