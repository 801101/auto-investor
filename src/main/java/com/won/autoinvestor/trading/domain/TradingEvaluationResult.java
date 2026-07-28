package com.won.autoinvestor.trading.domain;

public class TradingEvaluationResult {

    private final TradingStatus status;
    private final String reason;

    public TradingEvaluationResult(TradingStatus status, String reason) {
        this.status = status;
        this.reason = reason;
    }

    public TradingStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }
}
