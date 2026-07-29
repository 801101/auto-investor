package com.won.autoinvestor.trading.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TradeLifecycleSnapshot(Long lifecycleId,
                                     TradingStatus status,
                                     BigDecimal currentPrice,
                                     BigDecimal averageBuyPrice,
                                     BigDecimal referencePrice,
                                     BigDecimal highestPrice,
                                     BigDecimal lowestPrice,
                                     BigDecimal holdingQuantity,
                                     BigDecimal returnRate,
                                     Integer grayTradingDays,
                                     OffsetDateTime evaluatedAt) {
}
