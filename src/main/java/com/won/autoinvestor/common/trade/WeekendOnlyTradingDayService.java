package com.won.autoinvestor.common.trade;

import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;

@Service
public class WeekendOnlyTradingDayService implements TradingDayService {

    @Override
    public boolean isTradingDay(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        // TODO: Connect official Korean market holiday data. Current implementation excludes weekends only.
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    @Override
    public long countTradingDays(LocalDate fromInclusive, LocalDate toInclusive) {
        if (fromInclusive == null || toInclusive == null || toInclusive.isBefore(fromInclusive)) {
            return 0;
        }

        long days = 0;
        LocalDate cursor = fromInclusive;
        while (!cursor.isAfter(toInclusive)) {
            if (isTradingDay(cursor)) {
                days++;
            }
            cursor = cursor.plusDays(1);
        }
        return days;
    }
}
