package com.won.autoinvestor.common.trade;

import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Trading-day calculation used by the state machine.
 *
 * <p>The local implementation currently excludes weekends. Official holiday
 * data is intentionally not fabricated; it can be added here when a verified
 * source is connected.</p>
 */
@Service
public class TradingDayService {

    public boolean isTradingDay(LocalDate date) {
        if (date == null) {
            return false;
        }

        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

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
