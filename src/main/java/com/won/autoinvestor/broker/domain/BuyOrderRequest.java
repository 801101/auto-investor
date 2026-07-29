package com.won.autoinvestor.broker.domain;

import com.won.autoinvestor.trading.domain.ExitReason;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BuyOrderRequest(String stockCode,
                              BigDecimal orderQuantity,
                              BigDecimal orderPrice,
                              BigDecimal orderAmount,
                              String reason,
                              String decisionCycleId,
                              String idempotencyKey,
                              String instanceId,
                              String maskedAccount,
                              BigDecimal currentPrice,
                              OffsetDateTime currentPriceAt,
                              ExitReason exitReason) {

    public BuyOrderRequest(String stockCode,
                           BigDecimal orderQuantity,
                           BigDecimal orderPrice,
                           BigDecimal orderAmount,
                           String reason) {
        this(stockCode, orderQuantity, orderPrice, orderAmount, reason, null, null, null, null, null, null, null);
    }
}
