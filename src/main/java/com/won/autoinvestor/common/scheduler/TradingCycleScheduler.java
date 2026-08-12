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

    @Scheduled(cron = "0 */${runtime.kis-batch-interval-minutes:20} * * * *")
    public synchronized void runTradingCycle() {
        if (!pilotService.isStartupAccountSyncCompleted()) {
            logger.debug("trading schedule skipped until startup account synchronization completes");
            return;
        }
        if (!firstTradingScheduleSkipped) {
            firstTradingScheduleSkipped = true;
            logger.info("first trading schedule skipped after application startup");
            return;
        }
        pilotService.runTradingCycle();
    }

    @Scheduled(cron = "0 */${runtime.internal-batch-interval-minutes:5} * * * *")
    public void runMaintenanceCycle() {
        pilotService.runMaintenanceCycle();
    }
}
