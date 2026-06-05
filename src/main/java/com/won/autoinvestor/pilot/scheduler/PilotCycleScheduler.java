package com.won.autoinvestor.pilot.scheduler;

import com.won.autoinvestor.pilot.service.PilotCycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PilotCycleScheduler {

    private static final Logger logger = LoggerFactory.getLogger(PilotCycleScheduler.class);

    private final PilotCycleService pilotCycleService;

    public PilotCycleScheduler(PilotCycleService pilotCycleService) {
        this.pilotCycleService = pilotCycleService;
    }

    @Scheduled(fixedDelayString = "${pilot.cycle.fixed-delay-ms}")
    public void run() {
        try {
            pilotCycleService.runCycle();
        } catch (Exception e) {
            logger.error("pilot cycle failed", e);
        }
    }
}
