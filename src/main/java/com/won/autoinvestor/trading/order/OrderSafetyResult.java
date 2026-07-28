package com.won.autoinvestor.trading.order;

public record OrderSafetyResult(boolean orderAllowed, String reason) {

    public static OrderSafetyResult allowed() {
        return new OrderSafetyResult(true, "ALLOWED");
    }

    public static OrderSafetyResult blocked(String reason) {
        return new OrderSafetyResult(false, reason);
    }
}
