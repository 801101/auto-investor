package com.won.autoinvestor.trading.market;

import com.won.autoinvestor.trading.calendar.WeekendOnlyTradingDayService;
import com.won.autoinvestor.trading.config.MarketProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketSessionServiceTest {

    @Test
    void regularOrderTimeUsesAsiaSeoulRegardlessOfServerZone() {
        MarketProperties properties = new MarketProperties();
        Clock utcClock = Clock.fixed(Instant.parse("2026-07-29T01:00:00Z"), ZoneId.of("UTC"));
        MarketSessionService service = new MarketSessionService(properties, new WeekendOnlyTradingDayService(), utcClock);

        assertTrue(service.isRegularOrderTimeNow());
    }

    @Test
    void afterMarketCloseBlocksOrderDecisions() {
        MarketProperties properties = new MarketProperties();
        Clock clock = Clock.fixed(Instant.parse("2026-07-29T07:00:00Z"), ZoneId.of("UTC"));
        MarketSessionService service = new MarketSessionService(properties, new WeekendOnlyTradingDayService(), clock);

        assertFalse(service.isRegularOrderTimeNow());
    }
}
