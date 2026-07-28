package com.won.autoinvestor.trading.service;

import com.won.autoinvestor.pilot.mapper.PilotMapper;
import com.won.autoinvestor.trading.config.InvestmentProperties;
import com.won.autoinvestor.trading.schedule.SchedulerLockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class TradingCycleService {

    private static final Logger logger = LoggerFactory.getLogger(TradingCycleService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final String SCHEDULER_TYPE = "TRADING_CYCLE";

    private final SchedulerLockService schedulerLockService;
    private final AccountSynchronizationService accountSynchronizationService;
    private final PilotMapper pilotMapper;
    private final InvestmentProperties investmentProperties;

    public TradingCycleService(SchedulerLockService schedulerLockService,
                               AccountSynchronizationService accountSynchronizationService,
                               PilotMapper pilotMapper,
                               InvestmentProperties investmentProperties) {
        this.schedulerLockService = schedulerLockService;
        this.accountSynchronizationService = accountSynchronizationService;
        this.pilotMapper = pilotMapper;
        this.investmentProperties = investmentProperties;
    }

    public void runTradingCycle() {
        runLocked(SCHEDULER_TYPE, () -> {
            logger.info("trading cycle started. liveTradingEnabled={}", investmentProperties.isLiveTradingEnabled());
            accountSynchronizationService.syncAccount();
            pilotMapper.insertAuditLog("TRADING_CYCLE_TODO", null,
                    "TODO: sync open orders, sync prices, evaluate states, sell BLACK, generate candidates, dry-run buy", now());
        });
    }

    public void runMaintenanceCycle() {
        runLocked("ORDER_MAINTENANCE", () -> {
            logger.info("order maintenance cycle started");
            accountSynchronizationService.syncAccount();
            pilotMapper.insertAuditLog("ORDER_MAINTENANCE_TODO", null,
                    "TODO: sync open orders, reflect fills, retry failed orders, clean locks", now());
        });
    }

    private void runLocked(String schedulerType, Runnable task) {
        String startedAt = now();
        if (!schedulerLockService.tryLock(schedulerType)) {
            logger.warn("scheduler skipped by running lock: {}", schedulerType);
            pilotMapper.insertSchedulerExecution(schedulerType, startedAt, now(), "SKIPPED", "already running");
            return;
        }

        try {
            task.run();
            pilotMapper.insertSchedulerExecution(schedulerType, startedAt, now(), "SUCCESS", "completed");
        } catch (Exception e) {
            logger.error("scheduler failed: {}", schedulerType, e);
            pilotMapper.insertSchedulerExecution(schedulerType, startedAt, now(), "FAILED", e.getMessage());
        } finally {
            schedulerLockService.unlock(schedulerType);
        }
    }

    private String now() {
        return OffsetDateTime.now().format(TIME_FORMATTER);
    }
}
