package com.won.autoinvestor.trading.order;

import com.won.autoinvestor.broker.domain.CurrentPrice;
import com.won.autoinvestor.broker.domain.AccountBalance;
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
                .calculateBuyQuantity(price("2000"), balance("10000"), BigDecimal.ZERO);

        assertFalse(result.orderable());
        assertEquals("SKIPPED_INSUFFICIENT_ORDER_AMOUNT", result.reason());
        assertEquals(BigDecimal.ZERO, result.quantity());
    }

    @Test
    void amountOrderRoundsQuantityDown() {
        InvestmentProperties properties = new InvestmentProperties();
        properties.setUnitAmount(new BigDecimal("10000"));

        OrderSizingResult result = new OrderSizingService(properties)
                .calculateBuyQuantity(price("3000"), balance("10000"), BigDecimal.ZERO);

        assertTrue(result.orderable());
        assertEquals(new BigDecimal("3"), result.quantity());
        assertEquals(new BigDecimal("9000"), result.expectedAmount());
    }

    @Test
    void amountOrderUsesAffordableQuantityWhenBalanceIsLessThanUnitAmount() {
        InvestmentProperties properties = new InvestmentProperties();
        properties.setUnitAmount(new BigDecimal("10000"));

        OrderSizingResult result = new OrderSizingService(properties)
                .calculateBuyQuantity(price("3000"), balance("7000"), BigDecimal.ZERO);

        assertTrue(result.orderable());
        assertEquals(new BigDecimal("2"), result.quantity());
        assertEquals(new BigDecimal("6000"), result.expectedAmount());
    }

    @Test
    void amountOrderCanBuyMultipleLowPriceShares() {
        InvestmentProperties properties = new InvestmentProperties();
        properties.setUnitAmount(new BigDecimal("1000"));

        OrderSizingResult result = new OrderSizingService(properties)
                .calculateBuyQuantity(price("100"), balance("10000"), BigDecimal.ZERO);

        assertTrue(result.orderable());
        assertEquals(new BigDecimal("10"), result.quantity());
        assertEquals(new BigDecimal("1000"), result.expectedAmount());
    }

    @Test
    void amountOrderShrinksToRemainingMaxHoldings() {
        InvestmentProperties properties = new InvestmentProperties();
        properties.setUnitAmount(new BigDecimal("1000"));
        properties.setMaxHoldings(50);

        OrderSizingResult result = new OrderSizingService(properties)
                .calculateBuyQuantity(price("100"), balance("10000"), new BigDecimal("45"));

        assertTrue(result.orderable());
        assertEquals(new BigDecimal("5"), result.quantity());
        assertEquals(new BigDecimal("500"), result.expectedAmount());
    }

    @Test
    void amountOrderIsBlockedWhenMaxHoldingsReached() {
        InvestmentProperties properties = new InvestmentProperties();
        properties.setUnitAmount(new BigDecimal("1000"));
        properties.setMaxHoldings(50);

        OrderSizingResult result = new OrderSizingService(properties)
                .calculateBuyQuantity(price("100"), balance("10000"), new BigDecimal("50"));

        assertFalse(result.orderable());
        assertEquals("MAX_HOLDINGS_REACHED", result.reason());
    }

    @Test
    void maxHoldingsZeroMeansUnlimited() {
        InvestmentProperties properties = new InvestmentProperties();
        properties.setUnitAmount(new BigDecimal("1000"));
        properties.setMaxHoldings(0);

        OrderSizingResult result = new OrderSizingService(properties)
                .calculateBuyQuantity(price("100"), balance("10000"), new BigDecimal("50"));

        assertTrue(result.orderable());
        assertEquals(new BigDecimal("10"), result.quantity());
    }

    @Test
    void shareOrderUsesConfiguredShareQuantity() {
        InvestmentProperties properties = new InvestmentProperties();
        properties.setOrderUnitType("SHARE");
        properties.setUnitShares(new BigDecimal("3"));

        OrderSizingResult result = new OrderSizingService(properties)
                .calculateBuyQuantity(price("1000"), balance("10000"), new BigDecimal("45"));

        assertTrue(result.orderable());
        assertEquals(new BigDecimal("3"), result.quantity());
        assertEquals(new BigDecimal("3000"), result.expectedAmount());
    }

    @Test
    void shareOrderDoesNotShrinkWhenBalanceIsInsufficient() {
        InvestmentProperties properties = new InvestmentProperties();
        properties.setOrderUnitType("SHARE");
        properties.setUnitShares(new BigDecimal("3"));

        OrderSizingResult result = new OrderSizingService(properties)
                .calculateBuyQuantity(price("4000"), balance("10000"), BigDecimal.ZERO);

        assertFalse(result.orderable());
        assertEquals("INSUFFICIENT_BALANCE", result.reason());
    }

    @Test
    void shareOrderDoesNotShrinkWhenRemainingMaxHoldingsIsInsufficient() {
        InvestmentProperties properties = new InvestmentProperties();
        properties.setOrderUnitType("SHARE");
        properties.setUnitShares(new BigDecimal("3"));
        properties.setMaxHoldings(50);

        OrderSizingResult result = new OrderSizingService(properties)
                .calculateBuyQuantity(price("1000"), balance("10000"), new BigDecimal("48"));

        assertFalse(result.orderable());
        assertEquals("MAX_HOLDINGS_INSUFFICIENT", result.reason());
    }

    @Test
    void preOrderPriceIncreaseCanMakeAmountOrderUnbuyable() {
        InvestmentProperties properties = new InvestmentProperties();
        properties.setUnitAmount(new BigDecimal("1000"));

        OrderSizingResult result = new OrderSizingService(properties)
                .calculateBuyQuantity(price("2000"), balance("1000"), BigDecimal.ZERO);

        assertFalse(result.orderable());
        assertEquals("SKIPPED_INSUFFICIENT_ORDER_AMOUNT", result.reason());
    }

    @Test
    void preOrderBalanceDecreaseRecalculatesAmountQuantity() {
        InvestmentProperties properties = new InvestmentProperties();
        properties.setUnitAmount(new BigDecimal("10000"));

        OrderSizingResult result = new OrderSizingService(properties)
                .calculateBuyQuantity(price("3000"), balance("7000"), BigDecimal.ZERO);

        assertTrue(result.orderable());
        assertEquals(new BigDecimal("2"), result.quantity());
    }

    private CurrentPrice price(String value) {
        return new CurrentPrice("005930", new BigDecimal(value), OffsetDateTime.now());
    }

    private AccountBalance balance(String value) {
        return new AccountBalance(new BigDecimal(value), new BigDecimal(value));
    }
}
