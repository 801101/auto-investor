package com.won.autoinvestor.common.kis;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface BrokerClient {

    Map<String, Object> issueAccessToken();

    Map<String, Object> getAccountBalance();

    default Map<String, Object> getBuyableBalance(String stockCode, BigDecimal orderPrice) {
        return getAccountBalance();
    }

    List<Map<String, Object>> getHoldings();

    Map<String, Object> getCurrentPrice(String stockCode);

    default Map<String, Object> getCurrentPrice(String stockCode, String marketType) {
        return getCurrentPrice(stockCode);
    }

    Map<String, Object> buy(Map<String, Object> request);

    Map<String, Object> sell(Map<String, Object> request);

    Map<String, Object> cancel(Map<String, Object> request);

    default List<Map<String, Object>> getOrderStatuses(Map<String, Object> request) {
        throw new UnsupportedOperationException("KIS order status inquiry is not implemented");
    }
}
