package com.won.autoinvestor.trading.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    @Bean
    public Clock tradingClock(MarketProperties marketProperties) {
        return Clock.system(ZoneId.of(marketProperties.getTimezone()));
    }
}
