package com.won.autoinvestor.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    @Bean
    public Clock tradingClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
