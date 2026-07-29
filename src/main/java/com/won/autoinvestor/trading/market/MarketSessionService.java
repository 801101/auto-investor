package com.won.autoinvestor.trading.market;

import com.won.autoinvestor.trading.calendar.TradingDayService;
import com.won.autoinvestor.trading.config.MarketProperties;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
public class MarketSessionService {

    private final MarketProperties marketProperties;
    private final TradingDayService tradingDayService;
    private final Clock clock;

    public MarketSessionService(MarketProperties marketProperties, TradingDayService tradingDayService, Clock clock) {
        this.marketProperties = marketProperties;
        this.tradingDayService = tradingDayService;
        this.clock = clock;
    }

    public boolean isRegularOrderTimeNow() {
        return isRegularOrderTime(ZonedDateTime.now(clock));
    }

    public boolean isRegularOrderTime(ZonedDateTime dateTime) {
        ZoneId marketZone = ZoneId.of(marketProperties.getTimezone());
        LocalDateTime localDateTime = dateTime.withZoneSameInstant(marketZone).toLocalDateTime();
        if (!tradingDayService.isTradingDay(localDateTime.toLocalDate())) {
            return false;
        }

        LocalTime current = localDateTime.toLocalTime();
        boolean regular = !current.isBefore(marketProperties.getRegularOpenTime())
                && !current.isAfter(marketProperties.getRegularCloseTime());
        if (regular) {
            return true;
        }
        if (current.isBefore(marketProperties.getRegularOpenTime())) {
            return marketProperties.isAllowPreMarketOrder();
        }
        return marketProperties.isAllowAfterHoursOrder();
    }
}
