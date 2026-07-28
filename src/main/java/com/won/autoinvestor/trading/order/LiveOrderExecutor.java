package com.won.autoinvestor.trading.order;

import com.won.autoinvestor.broker.BrokerClient;
import com.won.autoinvestor.broker.domain.BuyOrderRequest;
import com.won.autoinvestor.broker.domain.OrderResult;
import com.won.autoinvestor.broker.domain.SellOrderRequest;

public class LiveOrderExecutor implements OrderExecutor {

    private final BrokerClient brokerClient;

    public LiveOrderExecutor(BrokerClient brokerClient) {
        this.brokerClient = brokerClient;
    }

    @Override
    public OrderResult buy(BuyOrderRequest request) {
        return brokerClient.buy(request);
    }

    @Override
    public OrderResult sell(SellOrderRequest request) {
        return brokerClient.sell(request);
    }
}
