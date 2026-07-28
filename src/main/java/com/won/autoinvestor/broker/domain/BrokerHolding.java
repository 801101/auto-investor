package com.won.autoinvestor.broker.domain;

import java.math.BigDecimal;

public record BrokerHolding(String stockCode, String stockName, BigDecimal quantity, BigDecimal averagePrice) {
}
