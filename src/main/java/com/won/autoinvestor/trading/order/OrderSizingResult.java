package com.won.autoinvestor.trading.order;

import java.math.BigDecimal;

public record OrderSizingResult(boolean orderable, BigDecimal quantity, BigDecimal expectedAmount, String reason) {

    public static OrderSizingResult orderable(BigDecimal quantity, BigDecimal expectedAmount) {
        return new OrderSizingResult(true, quantity, expectedAmount, "ORDERABLE");
    }

    public static OrderSizingResult skipped(String reason) {
        return new OrderSizingResult(false, BigDecimal.ZERO, BigDecimal.ZERO, reason);
    }
}
