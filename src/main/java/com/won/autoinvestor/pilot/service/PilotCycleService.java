package com.won.autoinvestor.pilot.service;

import com.won.autoinvestor.pilot.domain.PilotMarketTick;
import com.won.autoinvestor.pilot.domain.PilotPosition;
import com.won.autoinvestor.pilot.mapper.PilotMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class PilotCycleService {

    private static final Logger logger = LoggerFactory.getLogger(PilotCycleService.class);
    private static final BigDecimal MIN_KRW_ORDER_AMOUNT = new BigDecimal("1000");
    private static final BigDecimal MIN_USD_ORDER_AMOUNT = new BigDecimal("1");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final PilotMarketDataClient marketDataClient;
    private final PilotMapper pilotMapper;
    private final GlobalSymbolLockService globalSymbolLockService;
    private final TradingLifecycleService tradingLifecycleService;
    private final DynamicBudgetAllocationService budgetAllocationService;

    public PilotCycleService(PilotMarketDataClient marketDataClient,
                             PilotMapper pilotMapper,
                             GlobalSymbolLockService globalSymbolLockService,
                             TradingLifecycleService tradingLifecycleService,
                             DynamicBudgetAllocationService budgetAllocationService) {
        this.marketDataClient = marketDataClient;
        this.pilotMapper = pilotMapper;
        this.globalSymbolLockService = globalSymbolLockService;
        this.tradingLifecycleService = tradingLifecycleService;
        this.budgetAllocationService = budgetAllocationService;
    }

    @Transactional
    public void runCycle() {
        if (pilotMapper.countActivePanicStop() > 0) {
            logger.error("pilot cycle halted: panic stop is active");
            return;
        }

        List<PilotMarketTick> marketTicks = marketDataClient.pollLinkedMarketTicks();
        if (marketTicks == null || marketTicks.isEmpty()) {
            logger.warn("pilot cycle skipped: linked market data is empty");
            return;
        }

        List<PilotMarketTick> validTicks = marketTicks.stream()
                .filter(this::isValidLinkedTick)
                .toList();
        if (validTicks.isEmpty()) {
            logger.warn("pilot cycle skipped: linked market data validation failed");
            return;
        }

        String observedAt = now();
        for (PilotMarketTick tick : validTicks) {
            processTick(tick, observedAt);
        }
    }

    private void processTick(PilotMarketTick tick, String observedAt) {
        PilotPosition position = pilotMapper.selectOpenPositionBySymbol(tick.getSymbol());
        if (position == null) {
            openMinimumPilotPosition(tick, observedAt);
            return;
        }

        long survivalSeconds = calculateSurvivalSeconds(position.getOpenedAt(), observedAt);
        pilotMapper.touchOpenPosition(position.getId(), observedAt);
        pilotMapper.touchPilotBalance(position.getSymbol(), observedAt);
        pilotMapper.insertObservation(
                tick.getSymbol(),
                tick.getMarketCurrency(),
                "LINKED_TICK_VALID",
                "SMALL_POSITION_SURVIVAL",
                position.getStatus(),
                survivalSeconds,
                observedAt
        );
        pilotMapper.insertMarketObservationData(
                tick.getSymbol(),
                tick.getMarketCurrency(),
                "LINKED_TICK_VALID",
                "SMALL_POSITION_SURVIVAL",
                "보유",
                survivalSeconds,
                gradeFor(tick.getSymbol()),
                observedAt
        );
    }

    private void openMinimumPilotPosition(PilotMarketTick tick, String observedAt) {
        String grade = gradeFor(tick.getSymbol());
        if ("BLACK".equalsIgnoreCase(grade)) {
            logger.warn("pilot entry rejected by BLACK grade: symbol={}", tick.getSymbol());
            pilotMapper.insertMarketObservationData(
                    tick.getSymbol(),
                    tick.getMarketCurrency(),
                    "LINKED_TICK_VALID",
                    "BLACK_GRADE_REJECTED",
                    "진입거부",
                    0L,
                    grade,
                    observedAt
            );
            return;
        }

        if (globalSymbolLockService.isLocked(tick.getSymbol())) {
            logger.warn("pilot entry rejected by global single-symbol lock: symbol={}", tick.getSymbol());
            pilotMapper.insertMarketObservationData(
                    tick.getSymbol(),
                    tick.getMarketCurrency(),
                    "LINKED_TICK_VALID",
                    "GLOBAL_SYMBOL_LOCK_REJECTED",
                    "진입거부",
                    0L,
                    grade,
                    observedAt
            );
            return;
        }

        BigDecimal orderAmount = minimumOrderAmount(tick.getMarketCurrency());
        BigDecimal referencePrice = referencePrice(tick);
        if (!budgetAllocationService.canAllocatePilot(tick.getMarketCurrency(), orderAmount)) {
            logger.warn("pilot entry rejected by budget allocation: symbol={}, currency={}, orderAmount={}",
                    tick.getSymbol(), tick.getMarketCurrency(), orderAmount);
            pilotMapper.insertMarketObservationData(
                    tick.getSymbol(),
                    tick.getMarketCurrency(),
                    "LINKED_TICK_VALID",
                    "PILOT_BUDGET_REJECTED",
                    "진입거부",
                    0L,
                    grade,
                    observedAt
            );
            return;
        }

        BigDecimal orderQuantity = orderAmount.divide(referencePrice, 16, RoundingMode.DOWN);

        PilotPosition position = new PilotPosition();
        position.setSymbol(tick.getSymbol());
        position.setMarketCurrency(tick.getMarketCurrency());
        position.setQuantity(orderQuantity);
        position.setInvestedAmount(orderAmount);
        position.setAverageEntryPrice(referencePrice);
        position.setStatus("OPEN");
        position.setOpenedAt(observedAt);
        position.setUpdatedAt(observedAt);
        pilotMapper.insertOpenPosition(position);
        pilotMapper.insertPilotBalance(position);
        tradingLifecycleService.logInitialPurchase(
                "PILOT",
                tick.getSymbol(),
                tick.getMarketCurrency(),
                referencePrice,
                orderQuantity,
                orderAmount,
                observedAt
        );

        pilotMapper.insertOrderIntent(
                tick.getSymbol(),
                tick.getMarketCurrency(),
                "BUY",
                orderAmount.toPlainString(),
                orderQuantity.toPlainString(),
                referencePrice.toPlainString(),
                "MINIMUM_FRACTIONAL_OBSERVATION_ENTRY",
                "CREATED",
                observedAt
        );
        pilotMapper.insertObservation(
                tick.getSymbol(),
                tick.getMarketCurrency(),
                "LINKED_TICK_VALID",
                "MINIMUM_FRACTIONAL_ENTRY",
                "OPEN",
                0L,
                observedAt
        );
        pilotMapper.insertMarketObservationData(
                tick.getSymbol(),
                tick.getMarketCurrency(),
                "LINKED_TICK_VALID",
                "MINIMUM_FRACTIONAL_ENTRY",
                "보유",
                0L,
                grade,
                observedAt
        );
    }

    private boolean isValidLinkedTick(PilotMarketTick tick) {
        if (tick == null) {
            return false;
        }
        return hasText(tick.getSymbol())
                && hasText(tick.getMarketCurrency())
                && isSupportedCurrency(tick.getMarketCurrency())
                && hasText(tick.getTradedAt())
                && tick.getLastPrice() != null
                && tick.getLastPrice().signum() > 0
                && referencePrice(tick).signum() > 0;
    }

    private BigDecimal referencePrice(PilotMarketTick tick) {
        if (tick.getAskPrice() != null && tick.getAskPrice().signum() > 0) {
            return tick.getAskPrice();
        }
        return tick.getLastPrice();
    }

    private BigDecimal minimumOrderAmount(String marketCurrency) {
        if ("KRW".equalsIgnoreCase(marketCurrency)) {
            return MIN_KRW_ORDER_AMOUNT;
        }
        if ("USD".equalsIgnoreCase(marketCurrency)) {
            return MIN_USD_ORDER_AMOUNT;
        }
        throw new IllegalArgumentException("unsupported pilot market currency: " + marketCurrency);
    }

    private boolean isSupportedCurrency(String marketCurrency) {
        return "KRW".equalsIgnoreCase(marketCurrency) || "USD".equalsIgnoreCase(marketCurrency);
    }

    private String gradeFor(String symbol) {
        String grade = pilotMapper.selectLatestAssetGrade(symbol);
        if (grade == null || grade.isBlank()) {
            return "GRAY";
        }
        return grade;
    }

    private long calculateSurvivalSeconds(String openedAt, String observedAt) {
        try {
            OffsetDateTime opened = OffsetDateTime.parse(openedAt, TIME_FORMATTER);
            OffsetDateTime observed = OffsetDateTime.parse(observedAt, TIME_FORMATTER);
            return Math.max(0L, Duration.between(opened, observed).toSeconds());
        } catch (RuntimeException e) {
            logger.warn("failed to calculate pilot survival seconds: openedAt={}, observedAt={}", openedAt, observedAt);
            return 0L;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String now() {
        return OffsetDateTime.now().format(TIME_FORMATTER);
    }
}
