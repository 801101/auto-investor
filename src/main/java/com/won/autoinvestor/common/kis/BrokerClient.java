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

    Map<String, Object> buy(Map<String, Object> request);

    Map<String, Object> sell(Map<String, Object> request);
}
