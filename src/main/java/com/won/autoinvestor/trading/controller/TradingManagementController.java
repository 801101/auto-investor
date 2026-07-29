package com.won.autoinvestor.trading.controller;

import com.won.autoinvestor.pilot.mapper.PilotMapper;
import com.won.autoinvestor.broker.BrokerClient;
import com.won.autoinvestor.kis.config.KisProperties;
import com.won.autoinvestor.trading.config.InvestmentProperties;
import com.won.autoinvestor.trading.service.AccountSynchronizationService;
import com.won.autoinvestor.trading.service.TradingCycleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TradingManagementController {

    private final InvestmentProperties investmentProperties;
    private final KisProperties kisProperties;
    private final BrokerClient brokerClient;
    private final PilotMapper pilotMapper;
    private final AccountSynchronizationService accountSynchronizationService;
    private final TradingCycleService tradingCycleService;

    public TradingManagementController(InvestmentProperties investmentProperties,
                                       KisProperties kisProperties,
                                       BrokerClient brokerClient,
                                       PilotMapper pilotMapper,
                                       AccountSynchronizationService accountSynchronizationService,
                                       TradingCycleService tradingCycleService) {
        this.investmentProperties = investmentProperties;
        this.kisProperties = kisProperties;
        this.brokerClient = brokerClient;
        this.pilotMapper = pilotMapper;
        this.accountSynchronizationService = accountSynchronizationService;
        this.tradingCycleService = tradingCycleService;
    }

    @GetMapping("/system/status")
    public Map<String, Object> systemStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("running", true);
        status.put("liveTradingEnabled", investmentProperties.isLiveTradingEnabled());
        status.put("orderUnitType", investmentProperties.getOrderUnitType());
        status.put("unitAmount", investmentProperties.getUnitAmount());
        status.put("unitShares", investmentProperties.getUnitShares());
        status.put("allowDuplicateStock", investmentProperties.isAllowDuplicateStock());
        status.put("maxHoldingStocks", investmentProperties.getMaxHoldingStocks());
        status.put("takeProfitEnabled", investmentProperties.getTakeProfit().isEnabled());
        status.put("takeProfitRate", investmentProperties.getTakeProfitRate());
        status.put("stopLossEnabled", investmentProperties.getStopLoss().isEnabled());
        status.put("stopLossRate", investmentProperties.getStopLoss().getRate());
        status.put("activePanicStopCount", pilotMapper.countActivePanicStop());
        status.put("kisConfigured", kisProperties.isConfigured());
        status.put("kisBaseUrl", kisProperties.getBaseUrl());
        status.put("kisAccount", maskedAccountNumber());
        return status;
    }

    @GetMapping("/system/kis/health")
    public Map<String, Object> kisHealth() {
        if (!kisProperties.isConfigured()) {
            return Map.of(
                    "connected", false,
                    "configured", false,
                    "message", "KIS environment variables are not fully configured"
            );
        }

        try {
            brokerClient.issueAccessToken();
            return Map.of(
                    "connected", true,
                    "configured", true,
                    "baseUrl", kisProperties.getBaseUrl(),
                    "account", maskedAccountNumber()
            );
        } catch (RuntimeException e) {
            return Map.of(
                    "connected", false,
                    "configured", true,
                    "baseUrl", kisProperties.getBaseUrl(),
                    "account", maskedAccountNumber(),
                    "message", e.getMessage()
            );
        }
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
