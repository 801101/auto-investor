package com.won.autoinvestor.trading.calendar;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeekendOnlyTradingDayServiceTest {

    private final WeekendOnlyTradingDayService service = new WeekendOnlyTradingDayService();

    @Test
    void weekendsAreExcludedFromTradingDays() {
        assertTrue(service.isTradingDay(LocalDate.of(2026, 7, 31)));
        assertFalse(service.isTradingDay(LocalDate.of(2026, 8, 1)));
        assertFalse(service.isTradingDay(LocalDate.of(2026, 8, 2)));
    }

    @Test
    void countTradingDaysExcludesWeekend() {
        assertEquals(3, service.countTradingDays(LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 4)));
    }
}
