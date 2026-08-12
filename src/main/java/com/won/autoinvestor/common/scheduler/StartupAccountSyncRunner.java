package com.won.autoinvestor.common.scheduler;

import com.won.autoinvestor.pilot.PilotService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class StartupAccountSyncRunner implements ApplicationRunner {

    private final PilotService pilotService;

    public StartupAccountSyncRunner(PilotService pilotService) {
        this.pilotService = pilotService;
    }

    @Override
    public void run(ApplicationArguments args) {
        pilotService.runStartupAccountSync();
    }
}
