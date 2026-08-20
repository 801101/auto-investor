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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PilotService {

    private static final int MAX_BUY_ATTEMPTS_PER_CYCLE = 20;

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
                logger.debug("trading cycle skipped because runtime.trading-enabled=false");
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
                logger.debug("maintenance state evaluation skipped because runtime.trading-enabled=false");
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

    public Map<String, Object> getPositionDetails() {
        List<Map<String, Object>> positions = tradingMapper.selectDashboardPositions();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("positions", positions);
        response.put("maxHoldings", investmentProperties.getMaxHoldings());
        response.put("maxHoldingsPerStock", investmentProperties.getMaxHoldingsPerStock());
        return response;
    }

    public Map<String, Object> getAccount() {
        Map<String, Object> balance = brokerClient.getAccountBalance();
        persistAccountBalance(balance);
        return Map.of(
                "cashBalance", MapUtils.decimal(balance, "cashBalance"),
                "totalValuationAmount", MapUtils.decimal(balance, "totalValuationAmount"),
                "holdings", brokerClient.getHoldings()
        );
    }

    public Map<String, Object> getAccountBalanceSnapshot() {
        Map<String, Object> balance = tradingMapper.selectAccountBalance();
        if (balance == null || balance.isEmpty()) {
            return Map.of("available", false);
        }
        return balance;
    }

    public Map<String, Object> getOrderSuccessSummary24h() {
        return tradingMapper.selectOrderSuccessSummary24h();
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
            Map<String, Object> balance = brokerClient.getAccountBalance();
            persistAccountBalance(balance);
            List<Map<String, Object>> holdings = brokerClient.getHoldings();
            Map<String, Object> orderSync = synchronizeOpenOrders();
            reconcileAccountHoldings(holdings, orderSync);
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
            Map<String, Object> balance = brokerClient.getAccountBalance();
            persistAccountBalance(balance);
            List<Map<String, Object>> holdings = brokerClient.getHoldings();
            Map<String, Object> orderSync = synchronizeOpenOrders();
            reconcileAccountHoldings(holdings, orderSync);
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

    private void persistAccountBalance(Map<String, Object> balance) {
        tradingMapper.upsertAccountBalance(MapUtils.map(
                "cashBalance", MapUtils.decimal(balance, "cashBalance"),
                "totalValuationAmount", MapUtils.decimal(balance, "totalValuationAmount"),
                "currencyCode", isOverseasMarket() ? investmentProperties.getOverseasCurrencyCode() : "KRW",
                "source", "KIS_ACCOUNT_SYNC",
                "updatedAt", OffsetDateTime.now().toString()
        ));
    }

    private void reconcileAccountHoldings(List<Map<String, Object>> holdings, Map<String, Object> orderSync) {
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
                reconcileAccountHolding(holding, syncedAt, orderSync);
            }
        }

        for (Map<String, Object> position : tradingMapper.selectActivePositions()) {
            if (!investmentProperties.getMarketType().equalsIgnoreCase(positionMarketType(position))) {
                continue;
            }
            String stockCode = MapUtils.string(position, "stockCode");
            if (stockCode == null || accountHoldingCodes.contains(stockCode)) {
                continue;
            }
            closePositionMissingFromAccount(position, syncedAt, orderSync);
        }
    }

    private Map<String, Object> synchronizeOpenOrders() {
        Map<String, Object> result = new LinkedHashMap<>();
        Set<String> pendingSellBrokerOrderIds = new HashSet<>();
        result.put("successful", false);
        result.put("pendingSellBrokerOrderIds", pendingSellBrokerOrderIds);
        List<Map<String, Object>> localOrders = tradingMapper.selectOrdersForStatusSync();
        if (localOrders.isEmpty()) {
            result.put("successful", true);
            return result;
        }

        List<Map<String, Object>> brokerOrders;
        try {
            brokerOrders = brokerClient.getOrderStatuses(Map.of());
        } catch (UnsupportedOperationException e) {
            insertAuditLog("ORDER_STATUS_SYNC_UNAVAILABLE", null, e.getMessage());
            logger.warn("KIS order status inquiry is unavailable; local order states are retained");
            return result;
        } catch (RuntimeException e) {
            insertAuditLog("ORDER_STATUS_SYNC_FAILED", null, e.getMessage());
            logger.warn("KIS order status inquiry failed; local order states are retained. message={}", e.getMessage());
            return result;
        }

        result.put("successful", true);
        for (Map<String, Object> brokerOrder : brokerOrders) {
            if ("SELL".equalsIgnoreCase(MapUtils.string(brokerOrder, "orderType"))
                    && isPendingBrokerOrder(MapUtils.string(brokerOrder, "brokerStatus"))) {
                String brokerOrderId = MapUtils.string(brokerOrder, "brokerOrderId");
                if (brokerOrderId != null && !brokerOrderId.isBlank()) {
                    pendingSellBrokerOrderIds.add(brokerOrderId);
                }
            }
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
                if ("SELL".equalsIgnoreCase(MapUtils.string(localOrder, "orderType"))) {
                    String checkedAt = now();
                    tradingMapper.markSellOrderFilledByKisOrderMissing(MapUtils.map(
                            "orderId", orderId,
                            "checkedAt", checkedAt,
                            "errorMessage", "KIS_ORDER_NOT_FOUND_LIFECYCLE_FILLED"
                    ));
                    insertAuditLog("SELL_ORDER_FILLED", MapUtils.string(localOrder, "stockCode"),
                            "orderId=" + orderId + ", brokerOrderId=" + brokerOrderId
                                    + ", reason=KIS_ORDER_NOT_FOUND_LIFECYCLE_FILLED");
                    logger.info("SELL lifecycle completed because KIS order was not found. stockCode={}, orderId={}",
                            MapUtils.string(localOrder, "stockCode"), orderId);
                } else {
                    logger.debug("KIS BUY order status not returned; local order retained. orderId={}, brokerOrderId={}",
                            orderId, brokerOrderId);
                }
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
                    "filledPrice", MapUtils.decimal(brokerOrder, "filledPrice").signum() > 0
                            ? MapUtils.decimal(brokerOrder, "filledPrice").toPlainString() : null,
                    "remainingQuantity", MapUtils.decimal(brokerOrder, "remainingQuantity").toPlainString(),
                    "errorMessage", MapUtils.string(brokerOrder, "brokerMessage"),
                    "checkedAt", checkedAt
            ));
            insertAuditLog("ORDER_STATUS_SYNCED", MapUtils.string(localOrder, "stockCode"),
                    "orderId=" + orderId + ", brokerOrderId=" + brokerOrderId
                            + ", brokerStatus=" + brokerStatus + ", orderStatus=" + orderStatus);
        }
        return result;
    }

    private Map<String, Object> findBrokerOrder(List<Map<String, Object>> brokerOrders,
                                                String brokerOrderId,
                                                String brokerOrderOrgNo) {
        if (brokerOrders == null) {
            return null;
        }
        for (Map<String, Object> brokerOrder : brokerOrders) {
            if (!sameBrokerOrderId(brokerOrderId, MapUtils.string(brokerOrder, "brokerOrderId"))) {
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

    private boolean sameBrokerOrderId(String localOrderId, String brokerOrderId) {
        if (localOrderId == null || brokerOrderId == null) {
            return false;
        }
        String local = localOrderId.trim();
        String remote = brokerOrderId.trim();
        if (local.equals(remote)) {
            return true;
        }
        if (!local.matches("\\d+") || !remote.matches("\\d+")) {
            return false;
        }
        return local.replaceFirst("^0+(?!$)", "")
                .equals(remote.replaceFirst("^0+(?!$)", ""));
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

    private void closePositionMissingFromAccount(Map<String, Object> position,
                                                 String syncedAt,
                                                 Map<String, Object> orderSync) {
        closePositionFromAccountSync(position, syncedAt, "ACCOUNT_SYNC", orderSync);
    }

    private void reconcileAccountHolding(Map<String, Object> holding,
                                         String syncedAt,
                                         Map<String, Object> orderSync) {
        String stockCode = MapUtils.string(holding, "stockCode");
        BigDecimal averagePrice = zeroIfNull(MapUtils.decimal(holding, "averagePrice"));
        BigDecimal quantity = MapUtils.decimal(holding, "quantity");
        String marketType = investmentProperties.getMarketType();
        List<Map<String, Object>> positions = tradingMapper.selectActivePositionsByStockCode(MapUtils.map(
                "stockCode", stockCode,
                "marketType", marketType
        ));
        if (positions.isEmpty()) {
            createAccountSyncPosition(holding, averagePrice, quantity, syncedAt);
            return;
        }

        BigDecimal localQuantity = positions.stream()
                .map(this::holdingQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (quantity.compareTo(localQuantity) > 0) {
            BigDecimal externalQuantity = quantity.subtract(localQuantity);
            createAccountSyncPosition(holding, averagePrice, externalQuantity, syncedAt);
            insertAuditLog("ACCOUNT_SYNC_UPDATED", stockCode,
                    "created separate account-sync position for quantity not linked to a local buy order");
        } else if (quantity.compareTo(localQuantity) < 0) {
            reducePositionsToAccountQuantity(positions, quantity, syncedAt, orderSync);
            insertAuditLog("ACCOUNT_SYNC_UPDATED", stockCode,
                    "reduced local positions to KIS holding quantity");
        }
    }

    private void createAccountSyncPosition(Map<String, Object> holding,
                                           BigDecimal averagePrice,
                                           BigDecimal quantity,
                                           String syncedAt) {
        if (quantity.signum() <= 0) {
            return;
        }
        String stockCode = MapUtils.string(holding, "stockCode");
        String accountSyncSource = MapUtils.string(holding, "accountSyncSource");
        if (accountSyncSource == null || accountSyncSource.isBlank()) {
            accountSyncSource = "KIS_HOLDING";
        }
        BigDecimal investedAmount = averagePrice.multiply(quantity);
        tradingMapper.insertSyncedPosition(positionMap(
                null, stockCode, MapUtils.string(holding, "stockName"), TradingStatus.WHITE.name(),
                averagePrice, quantity, investedAmount, syncedAt, accountSyncSource));
        Map<String, Object> position = tradingMapper.selectLatestActivePositionByStockCode(MapUtils.map(
                "stockCode", stockCode,
                "marketType", investmentProperties.getMarketType()
        ));
        Long positionId = position == null ? null : MapUtils.longValue(position, "id");
        String lifecycleKey = lifecycleKey(positionId);
        if (positionId != null) {
            tradingMapper.updatePositionLifecycleKey(MapUtils.map(
                    "positionId", positionId,
                    "lifecycleKey", lifecycleKey,
                    "updatedAt", syncedAt
            ));
        }
        Long lifecycleOrderId = null;
        if (!"KIS_HOLDING".equals(accountSyncSource)) {
            lifecycleOrderId = insertAccountSyncFallbackOrder(
                    positionId, lifecycleKey, stockCode, averagePrice, quantity, syncedAt, accountSyncSource);
        } else if (positionId != null) {
            Map<String, Object> acceptedBuyOrder = tradingMapper.selectUnlinkedAcceptedBuyOrderByStockCode(
                    MapUtils.map("stockCode", stockCode));
            if (acceptedBuyOrder != null) {
                lifecycleOrderId = MapUtils.longValue(acceptedBuyOrder, "id");
                tradingMapper.markOrderFilledByAccountSync(MapUtils.map(
                        "orderId", lifecycleOrderId,
                        "filledQuantity", plain(quantity),
                        "filledPrice", plain(averagePrice),
                        "checkedAt", syncedAt,
                        "errorMessage", "ACCOUNT_SYNC_BUY_CONFIRMED"
                ));
                tradingMapper.updateOrderPositionId(MapUtils.map(
                        "orderId", lifecycleOrderId,
                        "positionId", positionId,
                        "lifecycleKey", lifecycleKey,
                        "updatedAt", syncedAt
                ));
                String brokerOrderId = MapUtils.string(acceptedBuyOrder, "brokerOrderId");
                if (brokerOrderId != null && !brokerOrderId.isBlank()) {
                    tradingMapper.updatePositionBrokerOrderId(MapUtils.map(
                            "positionId", positionId,
                            "brokerOrderId", brokerOrderId,
                            "updatedAt", syncedAt
                    ));
                }
                insertAuditLog("ACCOUNT_SYNC_ORDER_LINKED", stockCode,
                        "accepted BUY order linked to KIS holding; orderId=" + lifecycleOrderId);
            }
        }
        recordAccountSyncLifecycleEvent(positionId, LifecycleEventType.ACCOUNT_SYNC_CREATED, null, TradingStatus.WHITE,
                holding, averagePrice, quantity, "ACCOUNT_SYNC_CREATED:" + accountSyncSource, syncedAt, lifecycleOrderId,
                lifecycleKey);
        insertAuditLog("ACCOUNT_SYNC_CREATED", stockCode, "created from KIS account holding; source=" + accountSyncSource);
        if (!"KIS_HOLDING".equals(accountSyncSource)) {
            insertAuditLog("ACCOUNT_SYNC_FALLBACK", stockCode,
                    "recovery marker recorded; source=" + accountSyncSource + ", lifecycleKey=" + lifecycleKey);
        }
    }

    private Long insertAccountSyncFallbackOrder(Long positionId,
                                                String lifecycleKey,
                                                String stockCode,
                                                BigDecimal averagePrice,
                                                BigDecimal quantity,
                                                String syncedAt,
                                                String accountSyncSource) {
        String idempotencyKey = "account-sync-fallback:" + lifecycleKey;
        tradingMapper.insertOrderRecordDetailed(MapUtils.map(
                "brokerOrderId", null,
                "positionId", positionId,
                "stockCode", stockCode,
                "orderType", "BUY",
                "orderQuantity", plain(quantity),
                "orderPrice", plain(averagePrice),
                "orderAmount", plain(averagePrice.multiply(quantity)),
                "orderStatus", "ACCOUNT_SYNC_FALLBACK",
                "errorMessage", "No broker order; KIS holding recovery provenance marker",
                "requestedAt", syncedAt,
                "idempotencyKey", idempotencyKey,
                "decisionCycleId", "account-sync:" + syncedAt,
                "instanceId", runtimeProperties.getInstanceId(),
                "maskedAccount", maskedAccountNumber(),
                "skipReason", "ACCOUNT_SYNC_FALLBACK",
                "exitReason", null,
                "dryRun", "N",
                "currentPrice", plain(averagePrice),
                "currentPriceAt", syncedAt,
                "candidateRank", null,
                "tradingValueScore", null,
                "volumeScore", null,
                "volatilityScore", null,
                "totalScore", null,
                "positionStatus", TradingStatus.WHITE.name(),
                "averageBuyPrice", plain(averagePrice),
                "highestPrice", plain(averagePrice),
                "lowestPrice", plain(averagePrice),
                "returnRate", "0",
                "grayTradingDays", 0,
                "brokerOrderOrgNo", null,
                "lifecycleKey", lifecycleKey,
                "orderSource", "ACCOUNT_SYNC_FALLBACK"
        ));
        Map<String, Object> order = tradingMapper.selectOrderByIdempotencyKey(MapUtils.map("idempotencyKey", idempotencyKey));
        if (order != null) {
            tradingMapper.markOrderFilledByAccountSync(MapUtils.map(
                    "orderId", MapUtils.longValue(order, "id"),
                    "filledQuantity", plain(quantity),
                    "filledPrice", plain(averagePrice),
                    "checkedAt", syncedAt,
                    "errorMessage", "ACCOUNT_SYNC_FALLBACK_CONFIRMED"
            ));
        }
        return order == null ? null : MapUtils.longValue(order, "id");
    }

    private void reducePositionsToAccountQuantity(List<Map<String, Object>> positions,
                                                  BigDecimal accountQuantity,
                                                  String syncedAt,
                                                  Map<String, Object> orderSync) {
        BigDecimal remaining = accountQuantity;
        for (Map<String, Object> position : positions) {
            BigDecimal currentQuantity = holdingQuantity(position);
            if (remaining.compareTo(currentQuantity) >= 0) {
                remaining = remaining.subtract(currentQuantity);
                continue;
            }
            if (remaining.signum() > 0) {
                updatePositionQuantity(position, remaining, syncedAt);
                remaining = BigDecimal.ZERO;
            } else {
                closePositionFromAccountSync(position, syncedAt, "ACCOUNT_SYNC", orderSync);
            }
        }
    }

    private void updatePositionQuantity(Map<String, Object> position, BigDecimal quantity, String updatedAt) {
        BigDecimal averagePrice = purchasePrice(position);
        tradingMapper.updatePositionHoldingQuantity(MapUtils.map(
                "positionId", MapUtils.longValue(position, "id"),
                "holdingQuantity", plain(quantity),
                "investedAmount", plain(averagePrice.multiply(quantity)),
                "updatedAt", updatedAt
        ));
        Map<String, Object> updatedPosition = new LinkedHashMap<>(position);
        updatedPosition.put("holdingQuantity", quantity);
        updatedPosition.put("purchaseQuantity", quantity);
        updatedPosition.put("investedAmount", averagePrice.multiply(quantity));
        recordLifecycleEvent(snapshotEvent(updatedPosition, LifecycleEventType.ACCOUNT_SYNC_UPDATED,
                tradingStatus(MapUtils.string(position, "status")), tradingStatus(MapUtils.string(position, "status")),
                MapUtils.decimal(position, "currentPrice"), "ACCOUNT_SYNC_QUANTITY_REDUCED",
                "account-sync-partial:" + MapUtils.longValue(position, "id") + ":" + updatedAt, null));
    }

    private void recordAccountSyncLifecycleEvent(Long positionId,
                                                 LifecycleEventType eventType,
                                                 TradingStatus previousState,
                                                 TradingStatus newState,
                                                 Map<String, Object> holding,
                                                 BigDecimal averagePrice,
                                                 BigDecimal quantity,
                                                 String reason,
                                                 String syncedAt,
                                                 Long orderId,
                                                 String lifecycleKey) {
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
                "orderId", orderId,
                "executionId", MapUtils.string(holding, "stockCode"),
                "idempotencyKey", "account-sync:" + MapUtils.string(holding, "stockCode") + ":" + positionId + ":" + eventType.name() + ":" + syncedAt,
                "lifecycleKey", lifecycleKey,
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
                "lifecycleKey", MapUtils.string(event, "lifecycleKey") == null
                        ? lifecycleKey(MapUtils.longValue(event, "lifecycleId"))
                        : MapUtils.string(event, "lifecycleKey"),
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
                Long positionId = MapUtils.longValue(position, "id");
                if (tradingMapper.countOpenSellOrderByPositionId(MapUtils.map("positionId", positionId)) > 0
                        || tradingMapper.countOpenUnlinkedSellOrderByStockCode(MapUtils.map("stockCode", stockCode)) > 0) {
                    logger.debug("BLACK sell skipped because open sell order exists. stockCode={}", stockCode);
                    continue;
                }
                BigDecimal quantity = holdingQuantity(position);
                if (quantity.signum() <= 0) {
                    insertAuditLog("SELL_SKIPPED", stockCode, "ZERO_HOLDING_QUANTITY; waiting for account sync");
                    continue;
                }
                String marketType = positionMarketType(position);
                Map<String, Object> currentPrice = brokerClient.getCurrentPrice(stockCode, marketType);
                BigDecimal price = MapUtils.decimal(currentPrice, "price");
                if (price.signum() <= 0) {
                    insertAuditLog("SELL_SKIPPED", stockCode, "INVALID_CURRENT_PRICE");
                    logger.debug("BLACK sell skipped. stockCode={}, reason=INVALID_CURRENT_PRICE", stockCode);
                    continue;
                }

                String idempotencyKey = maskedAccountNumber() + "|AUTO|SELL|" + stockCode + "|" + MapUtils.longValue(position, "id") + "|" + decisionCycleId;
                Map<String, Object> request = MapUtils.map(
                        "stockCode", stockCode,
                        "marketType", marketType,
                        "positionId", positionId,
                        "lifecycleKey", lifecycleKey(positionId),
                        "orderQuantity", quantity,
                        "orderPrice", orderPrice(currentPrice, marketType),
                        "orderAmount", quantity.multiply(price),
                        "reason", "BLACK_SELL",
                        "decisionCycleId", decisionCycleId,
                        "idempotencyKey", idempotencyKey,
                        "instanceId", runtimeProperties.getInstanceId(),
                        "maskedAccount", maskedAccountNumber(),
                        "currentPrice", price,
                        "currentPriceAt", MapUtils.offsetDateTime(currentPrice, "receivedAt"),
                        "candidateRank", null,
                        "tradingValueScore", null,
                        "volumeScore", null,
                        "volatilityScore", null,
                        "totalScore", null,
                        "positionStatus", MapUtils.string(position, "status"),
                        "averageBuyPrice", plain(purchasePrice(position)),
                        "highestPrice", plain(max(zeroIfNull(MapUtils.decimal(position, "highestPrice")), price)),
                        "lowestPrice", plain(min(nonZeroOr(MapUtils.decimal(position, "lowestPrice"), price), price)),
                        "returnRate", plain(rate(price, purchasePrice(position))),
                        "grayTradingDays", MapUtils.integer(position, "grayTradingDays"),
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

        logger.debug("cancelling open buy orders before the next buy cycle. count={}", openBuyOrders.size());
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
                        "marketType", investmentProperties.getMarketType(),
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
                        logger.debug("open buy order cancellation requested; final state awaits KIS inquiry. stockCode={}, orderId={}, previousStatus={}",
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

        logger.debug("cancelling open sell orders before BLACK processing. count={}", openSellOrders.size());
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
                    Map<String, Object> position = tradingMapper.selectPositionById(MapUtils.map("positionId", MapUtils.longValue(order, "positionId")));
                    result = brokerClient.cancel(MapUtils.map(
                            "stockCode", stockCode,
                            "marketType", positionMarketType(position),
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
                        logger.debug("open sell order cancellation requested; final state awaits KIS inquiry. stockCode={}, orderId={}, previousStatus={}",
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
            Map<String, Object> currentPrice = brokerClient.getCurrentPrice(stockCode, positionMarketType(position));
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
        String flatActive = "Y".equalsIgnoreCase(MapUtils.string(position, "flatActive"))
                || (flatStartedDate != null && !flatStartedDate.isBlank()) ? "Y" : "N";
        BigDecimal statusReferencePrice = zeroIfNull(MapUtils.decimal(position, "statusReferencePrice"));
        BigDecimal referencePrice = zeroIfNull(MapUtils.decimal(position, "referencePrice"));

        int comparison = currentPrice.compareTo(previousPrice);
        if (previousStatus == TradingStatus.WHITE) {
            String exitReason = exitTriggerReason(returnRate);
            if (exitReason != null) {
                nextStatus = TradingStatus.BLACK;
                reason = exitReason;
                flatStartedDate = null;
                flatActive = "N";
            } else if (comparison < 0) {
                nextStatus = TradingStatus.GRAY;
                reason = "PRICE_DECLINE";
                grayEnteredDate = today.toString();
                grayTradingDays = 0;
                flatStartedDate = null;
                flatActive = "N";
                statusReferencePrice = previousPrice;
                referencePrice = previousPrice;
            } else if (comparison == 0) {
                if (!"Y".equals(flatActive)) {
                    flatStartedDate = today.toString();
                    flatActive = "Y";
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
                    flatActive = "N";
                }
            } else {
                flatStartedDate = null;
                flatActive = "N";
                statusReferencePrice = currentPrice;
                referencePrice = currentPrice;
            }
        } else if (previousStatus == TradingStatus.GRAY) {
            flatStartedDate = null;
            flatActive = "N";
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
            } else if (currentPrice.compareTo(previousPrice) > 0
                    && currentPrice.compareTo(purchasePrice) >= 0) {
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
                "referencePrice", plain(referencePrice),
                "highestPrice", plain(highestPrice),
                "lowestPrice", plain(lowestPrice),
                "holdingQuantity", plain(quantity),
                "returnRate", plain(returnRate),
                "lastEvaluatedAt", evaluatedAt,
                "flatStartedDate", flatStartedDate,
                "flatActive", flatActive,
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
                "lifecycleKey", lifecycleKey(MapUtils.longValue(position, "id")),
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

    private void closePositionFromAccountSync(Map<String, Object> position,
                                              String closedAt,
                                              String reason,
                                              Map<String, Object> orderSync) {
        BigDecimal currentPrice = zeroIfNull(MapUtils.decimal(position, "currentPrice"));
        BigDecimal quantity = holdingQuantity(position);
        BigDecimal purchasePrice = purchasePrice(position);
        BigDecimal valuationAmount = currentPrice.multiply(quantity);
        BigDecimal returnRate = rate(currentPrice, purchasePrice);
        TradingStatus previousStatus = tradingStatus(MapUtils.string(position, "status"));
        Map<String, Object> sellOrder = tradingMapper.selectLatestSellOrderByPositionId(MapUtils.map(
                "positionId", MapUtils.longValue(position, "id")
        ));
        if (sellOrder == null) {
            Long fallbackSellOrderId = insertAccountSyncFallbackSellOrder(position, closedAt, currentPrice, quantity, reason);
            sellOrder = fallbackSellOrderId == null ? null : tradingMapper.selectLatestSellOrderByPositionId(MapUtils.map(
                    "positionId", MapUtils.longValue(position, "id")
            ));
        }
        Long sellOrderId = sellOrder == null ? null : MapUtils.longValue(sellOrder, "id");
        if (sellOrder != null) {
            if (!MapUtils.bool(orderSync, "successful")) {
                insertAuditLog("ACCOUNT_SYNC_SELL_PENDING", MapUtils.string(position, "stockCode"),
                        "sell order status inquiry failed; position remains active; orderId=" + sellOrderId);
                return;
            }
            String brokerOrderId = MapUtils.string(sellOrder, "brokerOrderId");
            Object pendingIdsValue = MapUtils.value(orderSync, "pendingSellBrokerOrderIds");
            boolean pending = pendingIdsValue instanceof Set<?> pendingIds
                    && brokerOrderId != null
                    && pendingIds.contains(brokerOrderId);
            if (pending) {
                insertAuditLog("ACCOUNT_SYNC_SELL_PENDING", MapUtils.string(position, "stockCode"),
                        "KIS sell order remains open; sell retry remains reserved; orderId=" + sellOrderId);
                return;
            }
            if (isOpenLocalOrderStatus(MapUtils.string(sellOrder, "orderStatus"))) {
                tradingMapper.markOrderFilledByAccountSync(MapUtils.map(
                        "orderId", sellOrderId,
                        "filledQuantity", plain(quantity),
                        "filledPrice", plain(currentPrice),
                        "checkedAt", closedAt,
                        "errorMessage", "ACCOUNT_SYNC_SELL_CONFIRMED"
                ));
                insertAuditLog("ACCOUNT_SYNC_SELL_CONFIRMED", MapUtils.string(position, "stockCode"),
                        "KIS holding disappeared and no open KIS sell order remained; orderId=" + sellOrderId);
            }
        }
        tradingMapper.closePosition(MapUtils.map(
                "positionId", MapUtils.longValue(position, "id"),
                "currentPrice", plain(currentPrice),
                "currentValuationAmount", plain(valuationAmount),
                "profitRate", plain(returnRate),
                "returnRate", plain(returnRate),
                "closedAt", closedAt
        ));
        recordLifecycleEvent(snapshotEvent(position, LifecycleEventType.ACCOUNT_SYNC_CLOSED, previousStatus, TradingStatus.CLOSED,
                currentPrice, reason, "account-sync-closed:" + MapUtils.longValue(position, "id") + ":" + closedAt, sellOrderId));
        recordLifecycleEvent(snapshotEvent(position, LifecycleEventType.CLOSED, previousStatus, TradingStatus.CLOSED,
                currentPrice, reason, "closed-account-sync:" + MapUtils.longValue(position, "id") + ":" + closedAt, sellOrderId));
        insertAuditLog("ACCOUNT_SYNC_CLOSED", MapUtils.string(position, "stockCode"),
                reason + (sellOrderId == null ? "" : "; sellOrderId=" + sellOrderId));
        logger.info("position closed by startup account sync. stockCode={}, reason={}", MapUtils.string(position, "stockCode"), reason);
    }

    private Long insertAccountSyncFallbackSellOrder(Map<String, Object> position,
                                                    String occurredAt,
                                                    BigDecimal currentPrice,
                                                    BigDecimal quantity,
                                                    String reason) {
        Long positionId = MapUtils.longValue(position, "id");
        String lifecycleKey = lifecycleKey(positionId);
        String idempotencyKey = "account-sync-sell-fallback:" + lifecycleKey;
        Map<String, Object> existing = tradingMapper.selectOrderByIdempotencyKey(MapUtils.map(
                "idempotencyKey", idempotencyKey
        ));
        if (existing == null) {
            BigDecimal averageBuyPrice = purchasePrice(position);
            tradingMapper.insertOrderRecordDetailed(MapUtils.map(
                    "brokerOrderId", null,
                    "positionId", positionId,
                    "stockCode", MapUtils.string(position, "stockCode"),
                    "orderType", "SELL",
                    "orderQuantity", plain(quantity),
                    "orderPrice", plain(currentPrice),
                    "orderAmount", plain(currentPrice.multiply(quantity)),
                    "orderStatus", "ACCOUNT_SYNC_FALLBACK",
                    "errorMessage", "No local broker sell order; account sync confirmed position disappearance",
                    "requestedAt", occurredAt,
                    "idempotencyKey", idempotencyKey,
                    "decisionCycleId", "account-sync-sell:" + occurredAt,
                    "instanceId", runtimeProperties.getInstanceId(),
                    "maskedAccount", maskedAccountNumber(),
                    "skipReason", null,
                    "exitReason", reason,
                    "dryRun", "N",
                    "currentPrice", plain(currentPrice),
                    "currentPriceAt", occurredAt,
                    "candidateRank", null,
                    "tradingValueScore", null,
                    "volumeScore", null,
                    "volatilityScore", null,
                    "totalScore", null,
                    "positionStatus", MapUtils.string(position, "status"),
                    "averageBuyPrice", plain(averageBuyPrice),
                    "highestPrice", plain(max(zeroIfNull(MapUtils.decimal(position, "highestPrice")), currentPrice)),
                    "lowestPrice", plain(min(nonZeroOr(MapUtils.decimal(position, "lowestPrice"), currentPrice), currentPrice)),
                    "returnRate", plain(rate(currentPrice, averageBuyPrice)),
                    "grayTradingDays", MapUtils.integer(position, "grayTradingDays"),
                    "brokerOrderOrgNo", null,
                    "lifecycleKey", lifecycleKey,
                    "orderSource", "ACCOUNT_SYNC_FALLBACK"
            ));
            existing = tradingMapper.selectOrderByIdempotencyKey(MapUtils.map("idempotencyKey", idempotencyKey));
        }
        if (existing == null) {
            return null;
        }
        Long orderId = MapUtils.longValue(existing, "id");
        tradingMapper.markOrderFilledByAccountSync(MapUtils.map(
                "orderId", orderId,
                "filledQuantity", plain(quantity),
                "filledPrice", plain(currentPrice),
                "checkedAt", occurredAt,
                "errorMessage", "ACCOUNT_SYNC_SELL_FALLBACK_CONFIRMED"
        ));
        return orderId;
    }

    private boolean isPendingBrokerOrder(String brokerStatus) {
        return "OPEN".equalsIgnoreCase(brokerStatus)
                || "PARTIALLY_FILLED".equalsIgnoreCase(brokerStatus);
    }

    private boolean isOpenLocalOrderStatus(String orderStatus) {
        return "ORDERING".equalsIgnoreCase(orderStatus)
                || "REQUESTED".equalsIgnoreCase(orderStatus)
                || "ACCEPTED".equalsIgnoreCase(orderStatus)
                || "PARTIALLY_FILLED".equalsIgnoreCase(orderStatus)
                || "RETRY_PENDING".equalsIgnoreCase(orderStatus);
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
                logger.debug("buy pipeline skipped because overseas candidate was not selected");
                return;
            }
            int attempts = 0;
            for (Map<String, Object> candidate : candidates) {
                if (attempts >= MAX_BUY_ATTEMPTS_PER_CYCLE) {
                    break;
                }
                attempts++;
                tryBuyCandidate(candidate, decisionCycleId);
            }
            return;
        }

        List<Map<String, Object>> candidates = domesticStockCandidateService.findOrderTargetsForCycle();
        if (candidates.isEmpty()) {
            logger.debug("buy pipeline skipped because domestic candidate was not selected");
            return;
        }
        int attempts = 0;
        for (Map<String, Object> candidate : candidates) {
            if (attempts >= MAX_BUY_ATTEMPTS_PER_CYCLE) {
                break;
            }
            attempts++;
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
                recordSkippedBuyOrder(candidate, currentPrice, MapUtils.string(sizingResult, "reason"), decisionCycleId, idempotencyKey, validationOrder);
                insertAuditLog("BUY_SKIPPED", normalizedStockCode, MapUtils.string(sizingResult, "reason"));
                logger.debug("buy skipped. stockCode={}, reason={}", normalizedStockCode, MapUtils.string(sizingResult, "reason"));
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
                recordBlockedBuyOrder(candidate, currentPrice, sizingResult, MapUtils.string(safetyResult, "reason"), decisionCycleId, idempotencyKey, validationOrder);
                insertAuditLog("BUY_BLOCKED", normalizedStockCode, MapUtils.string(safetyResult, "reason"));
                logger.debug("buy blocked. stockCode={}, reason={}", normalizedStockCode, MapUtils.string(safetyResult, "reason"));
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
                    "candidateRank", MapUtils.value(candidate, "candidateRank"),
                    "tradingValueScore", MapUtils.value(candidate, "tradingValueScore"),
                    "volumeScore", MapUtils.value(candidate, "volumeScore"),
                    "volatilityScore", MapUtils.value(candidate, "volatilityScore"),
                    "totalScore", MapUtils.value(candidate, "totalScore"),
                    "positionStatus", null,
                    "averageBuyPrice", null,
                    "highestPrice", null,
                    "lowestPrice", null,
                    "returnRate", null,
                    "grayTradingDays", null,
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
            logger.debug("buy requested. stockCode={}, quantity={}, expectedAmount={}, accepted={}, status={}, message={}",
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

    private void recordSkippedBuyOrder(Map<String, Object> candidate,
                                       Map<String, Object> currentPrice,
                                       String reason,
                                       String decisionCycleId,
                                       String idempotencyKey,
                                       boolean validationOrder) {
        tradingMapper.insertOrderRecordDetailed(orderRecord(
                candidate, "BUY", "0", orderPrice(currentPrice).toPlainString(), "0",
                "SKIPPED", reason, decisionCycleId, idempotencyKey, reason, null, currentPrice));
        if (validationOrder) {
            insertAuditLog("FRACTIONAL_VALIDATION_SKIPPED", MapUtils.string(candidate, "symbol"), reason);
        }
    }

    private void recordBlockedBuyOrder(Map<String, Object> candidate,
                                       Map<String, Object> currentPrice,
                                       Map<String, Object> sizingResult,
                                       String reason,
                                       String decisionCycleId,
                                       String idempotencyKey,
                                       boolean validationOrder) {
        tradingMapper.insertOrderRecordDetailed(orderRecord(
                candidate, "BUY", MapUtils.decimal(sizingResult, "quantity").toPlainString(), orderPrice(currentPrice).toPlainString(),
                MapUtils.decimal(sizingResult, "expectedAmount").toPlainString(), "BLOCKED", reason, decisionCycleId,
                idempotencyKey, reason, null, currentPrice));
        if (validationOrder) {
            insertAuditLog("FRACTIONAL_VALIDATION_BLOCKED", MapUtils.string(candidate, "symbol"), reason);
        }
    }

    private BigDecimal orderPrice(Map<String, Object> currentPrice) {
        return orderPrice(currentPrice, investmentProperties.getMarketType());
    }

    private BigDecimal orderPrice(Map<String, Object> currentPrice, String marketType) {
        if ("OVERSEAS".equalsIgnoreCase(marketType)) {
            return normalizeOverseasOrderPrice(MapUtils.decimal(currentPrice, "price"));
        }
        return BigDecimal.ZERO;
    }

    private String positionMarketType(Map<String, Object> position) {
        String marketType = position == null ? null : MapUtils.string(position, "marketType");
        if ("DOMESTIC".equalsIgnoreCase(marketType) || "OVERSEAS".equalsIgnoreCase(marketType)) {
            return marketType.toUpperCase(Locale.ROOT);
        }
        return investmentProperties.getMarketType();
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
        return graceTradingDays <= 0 || tradingDays > graceTradingDays;
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
                                             String syncedAt,
                                             String accountSyncSource) {
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
                "flatActive", "N",
                "accountSyncSource", accountSyncSource,
                "marketType", investmentProperties.getMarketType(),
                "lifecycleKey", null,
                "createdAt", syncedAt,
                "updatedAt", syncedAt
        );
    }

    private String lifecycleKey(Long positionId) {
        return positionId == null ? null : "POSITION-" + positionId;
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

    private Map<String, Object> orderRecord(Map<String, Object> candidate,
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
                "positionId", null,
                "stockCode", MapUtils.string(candidate, "symbol"),
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
                "currentPriceAt", MapUtils.offsetDateTime(currentPrice, "receivedAt") == null ? null : MapUtils.offsetDateTime(currentPrice, "receivedAt").format(TIME_FORMATTER),
                "candidateRank", MapUtils.value(candidate, "candidateRank"),
                "tradingValueScore", MapUtils.value(candidate, "tradingValueScore"),
                "volumeScore", MapUtils.value(candidate, "volumeScore"),
                "volatilityScore", MapUtils.value(candidate, "volatilityScore"),
                "totalScore", MapUtils.value(candidate, "totalScore"),
                "positionStatus", null,
                "averageBuyPrice", null,
                "highestPrice", null,
                "lowestPrice", null,
                "returnRate", null,
                "grayTradingDays", null
        );
    }

    private Map<String, Object> rejected(String message) {
        return MapUtils.map("accepted", false, "brokerOrderId", null, "status", "REJECTED", "message", message);
    }
}
