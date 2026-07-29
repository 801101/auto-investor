package com.won.autoinvestor.trading.order;

import org.springframework.stereotype.Component;

@Component
public class IdempotencyKeyFactory {

    public String create(String accountNumber, String strategy, String stockCode, String side, String decisionCycleId) {
        return maskNull(accountNumber) + ":" + strategy + ":" + stockCode + ":" + side + ":" + decisionCycleId;
    }

    private String maskNull(String value) {
        return value == null ? "" : value;
    }
}
