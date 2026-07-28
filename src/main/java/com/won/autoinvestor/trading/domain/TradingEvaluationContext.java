package com.won.autoinvestor.trading.domain;

import java.math.BigDecimal;

public class TradingEvaluationContext {

    private final TradingStatus status;
    private final BigDecimal purchasePrice;
    private final BigDecimal lastEvaluatedPrice;
    private final BigDecimal currentPrice;
    private final BigDecimal investedAmount;
    private final BigDecimal currentValuationAmount;
    private final BigDecimal profitRate;
    private final long grayTradingDays;

    public TradingEvaluationContext(TradingStatus status,
                                    BigDecimal purchasePrice,
                                    BigDecimal lastEvaluatedPrice,
                                    BigDecimal currentPrice,
                                    BigDecimal investedAmount,
                                    BigDecimal currentValuationAmount,
                                    BigDecimal profitRate,
                                    long grayTradingDays) {
        this.status = status;
        this.purchasePrice = purchasePrice;
        this.lastEvaluatedPrice = lastEvaluatedPrice;
        this.currentPrice = currentPrice;
        this.investedAmount = investedAmount;
        this.currentValuationAmount = currentValuationAmount;
        this.profitRate = profitRate;
        this.grayTradingDays = grayTradingDays;
    }

    public TradingStatus getStatus() {
        return status;
    }

    public BigDecimal getPurchasePrice() {
        return purchasePrice;
    }

    public BigDecimal getLastEvaluatedPrice() {
        return lastEvaluatedPrice;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public BigDecimal getInvestedAmount() {
        return investedAmount;
    }

    public BigDecimal getCurrentValuationAmount() {
        return currentValuationAmount;
    }

    public BigDecimal getProfitRate() {
        return profitRate;
    }

    public long getGrayTradingDays() {
        return grayTradingDays;
    }

    public TradingEvaluationContext withStatus(TradingStatus nextStatus) {
        return new TradingEvaluationContext(
                nextStatus,
                purchasePrice,
                lastEvaluatedPrice,
                currentPrice,
                investedAmount,
                currentValuationAmount,
                profitRate,
                grayTradingDays
        );
    }
}
