package com.won.autoinvestor.pilot.scheduler;

import com.won.autoinvestor.pilot.service.TradingLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "legacy-pilot.enabled", havingValue = "true")
public class TradingLifecycleScheduler {

    private static final Logger logger = LoggerFactory.getLogger(TradingLifecycleScheduler.class);

    private final TradingLifecycleService tradingLifecycleService;

    public TradingLifecycleScheduler(TradingLifecycleService tradingLifecycleService) {
        this.tradingLifecycleService = tradingLifecycleService;
    }

    @Scheduled(fixedDelayString = "${pilot.lifecycle.rotation-fixed-delay-ms}")
    public void rotateStatuses() {
        try {
            tradingLifecycleService.rotateStatuses();
        } catch (Exception e) {
            logger.error("trading lifecycle status rotation failed", e);
        }
    }

    @Scheduled(cron = "${pilot.lifecycle.market-start-cron}", zone = "Asia/Seoul")
    public void liquidateBlackAtMarketStart() {
        try {
            tradingLifecycleService.liquidateBlackAtMarketStart();
        } catch (Exception e) {
            logger.error("market start liquidation failed", e);
        }
    }
}
