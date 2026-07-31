package com.won.autoinvestor.common.trade;

import java.time.LocalDate;

public interface TradingDayService {

    boolean isTradingDay(LocalDate date);

    long countTradingDays(LocalDate fromInclusive, LocalDate toInclusive);
}
