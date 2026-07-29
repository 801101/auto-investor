package com.won.autoinvestor.trading.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TradeLifecycleEvent(Long lifecycleId,
                                  LifecycleEventType eventType,
                                  TradingStatus previousState,
                                  TradingStatus newState,
                                  BigDecimal currentPrice,
                                  BigDecimal averageBuyPrice,
                                  BigDecimal referencePrice,
                                  BigDecimal highestPrice,
                                  BigDecimal lowestPrice,
                                  BigDecimal holdingQuantity,
                                  BigDecimal returnRate,
                                  Integer grayTradingDays,
                                  String reason,
                                  Long orderId,
                                  String executionId,
                                  String idempotencyKey,
                                  OffsetDateTime occurredAt) {
}
