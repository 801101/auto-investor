package com.won.autoinvestor.broker.domain;

import java.math.BigDecimal;

public record BuyOrderRequest(String stockCode, BigDecimal orderQuantity, BigDecimal orderPrice, BigDecimal orderAmount, String reason) {
}
