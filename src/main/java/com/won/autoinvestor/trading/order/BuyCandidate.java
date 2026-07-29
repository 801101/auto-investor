package com.won.autoinvestor.trading.order;

import com.won.autoinvestor.broker.domain.CurrentPrice;

import java.math.BigDecimal;

public record BuyCandidate(String stockCode,
                           BigDecimal score,
                           CurrentPrice currentPrice,
                           boolean alreadyHeld,
                           boolean tradable) {
}
