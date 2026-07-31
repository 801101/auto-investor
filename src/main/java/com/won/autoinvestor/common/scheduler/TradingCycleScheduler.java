package com.won.autoinvestor.common.scheduler;

import com.won.autoinvestor.pilot.PilotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TradingCycleScheduler {

    private static final Logger logger = LoggerFactory.getLogger(TradingCycleScheduler.class);

    private final PilotService pilotService;
    private boolean firstTradingScheduleSkipped = false;

    public TradingCycleScheduler(PilotService pilotService) {
        this.pilotService = pilotService;
    }

    @Scheduled(fixedDelay = 1_200_000L)
    public synchronized void runTradingCycle() {
        if (!firstTradingScheduleSkipped) {
            firstTradingScheduleSkipped = true;
            logger.info("first trading schedule skipped after application startup");
            return;
        }
        pilotService.runTradingCycle();
    }

    @Scheduled(fixedDelay = 300_000L)
    public void runMaintenanceCycle() {
        pilotService.runMaintenanceCycle();
    }
}
