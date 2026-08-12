package com.won.autoinvestor.common.config;

import com.won.autoinvestor.common.kis.KisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class TradingPolicyValidator implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(TradingPolicyValidator.class);

    private final InvestmentProperties investmentProperties;
    private final RuntimeProperties runtimeProperties;
    private final KisProperties kisProperties;

    public TradingPolicyValidator(InvestmentProperties investmentProperties,
                                  RuntimeProperties runtimeProperties,
                                  KisProperties kisProperties) {
        this.investmentProperties = investmentProperties;
        this.runtimeProperties = runtimeProperties;
        this.kisProperties = kisProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        requirePositive(investmentProperties.getTakeProfit().getRate(), "investment.strategy.take-profit.rate must be > 0");
        requireNegative(investmentProperties.getStopLoss().getRate(), "investment.strategy.stop-loss.rate must be < 0");
        requirePositive(investmentProperties.getUnitAmount(), "investment.order.unit-amount must be > 0");
        requirePositive(investmentProperties.getUnitShares(), "investment.order.unit-shares must be > 0");
        require(investmentProperties.getMaxHoldingsPerStock() >= 0, "investment.holding.max-holdings-per-stock must be >= 0");
        require(investmentProperties.getMaxHoldings() >= 0, "investment.holding.max-holdings must be >= 0");
        require(investmentProperties.getWhiteFlatGraceTradingDays() >= 0, "investment.strategy.white.flat-grace-trading-days must be >= 0");
        require(investmentProperties.getGrayGraceTradingDays() >= 0, "investment.strategy.gray.grace-trading-days must be >= 0");
        require("AMOUNT".equalsIgnoreCase(investmentProperties.getOrderUnitType())
                        || "SHARE".equalsIgnoreCase(investmentProperties.getOrderUnitType()),
                "investment.order.unit-type must be AMOUNT or SHARE");
        require("OVERSEAS".equalsIgnoreCase(investmentProperties.getMarketType())
                        || "DOMESTIC".equalsIgnoreCase(investmentProperties.getMarketType()),
                "investment.market.type must be OVERSEAS or DOMESTIC");
        if ("DOMESTIC".equalsIgnoreCase(investmentProperties.getMarketType())) {
            require(hasText(investmentProperties.getDomesticMarketCode()),
                    "investment.market.domestic-market-code must not be blank");
        }
        if ("OVERSEAS".equalsIgnoreCase(investmentProperties.getMarketType())) {
            require(hasText(investmentProperties.getOverseasExchangeCode()),
                    "investment.market.overseas-exchange-code must not be blank");
            require(hasText(investmentProperties.getOverseasPriceExchangeCode()),
                    "investment.market.overseas-price-exchange-code must not be blank");
            require(hasText(investmentProperties.getOverseasCurrencyCode()),
                    "investment.market.overseas-currency-code must not be blank");
        }
        require(kisProperties.isPaperMode() || kisProperties.isRealMode(),
                "kis.account-mode must be PAPER or REAL");

        if (runtimeProperties.isTradingEnabled() && !kisProperties.isConfigured()) {
            throw new IllegalStateException("trading requires KIS app-key, app-secret, account-number, account-product-code, and base-url");
        }
        logger.info("runtime instanceId={}, tradingEnabled={}, kisAccountMode={}, kisBaseUrl={}",
                runtimeProperties.getInstanceId(),
                runtimeProperties.isTradingEnabled(),
                kisProperties.getAccountMode(),
                kisProperties.getBaseUrl());
    }

    private void requirePositive(BigDecimal value, String message) {
        require(value != null && value.compareTo(BigDecimal.ZERO) > 0, message);
    }

    private void requireNegative(BigDecimal value, String message) {
        require(value != null && value.compareTo(BigDecimal.ZERO) < 0, message);
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
