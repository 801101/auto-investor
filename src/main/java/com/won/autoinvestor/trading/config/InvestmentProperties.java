package com.won.autoinvestor.trading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "investment")
public class InvestmentProperties {

    private BigDecimal unitAmount = new BigDecimal("1000");
    private int maxHoldings = 0;
    private int maxPerStock = 1;
    private boolean includeEtf = false;
    private BigDecimal takeProfitRate = new BigDecimal("0.10");
    private int grayMaxTradingDays = 3;
    private boolean liveTradingEnabled = false;
    private int orderMaxRetryCount = 3;
    private long orderRetryIntervalSeconds = 30;

    public BigDecimal getUnitAmount() {
        return unitAmount;
    }

    public void setUnitAmount(BigDecimal unitAmount) {
        this.unitAmount = unitAmount;
    }

    public int getMaxHoldings() {
        return maxHoldings;
    }

    public void setMaxHoldings(int maxHoldings) {
        this.maxHoldings = maxHoldings;
    }

    public int getMaxPerStock() {
        return maxPerStock;
    }

    public void setMaxPerStock(int maxPerStock) {
        this.maxPerStock = maxPerStock;
    }

    public boolean isIncludeEtf() {
        return includeEtf;
    }

    public void setIncludeEtf(boolean includeEtf) {
        this.includeEtf = includeEtf;
    }

    public BigDecimal getTakeProfitRate() {
        return takeProfitRate;
    }

    public void setTakeProfitRate(BigDecimal takeProfitRate) {
        this.takeProfitRate = takeProfitRate;
    }

    public int getGrayMaxTradingDays() {
        return grayMaxTradingDays;
    }

    public void setGrayMaxTradingDays(int grayMaxTradingDays) {
        this.grayMaxTradingDays = grayMaxTradingDays;
    }

    public boolean isLiveTradingEnabled() {
        return liveTradingEnabled;
    }

    public void setLiveTradingEnabled(boolean liveTradingEnabled) {
        this.liveTradingEnabled = liveTradingEnabled;
    }

    public int getOrderMaxRetryCount() {
        return orderMaxRetryCount;
    }

    public void setOrderMaxRetryCount(int orderMaxRetryCount) {
        this.orderMaxRetryCount = orderMaxRetryCount;
    }

    public long getOrderRetryIntervalSeconds() {
        return orderRetryIntervalSeconds;
    }

    public void setOrderRetryIntervalSeconds(long orderRetryIntervalSeconds) {
        this.orderRetryIntervalSeconds = orderRetryIntervalSeconds;
    }
}
