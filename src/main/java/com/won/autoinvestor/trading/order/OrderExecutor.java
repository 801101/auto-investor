package com.won.autoinvestor.trading.order;

import com.won.autoinvestor.broker.domain.BuyOrderRequest;
import com.won.autoinvestor.broker.domain.OrderResult;
import com.won.autoinvestor.broker.domain.SellOrderRequest;

public interface OrderExecutor {

    OrderResult buy(BuyOrderRequest request);

    OrderResult sell(SellOrderRequest request);
}
