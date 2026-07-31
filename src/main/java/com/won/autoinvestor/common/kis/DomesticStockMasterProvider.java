package com.won.autoinvestor.common.kis;

import java.util.List;
import java.util.Map;

public interface DomesticStockMasterProvider {

    List<Map<String, Object>> fetch(String marketCode);
}
