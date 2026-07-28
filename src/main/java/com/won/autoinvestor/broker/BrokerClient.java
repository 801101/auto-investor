package com.won.autoinvestor.broker;

import com.won.autoinvestor.broker.domain.AccessToken;
import com.won.autoinvestor.broker.domain.AccountBalance;
import com.won.autoinvestor.broker.domain.BrokerHolding;
import com.won.autoinvestor.broker.domain.BrokerOrder;
import com.won.autoinvestor.broker.domain.BuyOrderRequest;
import com.won.autoinvestor.broker.domain.CurrentPrice;
import com.won.autoinvestor.broker.domain.OrderResult;
import com.won.autoinvestor.broker.domain.OrderStatus;
import com.won.autoinvestor.broker.domain.SellOrderRequest;

import java.util.List;

public interface BrokerClient {

    AccessToken issueAccessToken();

    AccountBalance getAccountBalance();

    List<BrokerHolding> getHoldings();

    CurrentPrice getCurrentPrice(String stockCode);

    OrderResult buy(BuyOrderRequest request);

    OrderResult sell(SellOrderRequest request);

    List<BrokerOrder> getOpenOrders();

    OrderStatus getOrderStatus(String orderId);
}
