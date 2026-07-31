package com.won.autoinvestor.common.trade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.won.autoinvestor.common.util.MapUtils;
import com.won.autoinvestor.common.config.InvestmentProperties;
import com.won.autoinvestor.pilot.PilotMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OverseasStockCandidateService {

    private static final Logger logger = LoggerFactory.getLogger(OverseasStockCandidateService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final int DASHBOARD_BUFFER_SIZE = 20;
    private static final long RETRY_INTERVAL_SECONDS = 30L;

    private final InvestmentProperties investmentProperties;
    private final PilotMapper mapper;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public OverseasStockCandidateService(InvestmentProperties investmentProperties,
                                         PilotMapper mapper,
                                         Clock clock,
                                         ObjectMapper objectMapper) {
        this.investmentProperties = investmentProperties;
        this.mapper = mapper;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<Map<String, Object>> findOrderTargetsForCycle() {
        String now = now();
        if (!isOverseasOrderMode()) {
            logger.info("overseas candidate skipped because market is not overseas");
            return List.of();
        }

        int usedSlots = mapper.overseasCountActiveHeldAndOpenBuyQuantity();
        int remainingSlots = remainingSlots(usedSlots);
        if (remainingSlots <= 0) {
            mapper.insertAuditLog(MapUtils.map("eventType", "BUY_SKIPPED", "stockCode", null, "details", "MAX_HOLDING_SLOTS_REACHED", "createdAt", now));
            logger.info("overseas candidate skipped because max holding slots reached. usedSlots={}, maxHoldings={}",
                    usedSlots, investmentProperties.getMaxHoldings());
            refreshDashboard(now, usedSlots, 0);
            return List.of();
        }

        Map<String, Object> selection = refreshDashboard(now, usedSlots, remainingSlots);
        if (orderTargets(selection).isEmpty()) {
            String reason = MapUtils.integer(selection, "buyableCount") == 0 ? "NO_FRACTIONAL_TRADABLE_CANDIDATE" : "NO_ELIGIBLE_CANDIDATE_AFTER_EXCLUSIONS";
            mapper.insertAuditLog(MapUtils.map("eventType", "BUY_SKIPPED", "stockCode", null, "details", reason, "createdAt", now));
            logger.info("overseas candidate not selected. reason={}, buyableCandidates={}", reason, MapUtils.integer(selection, "buyableCount"));
            return List.of();
        }

        for (Map<String, Object> candidate : orderTargets(selection)) {
            mapper.overseasTouchCandidateSelected(MapUtils.map("id", MapUtils.longValue(candidate, "id"), "selectedAt", now));
            logger.info("overseas candidate selected. symbol={}, exchangeCode={}", MapUtils.string(candidate, "symbol"), MapUtils.string(candidate, "exchangeCode"));
        }
        return orderTargets(selection);
    }

    @Transactional
    public Map<String, Object> refreshDashboard() {
        int usedSlots = mapper.overseasCountActiveHeldAndOpenBuyQuantity();
        return refreshDashboard(now(), usedSlots, remainingSlots(usedSlots));
    }

    @Transactional
    public Map<String, Object> refreshDashboard(String evaluatedAt, int usedSlots, int remainingSlots) {
        List<Map<String, Object>> rows = mapper.overseasSelectCandidateEvaluations(MapUtils.map(
                "exchangeCode", investmentProperties.getOverseasExchangeCode(),
                "priceExchangeCode", investmentProperties.getOverseasPriceExchangeCode(),
                "currencyCode", investmentProperties.getOverseasCurrencyCode()));
        List<Map<String, Object>> scored = rows.stream()
                .map(row -> score(row, evaluatedAt))
                .sorted(candidateComparator())
                .toList();

        int activeLimit = Math.max(remainingSlots, 0);
        int bufferLimit = DASHBOARD_BUFFER_SIZE;
        int dashboardLimit = activeLimit + bufferLimit;
        List<Map<String, Object>> targets = new ArrayList<>();
        int rank = 1;
        int activeCount = 0;
        int bufferCount = 0;
        int buyableCount = 0;
        Set<String> selectedSymbols = new HashSet<>();

        for (Map<String, Object> scoredCandidate : scored) {
            boolean buyable = MapUtils.string(scoredCandidate, "exclusionReason") == null;
            if (buyable) {
                buyableCount++;
            }
            String zone = "EXCLUDED";
            String status = buyable ? "READY" : "EXCLUDED";
            if (buyable && activeCount < activeLimit) {
                zone = "ACTIVE";
                status = "READY";
                activeCount++;
                Map<String, Object> row = row(scoredCandidate);
                if (targets.size() < activeLimit && selectedSymbols.add(MapUtils.string(row, "symbol"))) {
                    targets.add(toCandidate(scoredCandidate));
                }
            } else if (buyable && bufferCount < bufferLimit) {
                zone = "BUFFER";
                status = "WATCHING";
                bufferCount++;
            }

            boolean required = MapUtils.integer(scoredCandidate, "currentDuplicateCount") > 0;
            if (!required && "EXCLUDED".equals(zone) && rank > dashboardLimit) {
                continue;
            }

            mapper.overseasUpsertDashboardRow(toDashboardRow(scoredCandidate, rank++, zone, status, buyable, evaluatedAt));
        }
        mapper.overseasDeleteStaleDashboardRows(MapUtils.map("exchangeCode", investmentProperties.getOverseasExchangeCode(), "evaluatedAt", evaluatedAt));
        logger.info("overseas dashboard refreshed. evaluated={}, usedSlots={}, remainingSlots={}, active={}, buffer={}, buyable={}",
                rows.size(), usedSlots, remainingSlots, activeCount, bufferCount, buyableCount);
        return MapUtils.map("orderTargets", targets, "buyableCount", buyableCount);
    }

    public List<Map<String, Object>> findDashboardRows() {
        return mapper.overseasSelectDashboardRows(MapUtils.map("exchangeCode", investmentProperties.getOverseasExchangeCode()));
    }

    public void recordBuyAttempt(String symbol, String exchangeCode) {
        mapper.overseasUpdateCandidateBuyAttempt(MapUtils.map("symbol", symbol, "exchangeCode", exchangeCode, "attemptedAt", now()));
    }

    public void recordFractionalValidationAttempt(String symbol, String exchangeCode) {
        String attemptedAt = now();
        mapper.overseasMarkFractionalVerificationAttempt(MapUtils.map("symbol", symbol, "exchangeCode", exchangeCode, "attemptedAt", attemptedAt));
        mapper.overseasUpdateCandidateBuyAttempt(MapUtils.map("symbol", symbol, "exchangeCode", exchangeCode, "attemptedAt", attemptedAt));
    }

    public void recordBuyResult(String symbol, String exchangeCode, boolean accepted, String reason) {
        String now = now();
        if (accepted) {
            mapper.overseasMarkCandidateSuccess(MapUtils.map("symbol", symbol, "exchangeCode", exchangeCode, "successAt", now));
            return;
        }
        OffsetDateTime retryAt = OffsetDateTime.now(clock).plusSeconds(RETRY_INTERVAL_SECONDS);
        mapper.overseasMarkCandidateFailure(MapUtils.map("symbol", symbol, "exchangeCode", exchangeCode,
                "retryAfter", retryAt.format(TIME_FORMATTER), "reason", candidateFailureReason(reason), "updatedAt", now));
    }

    public void recordFractionalValidationResult(String symbol,
                                                 String exchangeCode,
                                                 Map<String, Object> orderResult,
                                                 boolean dryRun) {
        String now = now();
        if (dryRun) {
            mapper.insertAuditLog(MapUtils.map("eventType", "FRACTIONAL_VALIDATION_SKIPPED", "stockCode", symbol, "details", "VALIDATION_SKIPPED_DRY_RUN", "createdAt", now));
            return;
        }

        Map<String, Object> response = summarize(orderResult);
        if (orderResult != null && MapUtils.bool(orderResult, "accepted")) {
            mapper.overseasMarkFractionalVerificationYes(MapUtils.map(
                    "symbol", symbol,
                    "exchangeCode", exchangeCode,
                    "source", "ORDER_ACCEPTED",
                    "responseCode", MapUtils.string(response, "code"),
                    "responseMessage", MapUtils.string(response, "message"),
                    "verifiedAt", now
            ));
            mapper.overseasMarkCandidateSuccess(MapUtils.map("symbol", symbol, "exchangeCode", exchangeCode, "successAt", now));
            mapper.insertAuditLog(MapUtils.map("eventType", "FRACTIONAL_VALIDATION_YES", "stockCode", symbol, "details", MapUtils.string(response, "message"), "createdAt", now));
            return;
        }

        OffsetDateTime retryAt = OffsetDateTime.now(clock).plusSeconds(RETRY_INTERVAL_SECONDS);
        mapper.overseasKeepFractionalVerificationUnknown(MapUtils.map(
                "symbol", symbol,
                "exchangeCode", exchangeCode,
                "responseCode", MapUtils.string(response, "code"),
                "responseMessage", MapUtils.string(response, "message"),
                "retryAfter", retryAt.format(TIME_FORMATTER),
                "updatedAt", now
        ));
        mapper.insertAuditLog(MapUtils.map("eventType", "FRACTIONAL_VALIDATION_UNKNOWN", "stockCode", symbol, "details", MapUtils.string(response, "message"), "createdAt", now));
    }

    private Map<String, Object> score(Map<String, Object> row, String now) {
        int maxDuplicates = investmentProperties.getAllowDuplicateStock();
        int currentDuplicates = MapUtils.integer(row, "completedBuyCount") + MapUtils.integer(row, "pendingBuyCount") + MapUtils.integer(row, "reservedBuyCount");
        int remainingDuplicates = maxDuplicates == 0 ? Integer.MAX_VALUE : Math.max(maxDuplicates - currentDuplicates, 0);
        String exclusion = exclusionReason(row, now, remainingDuplicates);

        BigDecimal rotationScore = rotationScore(row);
        return MapUtils.map("row", row, "candidateScore", rotationScore, "currentDuplicateCount", currentDuplicates, "remainingDuplicateCount", remainingDuplicates, "exclusionReason", exclusion);
    }

    private String exclusionReason(Map<String, Object> row, String now, int remainingDuplicates) {
        if ("AMOUNT".equalsIgnoreCase(investmentProperties.getOrderUnitType())
                && !isFractionalCandidateAllowed(row)) {
            return "FRACTIONAL_NOT_CONFIRMED";
        }
        if (isUnsupportedInstrument(row)) {
            return "UNSUPPORTED_INSTRUMENT";
        }
        if (MapUtils.integer(row, "pendingBuyCount") > 0 || MapUtils.integer(row, "reservedBuyCount") > 0) {
            return "OPEN_BUY_ORDER_EXISTS";
        }
        if (remainingDuplicates <= 0) {
            return "DUPLICATE_LIMIT_REACHED";
        }
        String retryAfter = MapUtils.string(row, "retryAfter");
        if (retryAfter != null && retryAfter.compareTo(now) > 0) {
            return "RETRY_AFTER_NOT_REACHED";
        }
        return null;
    }

    private boolean isUnsupportedInstrument(Map<String, Object> row) {
        String symbol = MapUtils.string(row, "symbol") == null ? "" : MapUtils.string(row, "symbol").toUpperCase();
        String name = MapUtils.string(row, "stockName") == null ? "" : MapUtils.string(row, "stockName").toUpperCase();
        boolean unsupportedType = symbol.endsWith("U")
                || symbol.endsWith("W")
                || symbol.endsWith("R")
                || name.contains(" WARRANT")
                || name.contains(" RIGHT")
                || name.contains(" UNIT")
                || name.contains(" UNITS")
                || name.contains("ACQUISITION")
                || name.contains("애퀴지션")
                || name.contains("워런트")
                || name.contains("권리")
                || name.contains("유닛");
        if (unsupportedType) {
            return true;
        }
        if (!investmentProperties.isIncludeEtf()) {
            return name.contains(" ETF")
                    || name.contains(" TRUST")
                    || name.contains(" FUND")
                    || name.contains(" DAILY ")
                    || name.contains(" 2X")
                    || name.contains(" 3X")
                    || name.contains(" LEVERAGE")
                    || name.contains(" INVERSE");
        }
        return false;
    }

    private boolean isFractionalCandidateAllowed(Map<String, Object> row) {
        return "YES".equalsIgnoreCase(MapUtils.string(row, "fractionalTradable"));
    }

    private BigDecimal rotationScore(Map<String, Object> row) {
        if (MapUtils.string(row, "lastSelectedAt") == null && MapUtils.string(row, "lastBuyAttemptAt") == null && MapUtils.string(row, "lastBuySuccessAt") == null) {
            return new BigDecimal("100");
        }
        return new BigDecimal("50");
    }

    private Comparator<Map<String, Object>> candidateComparator() {
        return Comparator.<Map<String, Object>, Integer>comparing(candidate -> routeAcceptedPriority(MapUtils.string(row(candidate), "lastKisResponseCode")))
                .thenComparing(candidate -> MapUtils.decimal(candidate, "candidateScore"), Comparator.reverseOrder())
                .thenComparing(candidate -> securityTypePriority(MapUtils.string(row(candidate), "securityType")))
                .thenComparing(candidate -> MapUtils.integer(candidate, "remainingDuplicateCount"), Comparator.reverseOrder())
                .thenComparing(candidate -> nullToOldest(MapUtils.string(row(candidate), "lastBuySuccessAt")))
                .thenComparing(candidate -> nullToOldest(MapUtils.string(row(candidate), "lastSelectedAt")))
                .thenComparing(candidate -> MapUtils.string(row(candidate), "symbol"));
    }

    private int securityTypePriority(String securityType) {
        return "2".equals(securityType) ? 0 : 1;
    }

    private int routeAcceptedPriority(String lastKisResponseCode) {
        return "40600000".equals(lastKisResponseCode) ? 0 : 1;
    }

    private String candidateFailureReason(String reason) {
        if (reason != null && reason.contains("EGW00202")) {
            return "GATEWAY_ROUTING_ERROR";
        }
        return reason;
    }

    private Map<String, Object> summarize(Map<String, Object> result) {
        if (result == null) {
            return MapUtils.map("code", null, "message", "ORDER_RESULT_NULL");
        }
        String message = MapUtils.string(result, "message");
        if (message == null || message.isBlank()) {
            return MapUtils.map("code", MapUtils.string(result, "status"), "message", MapUtils.string(result, "status"));
        }
        try {
            JsonNode root = objectMapper.readTree(message);
            String code = textFirst(root, "msg_cd", "rt_cd", "code");
            String responseMessage = textFirst(root, "msg1", "message", "msg");
            return MapUtils.map("code", code.isBlank() ? MapUtils.string(result, "status") : code,
                    "message", responseMessage.isBlank() ? message : responseMessage);
        } catch (Exception ignored) {
            return MapUtils.map("code", MapUtils.string(result, "status"), "message", message);
        }
    }

    private String textFirst(JsonNode node, String... names) {
        if (node == null) {
            return "";
        }
        for (String name : names) {
            String value = node.path(name).asText("").trim();
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String nullToOldest(String value) {
        return value == null || value.isBlank() ? "0000" : value;
    }

    private int remainingSlots(int usedSlots) {
        int maxHoldings = investmentProperties.getMaxHoldings();
        if (maxHoldings == 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(maxHoldings - usedSlots, 0);
    }

    private boolean isOverseasOrderMode() {
        return "OVERSEAS".equalsIgnoreCase(investmentProperties.getMarketType());
    }

    private String now() {
        return OffsetDateTime.now(clock).format(TIME_FORMATTER);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> orderTargets(Map<String, Object> selection) {
        Object value = MapUtils.value(selection, "orderTargets");
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> row(Map<String, Object> scoredCandidate) {
        return (Map<String, Object>) MapUtils.value(scoredCandidate, "row");
    }

    private Map<String, Object> toCandidate(Map<String, Object> scoredCandidate) {
        Map<String, Object> row = row(scoredCandidate);
        return MapUtils.map(
                "id", MapUtils.longValue(row, "id"),
                "symbol", MapUtils.string(row, "symbol"),
                "stockName", MapUtils.string(row, "stockName"),
                "exchangeCode", MapUtils.string(row, "exchangeCode"),
                "priceExchangeCode", MapUtils.string(row, "priceExchangeCode"),
                "currencyCode", MapUtils.string(row, "currencyCode"),
                "fractionalTradable", MapUtils.string(row, "fractionalTradable"),
                "overseas", true,
                "validationOrder", false
        );
    }

    private Map<String, Object> toDashboardRow(Map<String, Object> scoredCandidate,
                                               int rankNo,
                                               String purchaseZone,
                                               String dashboardStatus,
                                               boolean purchasable,
                                               String evaluatedAt) {
        Map<String, Object> row = row(scoredCandidate);
        int remainingDuplicateCount = MapUtils.integer(scoredCandidate, "remainingDuplicateCount");
        int currentDuplicateCount = MapUtils.integer(scoredCandidate, "currentDuplicateCount");
        int normalizedRemaining = remainingDuplicateCount == Integer.MAX_VALUE ? 999999 : remainingDuplicateCount;
        int maxDuplicate = normalizedRemaining == 999999 ? 0 : currentDuplicateCount + normalizedRemaining;
        return MapUtils.map(
                "rankNo", rankNo,
                "symbol", MapUtils.string(row, "symbol"),
                "stockName", MapUtils.string(row, "stockName"),
                "exchangeCode", MapUtils.string(row, "exchangeCode"),
                "candidateScore", MapUtils.decimal(scoredCandidate, "candidateScore"),
                "purchaseZone", purchaseZone,
                "dashboardStatus", dashboardStatus,
                "lastPrice", MapUtils.decimal(row, "lastPrice"),
                "completedBuyCount", MapUtils.integer(row, "completedBuyCount"),
                "pendingBuyCount", MapUtils.integer(row, "pendingBuyCount"),
                "reservedBuyCount", MapUtils.integer(row, "reservedBuyCount"),
                "currentDuplicateCount", currentDuplicateCount,
                "maximumDuplicateCount", maxDuplicate,
                "remainingDuplicateCount", normalizedRemaining,
                "totalInvestedAmount", MapUtils.decimal(row, "totalInvestedAmount"),
                "pendingInvestmentAmount", MapUtils.decimal(row, "pendingInvestmentAmount"),
                "purchasable", purchasable,
                "exclusionReason", MapUtils.string(scoredCandidate, "exclusionReason"),
                "lastSelectedAt", MapUtils.string(row, "lastSelectedAt"),
                "lastBuySuccessAt", MapUtils.string(row, "lastBuySuccessAt"),
                "retryAfter", MapUtils.string(row, "retryAfter"),
                "evaluatedAt", evaluatedAt,
                "updatedAt", evaluatedAt
        );
    }
}
