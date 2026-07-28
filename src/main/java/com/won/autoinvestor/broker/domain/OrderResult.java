package com.won.autoinvestor.broker.domain;

public record OrderResult(boolean accepted, String brokerOrderId, String status, String message) {

    public static OrderResult accepted(String brokerOrderId, String status, String message) {
        return new OrderResult(true, brokerOrderId, status, message);
    }

    public static OrderResult rejected(String message) {
        return new OrderResult(false, null, "REJECTED", message);
    }
}
