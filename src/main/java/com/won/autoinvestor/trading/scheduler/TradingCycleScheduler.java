package com.won.autoinvestor.trading.scheduler;

import com.won.autoinvestor.trading.service.TradingCycleService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TradingCycleScheduler {

    private final TradingCycleService tradingCycleService;

    public TradingCycleScheduler(TradingCycleService tradingCycleService) {
        this.tradingCycleService = tradingCycleService;
    }

    @Scheduled(cron = "${scheduler.trading-cycle-cron}", zone = "Asia/Seoul")
    public void runTradingCycle() {
        tradingCycleService.runTradingCycle();
    }

    @Scheduled(fixedDelayString = "${scheduler.maintenance-fixed-delay-ms}")
    public void runMaintenanceCycle() {
        tradingCycleService.runMaintenanceCycle();
    }
}
