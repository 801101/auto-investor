package com.won.autoinvestor.pilot;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class PilotController {

    private final PilotService pilotService;

    public PilotController(PilotService pilotService) {
        this.pilotService = pilotService;
    }

    @GetMapping("/system/status")
    public Map<String, Object> systemStatus() {
        return pilotService.getSystemStatus();
    }

    @GetMapping("/system/kis/health")
    public Map<String, Object> kisHealth() {
        return pilotService.getKisHealth();
    }

    @GetMapping("/positions")
    public Map<String, Object> positions() {
        return pilotService.getPositions();
    }

    @GetMapping("/account")
    public Map<String, Object> account() {
        return pilotService.getAccount();
    }

    @GetMapping("/overseas/dashboard")
    public Map<String, Object> overseasDashboard() {
        return pilotService.getOverseasDashboard();
    }

    @GetMapping("/domestic/dashboard")
    public Map<String, Object> domesticDashboard() {
        return pilotService.getDomesticDashboard();
    }

    @PostMapping("/trading/evaluate")
    public Map<String, Object> evaluate() {
        pilotService.runTradingCycle();
        return Map.of("status", "requested");
    }

    @PostMapping("/trading/sync")
    public Map<String, Object> sync() {
        pilotService.syncAccount();
        return Map.of("status", "requested");
    }

    @PostMapping("/trading/run-cycle")
    public Map<String, Object> runCycle() {
        pilotService.runTradingCycle();
        return Map.of("status", "requested");
    }

}
