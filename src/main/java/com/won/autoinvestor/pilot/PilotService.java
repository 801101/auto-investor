package com.won.autoinvestor.pilot;

import com.won.autoinvestor.common.kis.BrokerClient;
import com.won.autoinvestor.common.util.MapUtils;
import com.won.autoinvestor.common.kis.KisProperties;
import com.won.autoinvestor.common.config.InvestmentProperties;
import com.won.autoinvestor.common.config.RuntimeProperties;
import com.won.autoinvestor.common.trade.ExitReason;
import com.won.autoinvestor.common.trade.LifecycleEventType;
import com.won.autoinvestor.common.trade.OrderExecutor;
import com.won.autoinvestor.common.trade.OrderSafetyService;
import com.won.autoinvestor.common.trade.OrderSizingService;
import com.won.autoinvestor.common.trade.TradingDayService;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final TradingDayService tradingDayService;
    private volatile boolean startupAccountSyncCompleted = false;

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
                               AccountSyncStateService accountSyncStateService,
                               TradingDayService tradingDayService) {
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
        this.tradingDayService = tradingDayService;
    }

    public void runTradingCycle() {
        runLocked(SCHEDULER_TYPE, () -> {
            if (!runtimeProperties.isTradingEnabled()) {
                insertAuditLog("TRADING_CYCLE_SKIPPED", null, "runtime.trading-enabled=false");
                logger.info("trading cycle skipped because runtime.trading-enabled=false");
                return;
            }
            syncAccount();
            if (!accountSyncStateService.isLastSyncSuccessful()) {
                insertAuditLog("TRADING_CYCLE_SKIPPED", null, "ACCOUNT_SYNC_FAILED");
                logger.warn("trading cycle skipped because account synchronization failed");
                return;
            }
            cancelOpenBuyOrders();
            cancelOpenSellOrders();
            evaluateActivePositions();
            processBlackPositions();
            runBuyPipeline();
        });
    }

    public void runMaintenanceCycle() {
        runLocked("ORDER_MAINTENANCE", SCHEDULER_TYPE, () -> {
            syncAccount();
            if (!accountSyncStateService.isLastSyncSuccessful()) {
                logger.warn("maintenance state evaluation skipped because account synchronization failed");
                return;
            }
            if (!runtimeProperties.isTradingEnabled()) {
                logger.info("maintenance state evaluation skipped because runtime.trading-enabled=false");
                return;
            }
            evaluateActivePositions();
        });
    }

    public Map<String, Object> getSystemStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("running", true);
        status.put("tradingEnabled", runtimeProperties.isTradingEnabled());
        status.put("orderUnitType", investmentProperties.getOrderUnitType());
        status.put("unitAmount", investmentProperties.getUnitAmount());
        status.put("unitShares", investmentProperties.getUnitShares());
        status.put("maxHoldingsPerStock", investmentProperties.getMaxHoldingsPerStock());
        status.put("maxHoldings", investmentProperties.getMaxHoldings());
        status.put("takeProfitRate", investmentProperties.getTakeProfitRate());
        status.put("stopLossRate", investmentProperties.getStopLoss().getRate());
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

    public Map<String, Object> getAccount() {
        Map<String, Object> balance = brokerClient.getAccountBalance();
        return Map.of(
                "cashBalance", MapUtils.decimal(balance, "cashBalance"),
                "totalValuationAmount", MapUtils.decimal(balance, "totalValuationAmount"),
                "holdings", brokerClient.getHoldings()
        );
    }

    public Map<String, Object> getOverseasDashboard() {
        return Map.of("rows", overseasStockCandidateService.findDashboardRows());
    }

    public Map<String, Object> getDomesticDashboard() {
        return Map.of("rows", domesticStockCandidateService.findDashboardRows());
    }

    public boolean isStartupAccountSyncCompleted() {
        return startupAccountSyncCompleted;
    }

    public synchronized void runStartupAccountSync() {
        insertAuditLog("ACCOUNT_SYNC_STARTED", null, "startup account synchronization started");
        logger.info("startup account synchronization started");
        try {
            if (!kisProperties.isConfigured()) {
                String message = "KIS credentials are not configured. startup account synchronization skipped.";
                accountSyncStateService.recordFailure(message);
                insertAuditLog("ACCOUNT_SYNC_SKIPPED", null, message);
                logger.warn(message);
                return;
            }
            brokerClient.getAccountBalance();
            List<Map<String, Object>> holdings = brokerClient.getHoldings();
            synchronizeOpenOrders();
            reconcileAccountHoldings(holdings);
            accountSyncStateService.recordSuccess();
            insertAuditLog("ACCOUNT_SYNC_COMPLETED", null, "startup account synchronization completed");
            logger.info("startup account synchronization completed");
            startupAccountSyncCompleted = true;
        } catch (RuntimeException e) {
            accountSyncStateService.recordFailure(e.getMessage());
            insertAuditLog("ACCOUNT_SYNC_FAILED", null, e.getMessage());
            logger.error("startup account synchronization failed", e);
        }
    }

    public synchronized void syncAccount() {
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
            synchronizeOpenOrders();
            reconcileAccountHoldings(holdings);
            accountSyncStateService.recordSuccess();
            insertAuditLog("ACCOUNT_SYNC", null, "account and holdings synchronized");
            logger.debug("account synchronization completed");
            startupAccountSyncCompleted = true;
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

    private void reconcileAccountHoldings(List<Map<String, Object>> holdings) {
        Set<String> accountHoldingCodes = new HashSet<>();
        String syncedAt = now();
        if (holdings != null) {
            for (Map<String, Object> holding : holdings) {
                String stockCode = MapUtils.string(holding, "stockCode");
                BigDecimal quantity = MapUtils.decimal(holding, "quantity");
                if (stockCode == null || stockCode.isBlank() || quantity.signum() <= 0) {
                    continue;
                }
                accountHoldingCodes.add(stockCode);
                reconcileAccountHolding(holding, syncedAt);
            }
        }

        for (Map<String, Object> position : tradingMapper.selectActivePositions()) {
            String stockCode = MapUtils.string(position, "stockCode");
            if (stockCode == null || accountHoldingCodes.contains(stockCode)) {
                continue;
            }
            closePositionMissingFromAccount(position, syncedAt);
        }
    }

    private void synchronizeOpenOrders() {
        List<Map<String, Object>> localOrders = tradingMapper.selectOrdersForStatusSync();
        if (localOrders.isEmpty()) {
            return;
        }

        List<Map<String, Object>> brokerOrders;
        try {
            brokerOrders = brokerClient.getOrderStatuses(Map.of());
        } catch (UnsupportedOperationException e) {
            insertAuditLog("ORDER_STATUS_SYNC_UNAVAILABLE", null, e.getMessage());
            logger.warn("KIS order status inquiry is unavailable; local order states are retained");
            return;
        } catch (RuntimeException e) {
            insertAuditLog("ORDER_STATUS_SYNC_FAILED", null, e.getMessage());
            logger.warn("KIS order status inquiry failed; local order states are retained. message={}", e.getMessage());
            return;
        }

        for (Map<String, Object> localOrder : localOrders) {
            Long orderId = MapUtils.longValue(localOrder, "id");
            String brokerOrderId = MapUtils.string(localOrder, "brokerOrderId");
            if (brokerOrderId == null || brokerOrderId.isBlank()) {
                insertAuditLog("ORDER_STATUS_SYNC_SKIPPED", MapUtils.string(localOrder, "stockCode"),
                        "orderId=" + orderId + ", reason=BROKER_ORDER_ID_MISSING");
                continue;
            }

            Map<String, Object> brokerOrder = findBrokerOrder(brokerOrders, brokerOrderId,
                    MapUtils.string(localOrder, "brokerOrderOrgNo"));
            if (brokerOrder == null) {
                logger.debug("KIS order status not returned; local order retained. orderId={}, brokerOrderId={}",
                        orderId, brokerOrderId);
                continue;
            }

            String brokerStatus = MapUtils.string(brokerOrder, "brokerStatus");
            String orderStatus = localOrderStatus(localOrder, brokerStatus);
            if (orderStatus == null) {
                insertAuditLog("ORDER_STATUS_UNKNOWN", MapUtils.string(localOrder, "stockCode"),
                        "orderId=" + orderId + ", brokerOrderId=" + brokerOrderId);
                continue;
            }

            String checkedAt = now();
            tradingMapper.updateOrderStatusByBrokerOrderId(MapUtils.map(
                    "brokerOrderId", brokerOrderId,
                    "brokerOrderOrgNo", MapUtils.string(brokerOrder, "brokerOrderOrgNo"),
                    "orderStatus", orderStatus,
                    "brokerStatus", brokerStatus,
                    "filledQuantity", MapUtils.decimal(brokerOrder, "filledQuantity").toPlainString(),
                    "remainingQuantity", MapUtils.decimal(brokerOrder, "remainingQuantity").toPlainString(),
                    "errorMessage", MapUtils.string(brokerOrder, "brokerMessage"),
                    "checkedAt", checkedAt
            ));
            insertAuditLog("ORDER_STATUS_SYNCED", MapUtils.string(localOrder, "stockCode"),
                    "orderId=" + orderId + ", brokerOrderId=" + brokerOrderId
                            + ", brokerStatus=" + brokerStatus + ", orderStatus=" + orderStatus);
        }
    }

    private Map<String, Object> findBrokerOrder(List<Map<String, Object>> brokerOrders,
                                                String brokerOrderId,
                                                String brokerOrderOrgNo) {
        if (brokerOrders == null) {
            return null;
        }
        for (Map<String, Object> brokerOrder : brokerOrders) {
            if (!brokerOrderId.equals(MapUtils.string(brokerOrder, "brokerOrderId"))) {
                continue;
            }
            String remoteOrgNo = MapUtils.string(brokerOrder, "brokerOrderOrgNo");
            if (brokerOrderOrgNo == null || brokerOrderOrgNo.isBlank()
                    || remoteOrgNo == null || remoteOrgNo.isBlank()
                    || brokerOrderOrgNo.equals(remoteOrgNo)) {
                return brokerOrder;
            }
        }
        return null;
    }

    private String localOrderStatus(Map<String, Object> localOrder, String brokerStatus) {
        if (brokerStatus == null) {
            return null;
        }
        return switch (brokerStatus.toUpperCase()) {
            case "FILLED" -> "FILLED";
            case "PARTIALLY_FILLED" -> "PARTIALLY_FILLED";
            case "CANCELLED" -> "CANCELLED";
            case "REJECTED" -> "REJECTED";
            case "OPEN" -> "PARTIALLY_FILLED".equalsIgnoreCase(MapUtils.string(localOrder, "orderStatus"))
                    ? "PARTIALLY_FILLED" : "ACCEPTED";
            default -> null;
        };
    }

    private void closePositionMissingFromAccount(Map<String, Object> position, String syncedAt) {
        closePositionFromAccountSync(position, syncedAt, "ACCOUNT_SYNC");
    }

    private void reconcileAccountHolding(Map<String, Object> holding, String syncedAt) {
        String stockCode = MapUtils.string(holding, "stockCode");
        BigDecimal averagePrice = zeroIfNull(MapUtils.decimal(holding, "averagePrice"));
        BigDecimal quantity = MapUtils.decimal(holding, "quantity");
        BigDecimal investedAmount = averagePrice.multiply(quantity);
        Map<String, Object> position = tradingMapper.selectActivePositionByStockCode(MapUtils.map("stockCode", stockCode));
        if (position == null) {
            tradingMapper.insertSyncedPosition(positionMap(
                    null, stockCode, MapUtils.string(holding, "stockName"), TradingStatus.WHITE.name(),
                    averagePrice, quantity, investedAmount, syncedAt));
            Long newPositionId = tradingMapper.selectActivePositionIdByStockCode(MapUtils.map("stockCode", stockCode));
            recordAccountSyncLifecycleEvent(newPositionId, LifecycleEventType.ACCOUNT_SYNC_CREATED, null, TradingStatus.WHITE,
                    holding, averagePrice, quantity, "ACCOUNT_SYNC_CREATED", syncedAt);
            insertAuditLog("ACCOUNT_SYNC_CREATED", stockCode, "created from KIS account holding");
            return;
        }

        BigDecimal beforeQuantity = holdingQuantity(position);
        BigDecimal beforeAveragePrice = purchasePrice(position);
        tradingMapper.updateSyncedPosition(positionMap(
                MapUtils.longValue(position, "id"), stockCode, MapUtils.string(holding, "stockName"),
                MapUtils.string(position, "status"), averagePrice, quantity, investedAmount, syncedAt));
        if (quantity.signum() == 0) {
            closePositionFromAccountSync(position, syncedAt, "ACCOUNT_SYNC");
            return;
        }
        if (beforeQuantity.compareTo(quantity) != 0 || beforeAveragePrice.compareTo(averagePrice) != 0) {
            recordAccountSyncLifecycleEvent(MapUtils.longValue(position, "id"), LifecycleEventType.ACCOUNT_SYNC_UPDATED,
                    tradingStatus(MapUtils.string(position, "status")), tradingStatus(MapUtils.string(position, "status")),
                    holding, averagePrice, quantity, "ACCOUNT_SYNC_UPDATED", syncedAt);
            insertAuditLog("ACCOUNT_SYNC_UPDATED", stockCode,
                    "quantity/averagePrice synchronized from KIS account");
        }
    }

    private void recordAccountSyncLifecycleEvent(Long positionId,
                                                 LifecycleEventType eventType,
                                                 TradingStatus previousState,
                                                 TradingStatus newState,
                                                 Map<String, Object> holding,
                                                 BigDecimal averagePrice,
                                                 BigDecimal quantity,
                                                 String reason,
                                                 String syncedAt) {
        if (positionId == null) {
            return;
        }
        recordLifecycleEvent(MapUtils.map(
                "lifecycleId", positionId,
                "eventType", eventType,
                "previousState", previousState,
                "newState", newState,
                "currentPrice", averagePrice,
                "averageBuyPrice", averagePrice,
                "referencePrice", averagePrice,
                "highestPrice", averagePrice,
                "lowestPrice", averagePrice,
                "holdingQuantity", quantity,
                "returnRate", BigDecimal.ZERO,
                "grayTradingDays", 0,
                "reason", reason,
                "orderId", null,
                "executionId", MapUtils.string(holding, "stockCode"),
                "idempotencyKey", "account-sync:" + MapUtils.string(holding, "stockCode") + ":" + positionId + ":" + eventType.name() + ":" + syncedAt,
                "occurredAt", OffsetDateTime.parse(syncedAt, TIME_FORMATTER)
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

    private void processBlackPositions() {
        String decisionCycleId = "sell-" + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" + UUID.randomUUID();
        for (Map<String, Object> position : tradingMapper.selectActiveBlackPositions()) {
            String stockCode = MapUtils.string(position, "stockCode");
            try {
                if (stockCode == null || stockCode.isBlank()) {
                    continue;
                }
                if (tradingMapper.countOpenSellOrderByStockCode(MapUtils.map("stockCode", stockCode)) > 0) {
                    logger.info("BLACK sell skipped because open sell order exists. stockCode={}", stockCode);
                    continue;
                }
                BigDecimal quantity = holdingQuantity(position);
                if (quantity.signum() <= 0) {
                    closePosition(position, now(), "ZERO_HOLDING_QUANTITY");
                    continue;
                }
                Map<String, Object> currentPrice = brokerClient.getCurrentPrice(stockCode);
                BigDecimal price = MapUtils.decimal(currentPrice, "price");
                if (price.signum() <= 0) {
                    insertAuditLog("SELL_SKIPPED", stockCode, "INVALID_CURRENT_PRICE");
                    logger.info("BLACK sell skipped. stockCode={}, reason=INVALID_CURRENT_PRICE", stockCode);
                    continue;
                }

                String idempotencyKey = maskedAccountNumber() + "|AUTO|SELL|" + stockCode + "|" + MapUtils.longValue(position, "id") + "|" + decisionCycleId;
                Map<String, Object> request = MapUtils.map(
                        "stockCode", stockCode,
                        "orderQuantity", quantity,
                        "orderPrice", orderPrice(currentPrice),
                        "orderAmount", quantity.multiply(price),
                        "reason", "BLACK_SELL",
                        "decisionCycleId", decisionCycleId,
                        "idempotencyKey", idempotencyKey,
                        "instanceId", runtimeProperties.getInstanceId(),
                        "maskedAccount", maskedAccountNumber(),
                        "currentPrice", price,
                        "currentPriceAt", MapUtils.offsetDateTime(currentPrice, "receivedAt"),
                        "exitReason", exitReason(position)
                );
                Map<String, Object> result = orderExecutor.sell(request);
                if (MapUtils.bool(result, "accepted")) {
                    recordLifecycleEvent(snapshotEvent(position, LifecycleEventType.SELL_ORDER_CREATED, TradingStatus.BLACK,
                            TradingStatus.BLACK, price, "BLACK_SELL", idempotencyKey, null));
                    insertAuditLog("SELL_REQUESTED", stockCode,
                            "accepted=true, status=" + MapUtils.string(result, "status") + ", brokerOrderId=" + MapUtils.string(result, "brokerOrderId"));
                    logger.info("BLACK sell requested. stockCode={}, quantity={}, status={}", stockCode, quantity, MapUtils.string(result, "status"));
                }
            } catch (RuntimeException e) {
                insertAuditLog("SELL_FAILED", stockCode, e.getMessage());
                logger.warn("BLACK sell failed; position remains BLACK for next cycle. stockCode={}, message={}", stockCode, e.getMessage());
            }
        }
    }

    private void cancelOpenBuyOrders() {
        List<Map<String, Object>> openBuyOrders = tradingMapper.selectOpenBuyOrders();
        if (openBuyOrders.isEmpty()) {
            return;
        }

        logger.info("cancelling open buy orders before the next buy cycle. count={}", openBuyOrders.size());
        for (Map<String, Object> order : openBuyOrders) {
            Long orderId = MapUtils.longValue(order, "id");
            String stockCode = MapUtils.string(order, "stockCode");
            String brokerOrderId = MapUtils.string(order, "brokerOrderId");
            String orderStatus = MapUtils.string(order, "orderStatus");
            try {
                Map<String, Object> result;
                if ("DRY_RUN".equalsIgnoreCase(orderStatus)) {
                    result = MapUtils.map("accepted", true, "status", "CANCELLED", "message", "local open buy order cancelled");
                } else if (brokerOrderId == null || brokerOrderId.isBlank()) {
                    result = MapUtils.map("accepted", false, "status", "CANCEL_FAILED", "message", "broker order number is missing");
                } else {
                    result = brokerClient.cancel(MapUtils.map(
                            "stockCode", stockCode,
                            "brokerOrderId", brokerOrderId,
                            "brokerOrderOrgNo", MapUtils.string(order, "brokerOrderOrgNo"),
                            "orderQuantity", MapUtils.decimal(order, "orderQuantity"),
                            "orderPrice", MapUtils.decimal(order, "orderPrice")
                    ));
                }

                if (MapUtils.bool(result, "accepted")) {
                    String updatedAt = now();
                    if ("DRY_RUN".equalsIgnoreCase(orderStatus)) {
                        tradingMapper.updateOrderStatusById(MapUtils.map(
                                "orderId", orderId,
                                "orderStatus", "CANCELLED",
                                "errorMessage", "DRY_RUN_BUY_ORDER_CANCELLED",
                                "updatedAt", updatedAt
                        ));
                        insertAuditLog("BUY_ORDER_CANCELLED", stockCode, "orderId=" + orderId + ", dryRun=true");
                    } else {
                        tradingMapper.updateOrderBrokerStatusById(MapUtils.map(
                                "orderId", orderId,
                                "brokerStatus", "CANCEL_REQUESTED",
                                "errorMessage", "BUY_ORDER_CANCEL_REQUESTED",
                                "updatedAt", updatedAt
                        ));
                        insertAuditLog("BUY_ORDER_CANCEL_REQUESTED", stockCode,
                                "orderId=" + orderId + ", brokerOrderId=" + brokerOrderId);
                        logger.info("open buy order cancellation requested; final state awaits KIS inquiry. stockCode={}, orderId={}, previousStatus={}",
                                stockCode, orderId, orderStatus);
                    }
                } else {
                    insertAuditLog("BUY_ORDER_CANCEL_FAILED", stockCode,
                            "orderId=" + orderId + ", message=" + MapUtils.string(result, "message"));
                    logger.warn("open buy order cancellation failed; slot remains reserved. stockCode={}, orderId={}, message={}",
                            stockCode, orderId, MapUtils.string(result, "message"));
                }
            } catch (RuntimeException e) {
                insertAuditLog("BUY_ORDER_CANCEL_FAILED", stockCode,
                        "orderId=" + orderId + ", message=" + e.getMessage());
                logger.warn("open buy order cancellation failed; slot remains reserved. stockCode={}, orderId={}, message={}",
                        stockCode, orderId, e.getMessage());
            }
        }
    }

    private void cancelOpenSellOrders() {
        List<Map<String, Object>> openSellOrders = tradingMapper.selectOpenSellOrders();
        if (openSellOrders.isEmpty()) {
            return;
        }

        logger.info("cancelling open sell orders before BLACK processing. count={}", openSellOrders.size());
        for (Map<String, Object> order : openSellOrders) {
            Long orderId = MapUtils.longValue(order, "id");
            String stockCode = MapUtils.string(order, "stockCode");
            String brokerOrderId = MapUtils.string(order, "brokerOrderId");
            String orderStatus = MapUtils.string(order, "orderStatus");
            try {
                Map<String, Object> result;
                if ("DRY_RUN".equalsIgnoreCase(orderStatus)) {
                    result = MapUtils.map("accepted", true, "status", "CANCELLED", "message", "local open sell order cancelled");
                } else if (brokerOrderId == null || brokerOrderId.isBlank()) {
                    result = MapUtils.map("accepted", false, "status", "CANCEL_FAILED", "message", "broker order number is missing");
                } else {
                    result = brokerClient.cancel(MapUtils.map(
                            "stockCode", stockCode,
                            "brokerOrderId", brokerOrderId,
                            "brokerOrderOrgNo", MapUtils.string(order, "brokerOrderOrgNo"),
                            "orderQuantity", MapUtils.decimal(order, "orderQuantity"),
                            "orderPrice", MapUtils.decimal(order, "orderPrice")
                    ));
                }

                if (MapUtils.bool(result, "accepted")) {
                    String updatedAt = now();
                    if ("DRY_RUN".equalsIgnoreCase(orderStatus)) {
                        tradingMapper.updateOrderStatusById(MapUtils.map(
                                "orderId", orderId,
                                "orderStatus", "CANCELLED",
                                "errorMessage", "DRY_RUN_SELL_ORDER_CANCELLED",
                                "updatedAt", updatedAt
                        ));
                        insertAuditLog("SELL_ORDER_CANCELLED", stockCode, "orderId=" + orderId + ", dryRun=true");
                    } else {
                        tradingMapper.updateOrderBrokerStatusById(MapUtils.map(
                                "orderId", orderId,
                                "brokerStatus", "CANCEL_REQUESTED",
                                "errorMessage", "SELL_ORDER_CANCEL_REQUESTED_BEFORE_BLACK_RETRY",
                                "updatedAt", updatedAt
                        ));
                        insertAuditLog("SELL_ORDER_CANCEL_REQUESTED", stockCode,
                                "orderId=" + orderId + ", previousStatus=" + orderStatus);
                        logger.info("open sell order cancellation requested; final state awaits KIS inquiry. stockCode={}, orderId={}, previousStatus={}",
                                stockCode, orderId, orderStatus);
                    }
                } else {
                    insertAuditLog("SELL_ORDER_CANCEL_FAILED", stockCode,
                            "orderId=" + orderId + ", message=" + MapUtils.string(result, "message"));
                    logger.warn("open sell order cancellation failed; BLACK sell remains reserved. stockCode={}, orderId={}, message={}",
                            stockCode, orderId, MapUtils.string(result, "message"));
                }
            } catch (RuntimeException e) {
                insertAuditLog("SELL_ORDER_CANCEL_FAILED", stockCode,
                        "orderId=" + orderId + ", message=" + e.getMessage());
                logger.warn("open sell order cancellation failed; BLACK sell remains reserved. stockCode={}, orderId={}, message={}",
                        stockCode, orderId, e.getMessage());
            }
        }
    }

    private void evaluateActivePositions() {
        for (Map<String, Object> position : tradingMapper.selectActivePositions()) {
            String status = MapUtils.string(position, "status");
            if (TradingStatus.BLACK.name().equals(status) || TradingStatus.CLOSED.name().equals(status)) {
                continue;
            }
            String stockCode = MapUtils.string(position, "stockCode");
            if (stockCode == null || stockCode.isBlank()) {
                continue;
            }
            Map<String, Object> currentPrice = brokerClient.getCurrentPrice(stockCode);
            BigDecimal price = MapUtils.decimal(currentPrice, "price");
            if (price.signum() <= 0) {
                insertAuditLog("STRATEGY_EVALUATION_SKIPPED", stockCode, "INVALID_CURRENT_PRICE");
                continue;
            }
            evaluatePosition(position, price);
        }
    }

    private void evaluatePosition(Map<String, Object> position, BigDecimal currentPrice) {
        TradingStatus previousStatus = tradingStatus(MapUtils.string(position, "status"));
        BigDecimal previousPrice = previousPrice(position);
        BigDecimal purchasePrice = purchasePrice(position);
        BigDecimal quantity = holdingQuantity(position);
        BigDecimal valuationAmount = currentPrice.multiply(quantity);
        BigDecimal returnRate = rate(currentPrice, purchasePrice);
        String evaluatedAt = now();
        LocalDate today = OffsetDateTime.parse(evaluatedAt, TIME_FORMATTER).toLocalDate();

        TradingStatus nextStatus = previousStatus;
        String reason = "STATUS_UNCHANGED";
        String grayEnteredDate = MapUtils.string(position, "grayEnteredDate");
        int grayTradingDays = MapUtils.integer(position, "grayTradingDays");
        String flatStartedDate = MapUtils.string(position, "flatStartedDate");
        BigDecimal statusReferencePrice = zeroIfNull(MapUtils.decimal(position, "statusReferencePrice"));
        BigDecimal referencePrice = zeroIfNull(MapUtils.decimal(position, "referencePrice"));

        int comparison = currentPrice.compareTo(previousPrice);
        if (previousStatus == TradingStatus.WHITE) {
            String exitReason = exitTriggerReason(returnRate);
            if (exitReason != null) {
                nextStatus = TradingStatus.BLACK;
                reason = exitReason;
                flatStartedDate = null;
            } else if (comparison < 0) {
                nextStatus = TradingStatus.GRAY;
                reason = "PRICE_DECLINE";
                grayEnteredDate = today.toString();
                grayTradingDays = 0;
                flatStartedDate = null;
                statusReferencePrice = previousPrice;
                referencePrice = previousPrice;
            } else if (comparison == 0) {
                if (flatStartedDate == null || flatStartedDate.isBlank()) {
                    flatStartedDate = today.toString();
                }
                long flatTradingDays = tradingDayService.countTradingDays(LocalDate.parse(flatStartedDate), today);
                if (isGraceExpired(flatTradingDays, investmentProperties.getWhiteFlatGraceTradingDays())) {
                    nextStatus = TradingStatus.GRAY;
                    reason = "WHITE_FLAT_TIMEOUT";
                    grayEnteredDate = today.toString();
                    grayTradingDays = 0;
                    statusReferencePrice = previousPrice;
                    referencePrice = previousPrice;
                    flatStartedDate = null;
                }
            } else {
                flatStartedDate = null;
                statusReferencePrice = currentPrice;
                referencePrice = currentPrice;
            }
        } else if (previousStatus == TradingStatus.GRAY) {
            if (grayEnteredDate == null || grayEnteredDate.isBlank()) {
                grayEnteredDate = today.toString();
            }
            long countedDays = tradingDayService.countTradingDays(LocalDate.parse(grayEnteredDate), today);
            if (countedDays > grayTradingDays) {
                grayTradingDays = Math.toIntExact(countedDays);
                Map<String, Object> grayDayEvent = snapshotEvent(position, LifecycleEventType.GRAY_DAY_COUNTED, TradingStatus.GRAY,
                        TradingStatus.GRAY, currentPrice, "GRAY_DAY_" + grayTradingDays,
                        "gray-day:" + MapUtils.longValue(position, "id") + ":" + grayTradingDays, null);
                grayDayEvent.put("grayTradingDays", grayTradingDays);
                recordLifecycleEvent(grayDayEvent);
            }

            String exitReason = exitTriggerReason(returnRate);
            if (exitReason != null) {
                nextStatus = TradingStatus.BLACK;
                reason = exitReason;
            } else if (currentPrice.compareTo(purchasePrice) >= 0) {
                nextStatus = TradingStatus.WHITE;
                reason = "PRICE_RECOVERED";
                grayEnteredDate = null;
                grayTradingDays = 0;
                flatStartedDate = null;
                statusReferencePrice = currentPrice;
                referencePrice = currentPrice;
            } else if (isGraceExpired(grayTradingDays, investmentProperties.getGrayGraceTradingDays())) {
                nextStatus = TradingStatus.BLACK;
                reason = ExitReason.GRAY_TIMEOUT.name();
            }
        }

        BigDecimal highestPrice = max(zeroIfNull(MapUtils.decimal(position, "highestPrice")), currentPrice);
        BigDecimal lowestPrice = min(nonZeroOr(MapUtils.decimal(position, "lowestPrice"), currentPrice), currentPrice);
        tradingMapper.updatePositionAfterEvaluation(MapUtils.map(
                "positionId", MapUtils.longValue(position, "id"),
                "status", nextStatus.name(),
                "currentPrice", plain(currentPrice),
                "currentValuationAmount", plain(valuationAmount),
                "profitRate", plain(returnRate),
                "lastEvaluatedPrice", plain(currentPrice),
                "statusReferencePrice", plain(statusReferencePrice),
                "grayEnteredDate", grayEnteredDate,
                "grayTradingDays", grayTradingDays,
                "averageBuyPrice", plain(purchasePrice),
                "referencePrice", plain(referencePrice),
                "highestPrice", plain(highestPrice),
                "lowestPrice", plain(lowestPrice),
                "holdingQuantity", plain(quantity),
                "returnRate", plain(returnRate),
                "lastEvaluatedAt", evaluatedAt,
                "flatStartedDate", flatStartedDate,
                "updatedAt", evaluatedAt
        ));

        if (nextStatus != previousStatus) {
            LifecycleEventType eventType = transitionEvent(previousStatus, nextStatus, reason);
            Map<String, Object> stateEvent = snapshotEvent(position, eventType, previousStatus, nextStatus, currentPrice, reason,
                    "state:" + MapUtils.longValue(position, "id") + ":" + previousStatus + ":" + nextStatus + ":" + evaluatedAt,
                    null);
            stateEvent.put("grayTradingDays", grayTradingDays);
            stateEvent.put("referencePrice", referencePrice);
            recordLifecycleEvent(stateEvent);
            insertAuditLog("STATUS_CHANGED", MapUtils.string(position, "stockCode"),
                    previousStatus + " -> " + nextStatus + ", reason=" + reason + ", previousPrice=" + previousPrice + ", currentPrice=" + currentPrice);
            logger.info("[STATUS] stockCode={}, {} -> {}, reason={}, previousPrice={}, currentPrice={}",
                    MapUtils.string(position, "stockCode"), previousStatus, nextStatus, reason, previousPrice, currentPrice);
        }
    }

    private LifecycleEventType transitionEvent(TradingStatus previousStatus, TradingStatus nextStatus, String reason) {
        if (previousStatus == TradingStatus.WHITE && nextStatus == TradingStatus.GRAY) {
            return LifecycleEventType.WHITE_TO_GRAY;
        }
        if (previousStatus == TradingStatus.GRAY && nextStatus == TradingStatus.WHITE) {
            return LifecycleEventType.WHITE_RECOVERED;
        }
        if (nextStatus == TradingStatus.BLACK && ExitReason.TAKE_PROFIT.name().equals(reason)) {
            return LifecycleEventType.TAKE_PROFIT_TRIGGERED;
        }
        if (nextStatus == TradingStatus.BLACK && ExitReason.STOP_LOSS.name().equals(reason)) {
            return LifecycleEventType.STOP_LOSS_TRIGGERED;
        }
        if (nextStatus == TradingStatus.BLACK) {
            return LifecycleEventType.BLACK_ENTERED;
        }
        return LifecycleEventType.STRATEGY_ERROR;
    }

    private Map<String, Object> snapshotEvent(Map<String, Object> position,
                                              LifecycleEventType eventType,
                                              TradingStatus previousStatus,
                                              TradingStatus newStatus,
                                              BigDecimal currentPrice,
                                              String reason,
                                              String idempotencyKey,
                                              Object orderId) {
        BigDecimal price = zeroIfNull(currentPrice);
        BigDecimal averageBuyPrice = purchasePrice(position);
        return MapUtils.map(
                "lifecycleId", MapUtils.longValue(position, "id"),
                "eventType", eventType,
                "previousState", previousStatus,
                "newState", newStatus,
                "currentPrice", price,
                "averageBuyPrice", averageBuyPrice,
                "referencePrice", zeroIfNull(MapUtils.decimal(position, "referencePrice")),
                "highestPrice", max(zeroIfNull(MapUtils.decimal(position, "highestPrice")), price),
                "lowestPrice", min(nonZeroOr(MapUtils.decimal(position, "lowestPrice"), price), price),
                "holdingQuantity", holdingQuantity(position),
                "returnRate", rate(price, averageBuyPrice),
                "grayTradingDays", MapUtils.integer(position, "grayTradingDays"),
                "reason", reason,
                "orderId", orderId,
                "executionId", MapUtils.string(position, "stockCode"),
                "idempotencyKey", idempotencyKey,
                "occurredAt", OffsetDateTime.now()
        );
    }

    private void closePosition(Map<String, Object> position, String closedAt, String reason) {
        BigDecimal currentPrice = zeroIfNull(MapUtils.decimal(position, "currentPrice"));
        BigDecimal quantity = holdingQuantity(position);
        BigDecimal purchasePrice = purchasePrice(position);
        BigDecimal valuationAmount = currentPrice.multiply(quantity);
        BigDecimal returnRate = rate(currentPrice, purchasePrice);
        tradingMapper.closePosition(MapUtils.map(
                "positionId", MapUtils.longValue(position, "id"),
                "currentPrice", plain(currentPrice),
                "currentValuationAmount", plain(valuationAmount),
                "profitRate", plain(returnRate),
                "returnRate", plain(returnRate),
                "closedAt", closedAt
        ));
        TradingStatus previousStatus = tradingStatus(MapUtils.string(position, "status"));
        recordLifecycleEvent(snapshotEvent(position, LifecycleEventType.SELL_FILLED, previousStatus, previousStatus,
                currentPrice, reason, "sell-filled:" + MapUtils.longValue(position, "id") + ":" + closedAt, null));
        recordLifecycleEvent(snapshotEvent(position, LifecycleEventType.CLOSED, previousStatus, TradingStatus.CLOSED,
                currentPrice, reason, "closed:" + MapUtils.longValue(position, "id") + ":" + closedAt, null));
        insertAuditLog("POSITION_CLOSED", MapUtils.string(position, "stockCode"), reason);
        logger.info("position closed. stockCode={}, reason={}", MapUtils.string(position, "stockCode"), reason);
    }

    private void closePositionFromAccountSync(Map<String, Object> position, String closedAt, String reason) {
        BigDecimal currentPrice = zeroIfNull(MapUtils.decimal(position, "currentPrice"));
        BigDecimal quantity = holdingQuantity(position);
        BigDecimal purchasePrice = purchasePrice(position);
        BigDecimal valuationAmount = currentPrice.multiply(quantity);
        BigDecimal returnRate = rate(currentPrice, purchasePrice);
        tradingMapper.closePosition(MapUtils.map(
                "positionId", MapUtils.longValue(position, "id"),
                "currentPrice", plain(currentPrice),
                "currentValuationAmount", plain(valuationAmount),
                "profitRate", plain(returnRate),
                "returnRate", plain(returnRate),
                "closedAt", closedAt
        ));
        TradingStatus previousStatus = tradingStatus(MapUtils.string(position, "status"));
        recordLifecycleEvent(snapshotEvent(position, LifecycleEventType.ACCOUNT_SYNC_CLOSED, previousStatus, TradingStatus.CLOSED,
                currentPrice, reason, "account-sync-closed:" + MapUtils.longValue(position, "id") + ":" + closedAt, null));
        insertAuditLog("ACCOUNT_SYNC_CLOSED", MapUtils.string(position, "stockCode"), reason);
        logger.info("position closed by startup account sync. stockCode={}, reason={}", MapUtils.string(position, "stockCode"), reason);
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
        runLocked(schedulerType, schedulerType, task);
    }

    private void runLocked(String schedulerType, String lockType, Runnable task) {
        String startedAt = now();
        if (!schedulerLockService.tryLock(lockType)) {
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
            schedulerLockService.unlock(lockType);
        }
    }

    private void runBuyPipeline() {
        String decisionCycleId = "cycle-" + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" + UUID.randomUUID();
        if (isOverseasMarket()) {
            List<Map<String, Object>> candidates = overseasStockCandidateService.findOrderTargetsForCycle();
            if (candidates.isEmpty()) {
                logger.info("buy pipeline skipped because overseas candidate was not selected");
                return;
            }
            for (Map<String, Object> candidate : candidates) {
                tryBuyCandidate(candidate, decisionCycleId);
            }
            return;
        }

        List<Map<String, Object>> candidates = domesticStockCandidateService.findOrderTargetsForCycle();
        if (candidates.isEmpty()) {
            logger.info("buy pipeline skipped because domestic candidate was not selected");
            return;
        }
        for (Map<String, Object> candidate : candidates) {
            tryBuyCandidate(candidate, decisionCycleId);
        }
    }

    private void tryBuyCandidate(Map<String, Object> candidate, String decisionCycleId) {
            String normalizedStockCode = MapUtils.string(candidate, "symbol");
            boolean validationOrder = MapUtils.bool(candidate, "validationOrder");
            String marketCode = MapUtils.bool(candidate, "overseas") ? MapUtils.string(candidate, "exchangeCode") : MapUtils.string(candidate, "marketCode");
            String orderPurpose = validationOrder ? "FRACTIONAL_VALIDATION" : "AUTO";
            String orderReason = validationOrder ? "FRACTIONAL_VALIDATION" : "AUTO_BUY";
            String idempotencyKey = maskedAccountNumber() + "|" + orderPurpose + "|BUY|" + normalizedStockCode + "|" + decisionCycleId;
            Map<String, Object> currentPrice = brokerClient.getCurrentPrice(normalizedStockCode);
            Map<String, Object> buyableBalance = isOverseasMarket()
                    ? brokerClient.getBuyableBalance(normalizedStockCode, MapUtils.decimal(currentPrice, "price"))
                    : brokerClient.getAccountBalance();
            Map<String, Object> sizingResult = orderSizingService.calculateBuyQuantity(currentPrice, buyableBalance, BigDecimal.ZERO);
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

    private TradingStatus tradingStatus(String status) {
        if (status == null || status.isBlank()) {
            return TradingStatus.WHITE;
        }
        return TradingStatus.valueOf(status);
    }

    private BigDecimal previousPrice(Map<String, Object> position) {
        BigDecimal previousPrice = MapUtils.decimal(position, "lastEvaluatedPrice");
        if (previousPrice.signum() > 0) {
            return previousPrice;
        }
        return purchasePrice(position);
    }

    private BigDecimal purchasePrice(Map<String, Object> position) {
        BigDecimal averageBuyPrice = MapUtils.decimal(position, "averageBuyPrice");
        if (averageBuyPrice.signum() > 0) {
            return averageBuyPrice;
        }
        return MapUtils.decimal(position, "purchasePrice");
    }

    private BigDecimal holdingQuantity(Map<String, Object> position) {
        BigDecimal quantity = MapUtils.decimal(position, "holdingQuantity");
        if (quantity.signum() > 0) {
            return quantity;
        }
        return MapUtils.decimal(position, "purchaseQuantity");
    }

    private BigDecimal rate(BigDecimal currentPrice, BigDecimal purchasePrice) {
        if (currentPrice == null || purchasePrice == null || purchasePrice.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return currentPrice.subtract(purchasePrice).divide(purchasePrice, 8, RoundingMode.HALF_UP);
    }

    private String exitTriggerReason(BigDecimal returnRate) {
        if (returnRate.compareTo(investmentProperties.getTakeProfitRate()) >= 0) {
            return ExitReason.TAKE_PROFIT.name();
        }
        if (returnRate.compareTo(investmentProperties.getStopLoss().getRate()) <= 0) {
            return ExitReason.STOP_LOSS.name();
        }
        return null;
    }

    private BigDecimal max(BigDecimal first, BigDecimal second) {
        if (first == null) {
            return zeroIfNull(second);
        }
        if (second == null) {
            return first;
        }
        return first.max(second);
    }

    private BigDecimal min(BigDecimal first, BigDecimal second) {
        if (first == null) {
            return zeroIfNull(second);
        }
        if (second == null) {
            return first;
        }
        return first.min(second);
    }

    private BigDecimal nonZeroOr(BigDecimal value, BigDecimal fallback) {
        return value == null || value.signum() <= 0 ? zeroIfNull(fallback) : value;
    }

    private String exitReason(Map<String, Object> position) {
        String triggeredReason = exitTriggerReason(MapUtils.decimal(position, "returnRate"));
        if (triggeredReason != null) {
            return triggeredReason;
        }
        if (isGraceExpired(MapUtils.integer(position, "grayTradingDays"), investmentProperties.getGrayGraceTradingDays())) {
            return ExitReason.GRAY_TIMEOUT.name();
        }
        return ExitReason.GRAY_DECLINE.name();
    }

    private boolean isGraceExpired(long tradingDays, int graceTradingDays) {
        return graceTradingDays <= 0 || tradingDays >= graceTradingDays;
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
