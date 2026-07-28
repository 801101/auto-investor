package com.won.autoinvestor.broker.domain;

public record BrokerOrder(String brokerOrderId, String stockCode, String orderType, String status) {
}
