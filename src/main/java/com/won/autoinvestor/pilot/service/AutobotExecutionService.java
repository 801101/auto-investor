package com.won.autoinvestor.pilot.service;

import com.won.autoinvestor.pilot.mapper.PilotMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class AutobotExecutionService {

    private static final BigDecimal ONE_SHARE = new BigDecimal("1");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final GlobalSymbolLockService globalSymbolLockService;
    private final PilotMapper pilotMapper;
    private final TradingLifecycleService tradingLifecycleService;
    private final DynamicBudgetAllocationService budgetAllocationService;

    public AutobotExecutionService(GlobalSymbolLockService globalSymbolLockService,
                                   PilotMapper pilotMapper,
                                   TradingLifecycleService tradingLifecycleService,
                                   DynamicBudgetAllocationService budgetAllocationService) {
        this.globalSymbolLockService = globalSymbolLockService;
        this.pilotMapper = pilotMapper;
        this.tradingLifecycleService = tradingLifecycleService;
        this.budgetAllocationService = budgetAllocationService;
    }

    @Transactional
    public OrderPipelineResult executeOneSharePurchase(String symbol,
                                                       String marketCurrency,
                                                       BigDecimal referencePrice,
                                                       String requestedGrade) {
        if (pilotMapper.countActivePanicStop() > 0) {
            return OrderPipelineResult.rejected("panic stop is active");
        }

        if (!hasText(symbol) || !hasText(marketCurrency) || referencePrice == null || referencePrice.signum() <= 0) {
            return OrderPipelineResult.rejected("linked order data validation failed");
        }

        String latestGrade = pilotMapper.selectLatestAssetGrade(symbol);
        String grade = hasText(latestGrade) ? latestGrade : requestedGrade;
        if (!"WHITE".equalsIgnoreCase(grade)) {
            return OrderPipelineResult.rejected("autobot requires WHITE grade. current grade: " + grade);
        }

        if (globalSymbolLockService.isLocked(symbol)) {
            return OrderPipelineResult.rejected("global single-symbol lock is active: " + symbol);
        }

        BigDecimal orderAmount = referencePrice.multiply(ONE_SHARE);
        if (!budgetAllocationService.canAllocateAutobot(marketCurrency, orderAmount)) {
            return OrderPipelineResult.rejected("autobot budget is not available for one-share order: " + symbol);
        }

        String createdAt = OffsetDateTime.now().format(TIME_FORMATTER);
        pilotMapper.insertAutobotOrderIntent(
                symbol,
                marketCurrency,
                "BUY",
                ONE_SHARE.toPlainString(),
                referencePrice.toPlainString(),
                "WHITE",
                "WHITE_GRADE_ONE_SHARE_ENTRY",
                "CREATED",
                createdAt
        );
        pilotMapper.insertAutobotBalance(
                symbol,
                marketCurrency,
                ONE_SHARE.toPlainString(),
                referencePrice.toPlainString(),
                "WHITE",
                createdAt
        );
        tradingLifecycleService.logInitialPurchase(
                "AUTOBOT",
                symbol,
                marketCurrency,
                referencePrice,
                ONE_SHARE,
                orderAmount,
                createdAt
        );

        return OrderPipelineResult.accepted("autobot one-share purchase intent created: " + symbol);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
