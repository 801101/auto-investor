package com.won.autoinvestor.pilot;

import com.won.autoinvestor.common.kis.BrokerClient;
import com.won.autoinvestor.common.util.MapUtils;
import com.won.autoinvestor.common.kis.KisProperties;
import com.won.autoinvestor.common.config.InvestmentProperties;
import com.won.autoinvestor.common.config.RuntimeProperties;
import com.won.autoinvestor.common.trade.LifecycleEventType;
import com.won.autoinvestor.common.trade.OrderExecutor;
import com.won.autoinvestor.common.trade.OrderSafetyService;
import com.won.autoinvestor.common.trade.OrderSizingService;
import com.won.autoinvestor.common.trade.TradingStatus;
import com.won.autoinvestor.common.trade.AccountSyncStateService;
import com.won.autoinvestor.common.trade.DomesticStockCandidateService;
import com.won.autoinvestor.common.trade.OverseasStockCandidateService;
import com.won.autoinvestor.common.scheduler.SchedulerLockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class PilotService {

    private static final Logger logger = LoggerFactory.getLogger(PilotService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final String SCHEDULER_TYPE = "TRADING_CYCLE";

    private final SchedulerLockService schedulerLockService;
    private final PilotMapper tradingMapper;
    private final InvestmentProperties investmentProperties;
    private final RuntimeProperties runtimeProperties;
    private final BrokerClient brokerClient;
    private final OrderSizingService orderSizingService;
    private final OrderSafetyService orderSafetyService;
    private final OrderExecutor orderExecutor;
    private final KisProperties kisProperties;
    private final OverseasStockCandidateService overseasStockCandidateService;
    private final DomesticStockCandidateService domesticStockCandidateService;
    private final AccountSyncStateService accountSyncStateService;

    public PilotService(SchedulerLockService schedulerLockService,
                               PilotMapper tradingMapper,
                               InvestmentProperties investmentProperties,
                               RuntimeProperties runtimeProperties,
                               BrokerClient brokerClient,
                               OrderSizingService orderSizingService,
                               OrderSafetyService orderSafetyService,
                               OrderExecutor orderExecutor,
                               KisProperties kisProperties,
                               OverseasStockCandidateService overseasStockCandidateService,
                               DomesticStockCandidateService domesticStockCandidateService,
                               AccountSyncStateService accountSyncStateService) {
        this.schedulerLockService = schedulerLockService;
        this.tradingMapper = tradingMapper;
        this.investmentProperties = investmentProperties;
        this.runtimeProperties = runtimeProperties;
        this.brokerClient = brokerClient;
        this.orderSizingService = orderSizingService;
        this.orderSafetyService = orderSafetyService;
        this.orderExecutor = orderExecutor;
        this.kisProperties = kisProperties;
        this.overseasStockCandidateService = overseasStockCandidateService;
        this.domesticStockCandidateService = domesticStockCandidateService;
        this.accountSyncStateService = accountSyncStateService;
    }

    public void runTradingCycle() {
        runLocked(SCHEDULER_TYPE, () -> {
            logger.info("trading cycle started. instanceId={}, tradingEnabled={}",
                    runtimeProperties.getInstanceId(),
                    runtimeProperties.isTradingEnabled());
            if (!runtimeProperties.isTradingEnabled()) {
                insertAuditLog("TRADING_CYCLE_SKIPPED", null, "runtime.trading-enabled=false");
                logger.info("trading cycle skipped because runtime.trading-enabled=false");
                return;
            }
            syncAccount();
            runBuyPipeline();
        });
    }

    public void runMaintenanceCycle() {
        runLocked("ORDER_MAINTENANCE", () -> {
            logger.info("order maintenance cycle started");
            syncAccount();
        });
    }

    public Map<String, Object> getSystemStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("running", true);
        status.put("tradingEnabled", runtimeProperties.isTradingEnabled());
        status.put("orderUnitType", investmentProperties.getOrderUnitType());
        status.put("unitAmount", investmentProperties.getUnitAmount());
        status.put("unitShares", investmentProperties.getUnitShares());
        status.put("allowDuplicateStock", investmentProperties.getAllowDuplicateStock());
        status.put("maxHoldings", investmentProperties.getMaxHoldings());
        status.put("takeProfitEnabled", investmentProperties.getTakeProfit().isEnabled());
        status.put("takeProfitRate", investmentProperties.getTakeProfitRate());
        status.put("stopLossEnabled", investmentProperties.getStopLoss().isEnabled());
        status.put("stopLossRate", investmentProperties.getStopLoss().getRate());
        status.put("activePanicStopCount", tradingMapper.countActivePanicStop());
        status.put("kisConfigured", kisProperties.isConfigured());
        status.put("kisAccountMode", kisProperties.getAccountMode());
        status.put("kisBaseUrl", kisProperties.getBaseUrl());
        status.put("kisAccount", maskedAccountNumber());
        return status;
    }

    public Map<String, Object> getKisHealth() {
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
                    "accountMode", kisProperties.getAccountMode(),
                    "baseUrl", kisProperties.getBaseUrl(),
                    "account", maskedAccountNumber()
            );
        } catch (RuntimeException e) {
            return Map.of(
                    "connected", false,
                    "configured", true,
                    "accountMode", kisProperties.getAccountMode(),
                    "baseUrl", kisProperties.getBaseUrl(),
                    "account", maskedAccountNumber(),
                    "message", e.getMessage()
            );
        }
    }

    public Map<String, Object> getPositions() {
        return Map.of("activePositions", tradingMapper.countActivePositions());
    }

    public Map<String, Object> getOverseasDashboard() {
        return Map.of("rows", overseasStockCandidateService.findDashboardRows());
    }

    public Map<String, Object> getDomesticDashboard() {
        return Map.of("rows", domesticStockCandidateService.findDashboardRows());
    }

    public void syncAccount() {
        if (!kisProperties.isConfigured()) {
            String message = "KIS credentials are not configured. account synchronization skipped.";
            accountSyncStateService.recordFailure(message);
            insertAuditLog("ACCOUNT_SYNC_SKIPPED", null, message);
            logger.warn(message);
            return;
        }

        try {
            brokerClient.getAccountBalance();
            List<Map<String, Object>> holdings = brokerClient.getHoldings();
            syncHoldingsToLifecycle(holdings);
            accountSyncStateService.recordSuccess();
            insertAuditLog("ACCOUNT_SYNC", null, "account and holdings synchronized");
            logger.info("account synchronization completed");
        } catch (UnsupportedOperationException e) {
            accountSyncStateService.recordFailure(e.getMessage());
            insertAuditLog("ACCOUNT_SYNC_SKIPPED", null, e.getMessage());
            logger.warn("account synchronization skipped: {}", e.getMessage());
        } catch (RuntimeException e) {
            if (isKisRateLimit(e)) {
                String message = "KIS rate limit reached during account synchronization. sync skipped.";
                accountSyncStateService.recordFailure(message);
                insertAuditLog("ACCOUNT_SYNC_SKIPPED", null, message);
                logger.warn("{} cause={}", message, e.getMessage());
                return;
            }
            accountSyncStateService.recordFailure(e.getMessage());
            insertAuditLog("ACCOUNT_SYNC_FAILED", null, e.getMessage());
            logger.error("account synchronization failed", e);
        }
    }

    private boolean isKisRateLimit(RuntimeException e) {
        String message = e.getMessage();
        return message != null && (message.contains("EGW00133") || message.contains("EGW00201"));
    }

    private void syncHoldingsToLifecycle(List<Map<String, Object>> holdings) {
        if (holdings == null || holdings.isEmpty()) {
            return;
        }
        String syncedAt = now();
        for (Map<String, Object> holding : holdings) {
            if (MapUtils.string(holding, "stockCode") == null || Objects.requireNonNull(MapUtils.string(holding, "stockCode")).isBlank()
                    || MapUtils.decimal(holding, "quantity").signum() <= 0) {
                continue;
            }
            BigDecimal averagePrice = zeroIfNull(MapUtils.decimal(holding, "averagePrice"));
            BigDecimal quantity = MapUtils.decimal(holding, "quantity");
            BigDecimal investedAmount = averagePrice.multiply(quantity);
            String stockCode = MapUtils.string(holding, "stockCode");
            Long positionId = tradingMapper.selectActivePositionIdByStockCode(MapUtils.map("stockCode", stockCode));
            tradingMapper.markAcceptedBuyOrdersFilledByStockCode(MapUtils.map("stockCode", stockCode, "filledAt", syncedAt));
            if (positionId == null) {
                tradingMapper.insertSyncedPosition(positionMap(
                        null, stockCode, MapUtils.string(holding, "stockName"), TradingStatus.WHITE.name(),
                        averagePrice, quantity, investedAmount, syncedAt));
                Long newPositionId = tradingMapper.selectActivePositionIdByStockCode(MapUtils.map("stockCode", stockCode));
                recordInitialLifecycleEvents(newPositionId, holding, averagePrice, quantity, syncedAt);
                logger.info("synced new holding into lifecycle. stockCode={}, quantity={}", stockCode, quantity);
                continue;
            }
            tradingMapper.updateSyncedPosition(positionMap(
                    positionId, stockCode, MapUtils.string(holding, "stockName"), TradingStatus.WHITE.name(),
                    averagePrice, quantity, investedAmount, syncedAt));
        }
    }

    private void recordInitialLifecycleEvents(Long positionId,
                                              Map<String, Object> holding,
                                              BigDecimal averagePrice,
                                              BigDecimal quantity,
                                              String syncedAt) {
        if (positionId == null) {
            return;
        }
        OffsetDateTime occurredAt = OffsetDateTime.parse(syncedAt, TIME_FORMATTER);
        BigDecimal investedAmount = averagePrice.multiply(quantity);
        recordInitialLifecycleEvent(positionId, LifecycleEventType.BUY_FILLED, holding, averagePrice, quantity, investedAmount, occurredAt);
        recordInitialLifecycleEvent(positionId, LifecycleEventType.LIFECYCLE_STARTED, holding, averagePrice, quantity, investedAmount, occurredAt);
        recordInitialLifecycleEvent(positionId, LifecycleEventType.WHITE_ENTERED, holding, averagePrice, quantity, investedAmount, occurredAt);
    }

    private void recordInitialLifecycleEvent(Long positionId,
                                             LifecycleEventType eventType,
                                             Map<String, Object> holding,
                                             BigDecimal averagePrice,
                                             BigDecimal quantity,
                                             BigDecimal investedAmount,
                                             OffsetDateTime occurredAt) {
        String stockCode = MapUtils.string(holding, "stockCode");
        recordLifecycleEvent(MapUtils.map(
                "lifecycleId", positionId,
                "eventType", eventType,
                "previousState", null,
                "newState", TradingStatus.WHITE,
                "currentPrice", averagePrice,
                "averageBuyPrice", averagePrice,
                "referencePrice", averagePrice,
                "highestPrice", averagePrice,
                "lowestPrice", averagePrice,
                "holdingQuantity", quantity,
                "returnRate", BigDecimal.ZERO,
                "grayTradingDays", 0,
                "reason", "ACCOUNT_HOLDING_SYNC",
                "orderId", null,
                "executionId", stockCode,
                "idempotencyKey", "account-sync:" + stockCode + ":" + positionId + ":" + eventType.name(),
                "occurredAt", occurredAt
        ));
    }

    private void recordLifecycleEvent(Map<String, Object> event) {
        LifecycleEventType eventType = lifecycleEventType(event);
        if (eventType == null) {
            return;
        }
        String key = MapUtils.string(event, "idempotencyKey");
        if (key == null || key.isBlank()) {
            key = defaultLifecycleIdempotencyKey(event);
        }
        tradingMapper.insertTradeLifecycleHistory(MapUtils.map(
                "lifecycleId", MapUtils.longValue(event, "lifecycleId"),
                "eventType", eventType.name(),
                "previousState", statusName(event, "previousState"),
                "newState", statusName(event, "newState"),
                "currentPrice", amount(MapUtils.decimal(event, "currentPrice")),
                "averageBuyPrice", amount(MapUtils.decimal(event, "averageBuyPrice")),
                "referencePrice", amount(MapUtils.decimal(event, "referencePrice")),
                "highestPrice", amount(MapUtils.decimal(event, "highestPrice")),
                "lowestPrice", amount(MapUtils.decimal(event, "lowestPrice")),
                "holdingQuantity", amount(MapUtils.decimal(event, "holdingQuantity")),
                "returnRate", amount(MapUtils.decimal(event, "returnRate")),
                "grayTradingDays", MapUtils.integer(event, "grayTradingDays"),
                "reason", MapUtils.string(event, "reason"),
                "orderId", MapUtils.value(event, "orderId") == null ? null : MapUtils.longValue(event, "orderId"),
                "executionId", MapUtils.string(event, "executionId"),
                "idempotencyKey", key,
                "occurredAt", MapUtils.offsetDateTime(event, "occurredAt") == null ? OffsetDateTime.now().format(TIME_FORMATTER) : MapUtils.offsetDateTime(event, "occurredAt").format(TIME_FORMATTER)
        ));
    }

    private String defaultLifecycleIdempotencyKey(Map<String, Object> event) {
        return MapUtils.longValue(event, "lifecycleId") + ":" + lifecycleEventType(event) + ":" + nullable(MapUtils.value(event, "newState"))
                + ":" + nullable(MapUtils.value(event, "grayTradingDays")) + ":" + nullable(MapUtils.value(event, "orderId")) + ":" + nullable(MapUtils.value(event, "executionId"));
    }

    private LifecycleEventType lifecycleEventType(Map<String, Object> event) {
        Object value = MapUtils.value(event, "eventType");
        if (value instanceof LifecycleEventType lifecycleEventType) {
            return lifecycleEventType;
        }
        return value == null ? null : LifecycleEventType.valueOf(value.toString());
    }

    private String statusName(Map<String, Object> event, String key) {
        Object value = MapUtils.value(event, key);
        if (value instanceof TradingStatus tradingStatus) {
            return tradingStatus.name();
        }
        return value == null ? null : value.toString();
    }

    private void runLocked(String schedulerType, Runnable task) {
        String startedAt = now();
        if (!schedulerLockService.tryLock(schedulerType)) {
            logger.warn("scheduler skipped by running lock: {}", schedulerType);
            insertSchedulerExecution(schedulerType, startedAt, now(), "SKIPPED", "already running");
            return;
        }

        try {
            task.run();
            insertSchedulerExecution(schedulerType, startedAt, now(), "SUCCESS", "completed");
        } catch (Exception e) {
            logger.error("scheduler failed: {}", schedulerType, e);
            insertSchedulerExecution(schedulerType, startedAt, now(), "FAILED", e.getMessage());
        } finally {
            schedulerLockService.unlock(schedulerType);
        }
    }

    private void runBuyPipeline() {
        Map<String, Object> accountBalance = brokerClient.getAccountBalance();
        List<Map<String, Object>> holdings = brokerClient.getHoldings();
        BigDecimal currentTotalHoldingQuantity = holdings.stream()
                .map(holding -> MapUtils.decimal(holding, "quantity"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String decisionCycleId = "cycle-" + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" + UUID.randomUUID();
        if (isOverseasMarket()) {
            List<Map<String, Object>> candidates = overseasStockCandidateService.findOrderTargetsForCycle();
            if (candidates.isEmpty()) {
                logger.info("buy pipeline skipped because overseas candidate was not selected");
                return;
            }
            for (Map<String, Object> candidate : candidates) {
                tryBuyCandidate(candidate, accountBalance, BigDecimal.ZERO, decisionCycleId);
            }
            return;
        }

        List<Map<String, Object>> candidates = domesticStockCandidateService.findOrderTargetsForCycle();
        if (candidates.isEmpty()) {
            logger.info("buy pipeline skipped because domestic candidate was not selected");
            return;
        }
        for (Map<String, Object> candidate : candidates) {
            tryBuyCandidate(candidate, accountBalance, BigDecimal.ZERO, decisionCycleId);
        }
    }

    private void tryBuyCandidate(Map<String, Object> candidate,
                                 Map<String, Object> accountBalance,
                                 BigDecimal currentTotalHoldingQuantity,
                                 String decisionCycleId) {
            String normalizedStockCode = MapUtils.string(candidate, "symbol");
            boolean validationOrder = MapUtils.bool(candidate, "validationOrder");
            String marketCode = MapUtils.bool(candidate, "overseas") ? MapUtils.string(candidate, "exchangeCode") : MapUtils.string(candidate, "marketCode");
            String orderPurpose = validationOrder ? "FRACTIONAL_VALIDATION" : "AUTO";
            String orderReason = validationOrder ? "FRACTIONAL_VALIDATION" : "AUTO_BUY";
            String idempotencyKey = maskedAccountNumber() + "|" + orderPurpose + "|BUY|" + normalizedStockCode + "|" + decisionCycleId;
            Map<String, Object> currentPrice = brokerClient.getCurrentPrice(normalizedStockCode);
            Map<String, Object> buyableBalance = isOverseasMarket()
                    ? brokerClient.getBuyableBalance(normalizedStockCode, MapUtils.decimal(currentPrice, "price"))
                    : accountBalance;
            Map<String, Object> sizingResult = orderSizingService.calculateBuyQuantity(currentPrice, buyableBalance, currentTotalHoldingQuantity);
            if (!MapUtils.bool(sizingResult, "orderable")) {
                recordSkippedBuyOrder(normalizedStockCode, currentPrice, MapUtils.string(sizingResult, "reason"), decisionCycleId, idempotencyKey, validationOrder);
                insertAuditLog("BUY_SKIPPED", normalizedStockCode, MapUtils.string(sizingResult, "reason"));
                logger.info("buy skipped. stockCode={}, reason={}", normalizedStockCode, MapUtils.string(sizingResult, "reason"));
                if (validationOrder) {
                    overseasStockCandidateService.recordFractionalValidationResult(
                            normalizedStockCode,
                            marketCode,
                            rejected(MapUtils.string(sizingResult, "reason")),
                            !runtimeProperties.isTradingEnabled()
                    );
                } else {
                    recordBuyResult(candidate, false, MapUtils.string(sizingResult, "reason"));
                }
                return;
            }

            Map<String, Object> safetyResult = orderSafetyService.validateBuy(
                    normalizedStockCode,
                    MapUtils.decimal(sizingResult, "quantity"),
                    MapUtils.decimal(sizingResult, "expectedAmount"),
                    buyableBalance,
                    true
            );
            if (!MapUtils.bool(safetyResult, "orderAllowed")) {
                recordBlockedBuyOrder(normalizedStockCode, currentPrice, sizingResult, MapUtils.string(safetyResult, "reason"), decisionCycleId, idempotencyKey, validationOrder);
                insertAuditLog("BUY_BLOCKED", normalizedStockCode, MapUtils.string(safetyResult, "reason"));
                logger.info("buy blocked. stockCode={}, reason={}", normalizedStockCode, MapUtils.string(safetyResult, "reason"));
                if (validationOrder) {
                    overseasStockCandidateService.recordFractionalValidationResult(
                            normalizedStockCode,
                            marketCode,
                            rejected(MapUtils.string(safetyResult, "reason")),
                            !runtimeProperties.isTradingEnabled()
                    );
                } else {
                    recordBuyResult(candidate, false, MapUtils.string(safetyResult, "reason"));
                }
                return;
            }

            if (validationOrder) {
                overseasStockCandidateService.recordFractionalValidationAttempt(normalizedStockCode, marketCode);
            } else {
                recordBuyAttempt(candidate);
            }
            Map<String, Object> request = MapUtils.map(
                    "stockCode", normalizedStockCode,
                    "orderQuantity", MapUtils.decimal(sizingResult, "quantity"),
                    "orderPrice", orderPrice(currentPrice),
                    "orderAmount", MapUtils.decimal(sizingResult, "expectedAmount"),
                    "reason", orderReason,
                    "decisionCycleId", decisionCycleId,
                    "idempotencyKey", idempotencyKey,
                    "instanceId", runtimeProperties.getInstanceId(),
                    "maskedAccount", maskedAccountNumber(),
                    "currentPrice", MapUtils.decimal(currentPrice, "price"),
                    "currentPriceAt", MapUtils.offsetDateTime(currentPrice, "receivedAt"),
                    "exitReason", null
            );
            Map<String, Object> orderResult = orderExecutor.buy(request);
            if (MapUtils.bool(orderResult, "accepted")) {
                insertAuditLog("BUY_REQUESTED", normalizedStockCode,
                        "accepted=true, status=" + MapUtils.string(orderResult, "status") + ", brokerOrderId=" + MapUtils.string(orderResult, "brokerOrderId"));
            }
            if (validationOrder) {
                overseasStockCandidateService.recordFractionalValidationResult(
                        normalizedStockCode,
                        marketCode,
                        orderResult,
                        !runtimeProperties.isTradingEnabled()
                );
            } else {
                recordBuyResult(candidate, MapUtils.bool(orderResult, "accepted"), MapUtils.string(orderResult, "message"));
            }
            logger.info("buy requested. stockCode={}, quantity={}, expectedAmount={}, accepted={}, status={}, message={}",
                    normalizedStockCode, MapUtils.decimal(sizingResult, "quantity"), MapUtils.decimal(sizingResult, "expectedAmount"),
                    MapUtils.bool(orderResult, "accepted"), MapUtils.string(orderResult, "status"), MapUtils.string(orderResult, "message"));
    }

    private void recordBuyAttempt(Map<String, Object> candidate) {
        if (MapUtils.bool(candidate, "overseas")) {
            overseasStockCandidateService.recordBuyAttempt(MapUtils.string(candidate, "symbol"), MapUtils.string(candidate, "exchangeCode"));
            return;
        }
        domesticStockCandidateService.recordBuyAttempt(MapUtils.string(candidate, "symbol"), MapUtils.string(candidate, "marketCode"));
    }

    private void recordBuyResult(Map<String, Object> candidate, boolean accepted, String reason) {
        if (MapUtils.bool(candidate, "overseas")) {
            overseasStockCandidateService.recordBuyResult(MapUtils.string(candidate, "symbol"), MapUtils.string(candidate, "exchangeCode"), accepted, reason);
            return;
        }
        domesticStockCandidateService.recordBuyResult(MapUtils.string(candidate, "symbol"), MapUtils.string(candidate, "marketCode"), accepted, reason);
    }

    private void recordSkippedBuyOrder(String stockCode,
                                       Map<String, Object> currentPrice,
                                       String reason,
                                       String decisionCycleId,
                                       String idempotencyKey,
                                       boolean validationOrder) {
        tradingMapper.insertOrderRecordDetailed(orderRecord(
                stockCode, "BUY", "0", orderPrice(currentPrice).toPlainString(), "0",
                "SKIPPED", reason, decisionCycleId, idempotencyKey, reason, null, currentPrice));
        if (validationOrder) {
            insertAuditLog("FRACTIONAL_VALIDATION_SKIPPED", stockCode, reason);
        }
    }

    private void recordBlockedBuyOrder(String stockCode,
                                       Map<String, Object> currentPrice,
                                       Map<String, Object> sizingResult,
                                       String reason,
                                       String decisionCycleId,
                                       String idempotencyKey,
                                       boolean validationOrder) {
        tradingMapper.insertOrderRecordDetailed(orderRecord(
                stockCode, "BUY", MapUtils.decimal(sizingResult, "quantity").toPlainString(), orderPrice(currentPrice).toPlainString(),
                MapUtils.decimal(sizingResult, "expectedAmount").toPlainString(), "BLOCKED", reason, decisionCycleId,
                idempotencyKey, reason, null, currentPrice));
        if (validationOrder) {
            insertAuditLog("FRACTIONAL_VALIDATION_BLOCKED", stockCode, reason);
        }
    }

    private BigDecimal orderPrice(Map<String, Object> currentPrice) {
        if ("OVERSEAS".equalsIgnoreCase(investmentProperties.getMarketType())) {
            return normalizeOverseasOrderPrice(MapUtils.decimal(currentPrice, "price"));
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal normalizeOverseasOrderPrice(BigDecimal price) {
        if (price == null) {
            return BigDecimal.ZERO;
        }
        if (price.compareTo(BigDecimal.ONE) >= 0) {
            return price.setScale(2, RoundingMode.HALF_UP);
        }
        return price.setScale(4, RoundingMode.HALF_UP);
    }

    private boolean isOverseasMarket() {
        return "OVERSEAS".equalsIgnoreCase(investmentProperties.getMarketType());
    }

    private String maskedAccountNumber() {
        String accountNumber = kisProperties.getAccountNumber();
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String plain(BigDecimal value) {
        return zeroIfNull(value).stripTrailingZeros().toPlainString();
    }

    private String amount(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private String nullable(Object value) {
        return value == null ? "" : value.toString();
    }

    private String now() {
        return OffsetDateTime.now().format(TIME_FORMATTER);
    }

    private Map<String, Object> positionMap(Long positionId,
                                            String stockCode,
                                            String stockName,
                                            String status,
                                            BigDecimal averagePrice,
                                            BigDecimal quantity,
                                            BigDecimal investedAmount,
                                            String syncedAt) {
        return MapUtils.map(
                "positionId", positionId,
                "stockCode", stockCode,
                "stockName", stockName,
                "status", status,
                "purchasePrice", plain(averagePrice),
                "purchaseQuantity", plain(quantity),
                "investedAmount", plain(investedAmount),
                "currentPrice", plain(averagePrice),
                "currentValuationAmount", plain(investedAmount),
                "profitRate", "0",
                "lastEvaluatedPrice", plain(averagePrice),
                "statusReferencePrice", plain(averagePrice),
                "grayTradingDays", 0,
                "averageBuyPrice", plain(averagePrice),
                "referencePrice", plain(averagePrice),
                "highestPrice", plain(averagePrice),
                "lowestPrice", plain(averagePrice),
                "holdingQuantity", plain(quantity),
                "returnRate", "0",
                "lastEvaluatedAt", syncedAt,
                "createdAt", syncedAt,
                "updatedAt", syncedAt
        );
    }

    private void insertAuditLog(String eventType, String stockCode, String details) {
        tradingMapper.insertAuditLog(MapUtils.map(
                "eventType", eventType,
                "stockCode", stockCode,
                "details", details,
                "createdAt", now()
        ));
    }

    private void insertSchedulerExecution(String schedulerType,
                                          String startedAt,
                                          String finishedAt,
                                          String executionStatus,
                                          String message) {
        tradingMapper.insertSchedulerExecution(MapUtils.map(
                "schedulerType", schedulerType,
                "startedAt", startedAt,
                "finishedAt", finishedAt,
                "executionStatus", executionStatus,
                "message", message
        ));
    }

    private Map<String, Object> orderRecord(String stockCode,
                                            String orderType,
                                            String orderQuantity,
                                            String orderPrice,
                                            String orderAmount,
                                            String orderStatus,
                                            String errorMessage,
                                            String decisionCycleId,
                                            String idempotencyKey,
                                            String skipReason,
                                            String exitReason,
                                            Map<String, Object> currentPrice) {
        String requestedAt = now();
        return MapUtils.map(
                "brokerOrderId", null,
                "stockCode", stockCode,
                "orderType", orderType,
                "orderQuantity", orderQuantity,
                "orderPrice", orderPrice,
                "orderAmount", orderAmount,
                "orderStatus", orderStatus,
                "errorMessage", errorMessage,
                "requestedAt", requestedAt,
                "idempotencyKey", idempotencyKey,
                "decisionCycleId", decisionCycleId,
                "instanceId", runtimeProperties.getInstanceId(),
                "maskedAccount", maskedAccountNumber(),
                "skipReason", skipReason,
                "exitReason", exitReason,
                "dryRun", "N",
                "currentPrice", MapUtils.value(currentPrice, "price") == null ? null : MapUtils.decimal(currentPrice, "price").toPlainString(),
                "currentPriceAt", MapUtils.offsetDateTime(currentPrice, "receivedAt") == null ? null : MapUtils.offsetDateTime(currentPrice, "receivedAt").format(TIME_FORMATTER)
        );
    }

    private Map<String, Object> rejected(String message) {
        return MapUtils.map("accepted", false, "brokerOrderId", null, "status", "REJECTED", "message", message);
    }
}
