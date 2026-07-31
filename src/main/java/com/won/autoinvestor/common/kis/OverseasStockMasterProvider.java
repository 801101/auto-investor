package com.won.autoinvestor.common.kis;

import java.util.List;
import java.util.Map;

public interface OverseasStockMasterProvider {

    List<Map<String, Object>> fetch(String exchangeCode,
                                    String priceExchangeCode,
                                    String currencyCode);
}
