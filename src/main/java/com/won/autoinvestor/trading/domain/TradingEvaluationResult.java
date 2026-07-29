package com.won.autoinvestor.trading.domain;

public class TradingEvaluationResult {

    private final TradingStatus status;
    private final String reason;
    private final ExitReason exitReason;

    public TradingEvaluationResult(TradingStatus status, String reason) {
        this(status, reason, null);
    }

    public TradingEvaluationResult(TradingStatus status, String reason, ExitReason exitReason) {
        this.status = status;
        this.reason = reason;
        this.exitReason = exitReason;
    }

    public TradingStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public ExitReason getExitReason() {
        return exitReason;
    }
}
