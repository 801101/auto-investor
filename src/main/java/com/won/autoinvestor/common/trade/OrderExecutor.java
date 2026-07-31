package com.won.autoinvestor.common.trade;

import java.util.Map;

public interface OrderExecutor {

    Map<String, Object> buy(Map<String, Object> request);

    Map<String, Object> sell(Map<String, Object> request);
}
