package com.won.autoinvestor.trading.config;

import com.won.autoinvestor.kis.config.KisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZoneId;

@Component
public class TradingPolicyValidator implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(TradingPolicyValidator.class);

    private final InvestmentProperties investmentProperties;
    private final MarketProperties marketProperties;
    private final SafetyProperties safetyProperties;
    private final RuntimeProperties runtimeProperties;
    private final KisProperties kisProperties;

    public TradingPolicyValidator(InvestmentProperties investmentProperties,
                                  MarketProperties marketProperties,
                                  SafetyProperties safetyProperties,
                                  RuntimeProperties runtimeProperties,
                                  KisProperties kisProperties) {
        this.investmentProperties = investmentProperties;
        this.marketProperties = marketProperties;
        this.safetyProperties = safetyProperties;
        this.runtimeProperties = runtimeProperties;
        this.kisProperties = kisProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        requirePositive(investmentProperties.getTakeProfit().getRate(), "investment.take-profit.rate must be > 0");
        requireNegative(investmentProperties.getStopLoss().getRate(), "investment.stop-loss.rate must be < 0");
        requirePositive(investmentProperties.getUnitAmount(), "investment.unit-amount must be > 0");
        requirePositive(investmentProperties.getUnitShares(), "investment.unit-shares must be > 0");
        require(investmentProperties.getMaxHoldings() >= 0, "investment.max-holdings must be >= 0");
        require(investmentProperties.getGrayMaxTradingDays() > 0, "investment.gray-max-trading-days must be > 0");
        require(investmentProperties.getOrderMaxRetryCount() >= 0, "investment.order-max-retry-count must be >= 0");
        require(investmentProperties.getOrderRetryIntervalSeconds() > 0, "investment.order-retry-interval-seconds must be > 0");
        require("AMOUNT".equalsIgnoreCase(investmentProperties.getOrderUnitType())
                        || "SHARE".equalsIgnoreCase(investmentProperties.getOrderUnitType()),
                "investment.order-unit-type must be AMOUNT or SHARE");

        ZoneId.of(marketProperties.getTimezone());
        require(marketProperties.getRegularOpenTime().isBefore(marketProperties.getRegularCloseTime()),
                "market.regular-open-time must be before market.regular-close-time");

        require(safetyProperties.getRejectOrderWhenPriceStaleSeconds() > 0,
                "safety.reject-order-when-price-stale-seconds must be > 0");

        if (investmentProperties.isLiveTradingEnabled() && !kisProperties.isConfigured()) {
            throw new IllegalStateException("live trading requires KIS app-key, app-secret, account-number, and account-product-code");
        }
        if (investmentProperties.isLiveTradingEnabled() && !runtimeProperties.isTradingEnabled()) {
            logger.warn("live trading is configured, but runtime.trading-enabled=false blocks order decisions");
        }
        logger.info("runtime instanceId={}, tradingEnabled={}, liveTradingEnabled={}, killSwitchEnabled={}",
                runtimeProperties.getInstanceId(),
                runtimeProperties.isTradingEnabled(),
                investmentProperties.isLiveTradingEnabled(),
                safetyProperties.isKillSwitchEnabled());
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
}
