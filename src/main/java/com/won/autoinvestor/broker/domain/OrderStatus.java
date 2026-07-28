package com.won.autoinvestor.broker.domain;

public record OrderStatus(String brokerOrderId, String status, String message) {
}
