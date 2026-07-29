package com.won.autoinvestor.trading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "risk")
public class RiskProperties {

    private BigDecimal maxDailyLossRate = new BigDecimal("-0.03");
    private int maxDailyOrderCount = 100;
    private BigDecimal maxSingleOrderAmount = new BigDecimal("10000");
    private BigDecimal maxTotalInvestedAmount = new BigDecimal("50000");
    private BigDecimal minimumCashReserve = new BigDecimal("5000");
    private int consecutiveErrorStopCount = 5;

    public BigDecimal getMaxDailyLossRate() {
        return maxDailyLossRate;
    }

    public void setMaxDailyLossRate(BigDecimal maxDailyLossRate) {
        this.maxDailyLossRate = maxDailyLossRate;
    }

    public int getMaxDailyOrderCount() {
        return maxDailyOrderCount;
    }

    public void setMaxDailyOrderCount(int maxDailyOrderCount) {
        this.maxDailyOrderCount = maxDailyOrderCount;
    }

    public BigDecimal getMaxSingleOrderAmount() {
        return maxSingleOrderAmount;
    }

    public void setMaxSingleOrderAmount(BigDecimal maxSingleOrderAmount) {
        this.maxSingleOrderAmount = maxSingleOrderAmount;
    }

    public BigDecimal getMaxTotalInvestedAmount() {
        return maxTotalInvestedAmount;
    }

    public void setMaxTotalInvestedAmount(BigDecimal maxTotalInvestedAmount) {
        this.maxTotalInvestedAmount = maxTotalInvestedAmount;
    }

    public BigDecimal getMinimumCashReserve() {
        return minimumCashReserve;
    }

    public void setMinimumCashReserve(BigDecimal minimumCashReserve) {
        this.minimumCashReserve = minimumCashReserve;
    }

    public int getConsecutiveErrorStopCount() {
        return consecutiveErrorStopCount;
    }

    public void setConsecutiveErrorStopCount(int consecutiveErrorStopCount) {
        this.consecutiveErrorStopCount = consecutiveErrorStopCount;
    }
}
