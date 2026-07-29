package com.won.autoinvestor.trading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "investment")
public class InvestmentProperties {

    private String orderUnitType = "AMOUNT";
    private BigDecimal unitAmount = new BigDecimal("1000");
    private BigDecimal unitShares = BigDecimal.ONE;
    private boolean allowDuplicateStock = false;
    private int maxHoldings = 50;
    private boolean includeEtf = false;
    private BigDecimal takeProfitRate = new BigDecimal("0.10");
    private RatePolicy takeProfit = new RatePolicy(true, new BigDecimal("0.10"));
    private RatePolicy stopLoss = new RatePolicy(false, new BigDecimal("-0.10"));
    private int grayMaxTradingDays = 3;
    private boolean liveTradingEnabled = false;
    private int orderMaxRetryCount = 3;
    private long orderRetryIntervalSeconds = 30;

    public String getOrderUnitType() {
        return orderUnitType;
    }

    public void setOrderUnitType(String orderUnitType) {
        this.orderUnitType = orderUnitType;
    }

    public BigDecimal getUnitAmount() {
        return unitAmount;
    }

    public void setUnitAmount(BigDecimal unitAmount) {
        this.unitAmount = unitAmount;
    }

    public BigDecimal getUnitShares() {
        return unitShares;
    }

    public void setUnitShares(BigDecimal unitShares) {
        this.unitShares = unitShares;
    }

    public boolean isAllowDuplicateStock() {
        return allowDuplicateStock;
    }

    public void setAllowDuplicateStock(boolean allowDuplicateStock) {
        this.allowDuplicateStock = allowDuplicateStock;
    }

    public int getMaxHoldings() {
        return maxHoldings;
    }

    public void setMaxHoldings(int maxHoldings) {
        this.maxHoldings = maxHoldings;
    }

    public int getMaxHoldingsLimit() {
        return maxHoldings;
    }

    public boolean isIncludeEtf() {
        return includeEtf;
    }

    public void setIncludeEtf(boolean includeEtf) {
        this.includeEtf = includeEtf;
    }

    public BigDecimal getTakeProfitRate() {
        if (takeProfit != null && takeProfit.getRate() != null) {
            return takeProfit.getRate();
        }
        return takeProfitRate;
    }

    public void setTakeProfitRate(BigDecimal takeProfitRate) {
        this.takeProfitRate = takeProfitRate;
    }

    public RatePolicy getTakeProfit() {
        return takeProfit;
    }

    public void setTakeProfit(RatePolicy takeProfit) {
        this.takeProfit = takeProfit;
    }

    public RatePolicy getStopLoss() {
        return stopLoss;
    }

    public void setStopLoss(RatePolicy stopLoss) {
        this.stopLoss = stopLoss;
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

    public static class RatePolicy {

        private boolean enabled;
        private BigDecimal rate;

        public RatePolicy() {
        }

        public RatePolicy(boolean enabled, BigDecimal rate) {
            this.enabled = enabled;
            this.rate = rate;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public BigDecimal getRate() {
            return rate;
        }

        public void setRate(BigDecimal rate) {
            this.rate = rate;
        }
    }
}
