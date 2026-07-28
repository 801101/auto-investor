package com.won.autoinvestor.broker.domain;

import java.math.BigDecimal;

public record SellOrderRequest(String stockCode, BigDecimal orderQuantity, BigDecimal orderPrice, BigDecimal orderAmount, String reason) {
}
