package com.won.autoinvestor.broker.domain;

import java.math.BigDecimal;

public record AccountBalance(BigDecimal cashBalance, BigDecimal totalValuationAmount) {
}
