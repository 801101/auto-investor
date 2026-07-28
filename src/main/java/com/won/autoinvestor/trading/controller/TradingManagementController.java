package com.won.autoinvestor.trading.controller;

import com.won.autoinvestor.pilot.mapper.PilotMapper;
import com.won.autoinvestor.kis.config.KisProperties;
import com.won.autoinvestor.trading.config.InvestmentProperties;
import com.won.autoinvestor.trading.service.AccountSynchronizationService;
import com.won.autoinvestor.trading.service.TradingCycleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class TradingManagementController {

    private final InvestmentProperties investmentProperties;
    private final KisProperties kisProperties;
    private final PilotMapper pilotMapper;
    private final AccountSynchronizationService accountSynchronizationService;
    private final TradingCycleService tradingCycleService;

    public TradingManagementController(InvestmentProperties investmentProperties,
                                       KisProperties kisProperties,
                                       PilotMapper pilotMapper,
                                       AccountSynchronizationService accountSynchronizationService,
                                       TradingCycleService tradingCycleService) {
        this.investmentProperties = investmentProperties;
        this.kisProperties = kisProperties;
        this.pilotMapper = pilotMapper;
        this.accountSynchronizationService = accountSynchronizationService;
        this.tradingCycleService = tradingCycleService;
    }

    @GetMapping("/system/status")
    public Map<String, Object> systemStatus() {
        return Map.of(
                "running", true,
                "liveTradingEnabled", investmentProperties.isLiveTradingEnabled(),
                "activePanicStopCount", pilotMapper.countActivePanicStop(),
                "kisConfigured", kisProperties.isConfigured(),
                "kisBaseUrl", kisProperties.getBaseUrl(),
                "kisAccount", maskedAccountNumber()
        );
    }

    @GetMapping("/account")
    public Map<String, Object> account() {
        return Map.of("message", "TODO: expose account snapshot after KIS account synchronization is implemented");
    }

    @GetMapping("/positions")
    public Map<String, Object> positions() {
        return Map.of("activePositions", pilotMapper.countActivePositions());
    }

    @GetMapping("/orders")
    public Map<String, Object> orders() {
        return Map.of("message", "TODO: expose orders table with pagination");
    }

    @GetMapping("/status-history")
    public Map<String, Object> statusHistory() {
        return Map.of("message", "TODO: expose status_history table with pagination");
    }

    @PostMapping("/trading/evaluate")
    public Map<String, Object> evaluate() {
        tradingCycleService.runTradingCycle();
        return Map.of("status", "requested");
    }

    @PostMapping("/trading/sync")
    public Map<String, Object> sync() {
        accountSynchronizationService.syncAccount();
        return Map.of("status", "requested");
    }

    @PostMapping("/trading/run-cycle")
    public Map<String, Object> runCycle() {
        tradingCycleService.runTradingCycle();
        return Map.of("status", "requested");
    }

    private String maskedAccountNumber() {
        String accountNumber = kisProperties.getAccountNumber();
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }
}
