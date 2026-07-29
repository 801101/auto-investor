package com.won.autoinvestor.trading.order;

import com.won.autoinvestor.broker.domain.CurrentPrice;
import com.won.autoinvestor.trading.config.InvestmentProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderSizingServiceTest {

    @Test
    void amountOrderSkipsWhenTargetAmountCannotBuyOneShare() {
        InvestmentProperties properties = new InvestmentProperties();
        OrderSizingResult result = new OrderSizingService(properties)
                .calculateBuyQuantity(price("70000"));

        assertFalse(result.orderable());
        assertEquals("SKIPPED_INSUFFICIENT_ORDER_AMOUNT", result.reason());
        assertEquals(BigDecimal.ZERO, result.quantity());
    }

    @Test
    void amountOrderRoundsQuantityDown() {
        InvestmentProperties properties = new InvestmentProperties();
        properties.setUnitAmount(new BigDecimal("10000"));

        OrderSizingResult result = new OrderSizingService(properties)
                .calculateBuyQuantity(price("3000"));

        assertTrue(result.orderable());
        assertEquals(new BigDecimal("3"), result.quantity());
        assertEquals(new BigDecimal("9000"), result.expectedAmount());
    }

    @Test
    void shareOrderUsesConfiguredShareQuantity() {
        InvestmentProperties properties = new InvestmentProperties();
        properties.setOrderUnitType("SHARE");
        properties.setUnitShares(new BigDecimal("2"));

        OrderSizingResult result = new OrderSizingService(properties)
                .calculateBuyQuantity(price("70000"));

        assertTrue(result.orderable());
        assertEquals(new BigDecimal("2"), result.quantity());
        assertEquals(new BigDecimal("140000"), result.expectedAmount());
    }

    private CurrentPrice price(String value) {
        return new CurrentPrice("005930", new BigDecimal(value), OffsetDateTime.now());
    }
}
