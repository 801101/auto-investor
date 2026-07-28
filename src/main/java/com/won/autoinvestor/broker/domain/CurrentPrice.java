package com.won.autoinvestor.broker.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CurrentPrice(String stockCode, BigDecimal price, OffsetDateTime receivedAt) {
}
