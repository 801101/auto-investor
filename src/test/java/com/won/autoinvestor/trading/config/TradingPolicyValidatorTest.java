package com.won.autoinvestor.trading.config;

import com.won.autoinvestor.kis.config.KisProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;

class TradingPolicyValidatorTest {

    @Test
    void unitAmountMustBePositive() {
        InvestmentProperties investmentProperties = investmentProperties();
        investmentProperties.setUnitAmount(BigDecimal.ZERO);

        assertThrows(IllegalStateException.class, () -> validator(investmentProperties).run(null));
    }

    @Test
    void unitSharesMustBePositive() {
        InvestmentProperties investmentProperties = investmentProperties();
        investmentProperties.setUnitShares(BigDecimal.ZERO);

        assertThrows(IllegalStateException.class, () -> validator(investmentProperties).run(null));
    }

    @Test
    void maxHoldingsMustNotBeNegative() {
        InvestmentProperties investmentProperties = investmentProperties();
        investmentProperties.setMaxHoldings(-1);

        assertThrows(IllegalStateException.class, () -> validator(investmentProperties).run(null));
    }

    @Test
    void unsupportedOrderUnitTypeFailsFast() {
        InvestmentProperties investmentProperties = investmentProperties();
        investmentProperties.setOrderUnitType("WALLET");

        assertThrows(IllegalStateException.class, () -> validator(investmentProperties).run(null));
    }

    private InvestmentProperties investmentProperties() {
        return new InvestmentProperties();
    }

    private TradingPolicyValidator validator(InvestmentProperties investmentProperties) {
        return new TradingPolicyValidator(
                investmentProperties,
                new MarketProperties(),
                new SafetyProperties(),
                new RuntimeProperties(),
                new KisProperties()
        );
    }
}
