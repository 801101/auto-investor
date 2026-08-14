package com.won.autoinvestor.common.trade;

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
public class DomesticStockCandidateService {

    private static final Logger logger = LoggerFactory.getLogger(DomesticStockCandidateService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final int DASHBOARD_BUFFER_SIZE = 20;
    private static final long RETRY_INTERVAL_SECONDS = 30L;

    private final InvestmentProperties investmentProperties;
    private final PilotMapper mapper;
    private final Clock clock;

    public DomesticStockCandidateService(InvestmentProperties investmentProperties,
                                         PilotMapper mapper,
                                         Clock clock) {
        this.investmentProperties = investmentProperties;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public List<Map<String, Object>> findOrderTargetsForCycle() {
        String now = now();
        if (!"DOMESTIC".equalsIgnoreCase(investmentProperties.getMarketType())) {
            logger.info("domestic candidate skipped because market is not domestic");
            return List.of();
        }

        int usedSlots = mapper.countActivePositions();
        int remainingSlots = remainingSlots(usedSlots);
        if (remainingSlots <= 0) {
            mapper.insertAuditLog(MapUtils.map("eventType", "BUY_SKIPPED", "stockCode", null, "details", "MAX_HOLDING_SLOTS_REACHED", "createdAt", now));
            logger.info("domestic candidate skipped because max holding slots reached. usedSlots={}, maxHoldings={}",
                    usedSlots, investmentProperties.getMaxHoldings());
            refreshDashboard(now, usedSlots, 0);
            return List.of();
        }

        Map<String, Object> selection = refreshDashboard(now, usedSlots, remainingSlots);
        if (orderTargets(selection).isEmpty()) {
            String reason = MapUtils.integer(selection, "buyableCount") == 0 ? "NO_DOMESTIC_BUYABLE_CANDIDATE" : "NO_ELIGIBLE_CANDIDATE_AFTER_EXCLUSIONS";
            mapper.insertAuditLog(MapUtils.map("eventType", "BUY_SKIPPED", "stockCode", null, "details", reason, "createdAt", now));
            logger.info("domestic candidate not selected. reason={}, buyableCandidates={}", reason, MapUtils.integer(selection, "buyableCount"));
            return List.of();
        }

        for (Map<String, Object> candidate : orderTargets(selection)) {
            mapper.domesticTouchCandidateSelected(MapUtils.map("id", MapUtils.longValue(candidate, "id"), "selectedAt", now));
            logger.info("domestic candidate selected. symbol={}, marketCode={}", MapUtils.string(candidate, "symbol"), MapUtils.string(candidate, "marketCode"));
        }
        return orderTargets(selection);
    }

    @Transactional
    public Map<String, Object> refreshDashboard() {
        int usedSlots = mapper.countActivePositions();
        return refreshDashboard(now(), usedSlots, remainingSlots(usedSlots));
    }

    @Transactional
    public Map<String, Object> refreshDashboard(String evaluatedAt, int usedSlots, int remainingSlots) {
        List<Map<String, Object>> rows = mapper.domesticSelectCandidateEvaluations(MapUtils.map("marketCode", investmentProperties.getDomesticMarketCode()));
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
                activeCount++;
                Map<String, Object> row = row(scoredCandidate);
                if (targets.size() < activeLimit && selectedSymbols.add(MapUtils.string(row, "symbol"))) {
                    targets.add(toCandidate(scoredCandidate, rank));
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

            mapper.domesticUpsertDashboardRow(toDashboardRow(scoredCandidate, rank++, zone, status, buyable, evaluatedAt));
        }
        mapper.domesticDeleteStaleDashboardRows(MapUtils.map("marketCode", investmentProperties.getDomesticMarketCode(), "evaluatedAt", evaluatedAt));
        logger.info("domestic dashboard refreshed. evaluated={}, usedSlots={}, remainingSlots={}, active={}, buffer={}, buyable={}",
                rows.size(), usedSlots, remainingSlots, activeCount, bufferCount, buyableCount);
        return MapUtils.map("orderTargets", targets, "buyableCount", buyableCount);
    }

    public List<Map<String, Object>> findDashboardRows() {
        return mapper.domesticSelectDashboardRows(MapUtils.map("marketCode", investmentProperties.getDomesticMarketCode()));
    }

    public void recordBuyAttempt(String symbol, String marketCode) {
        mapper.domesticUpdateCandidateBuyAttempt(MapUtils.map("symbol", symbol, "marketCode", marketCode, "attemptedAt", now()));
    }

    public void recordBuyResult(String symbol, String marketCode, boolean accepted, String reason) {
        String now = now();
        if (accepted) {
            mapper.domesticMarkCandidateSuccess(MapUtils.map("symbol", symbol, "marketCode", marketCode, "successAt", now));
            return;
        }
        OffsetDateTime retryAt = OffsetDateTime.now(clock).plusSeconds(RETRY_INTERVAL_SECONDS);
        mapper.domesticMarkCandidateFailure(MapUtils.map("symbol", symbol, "marketCode", marketCode,
                "retryAfter", retryAt.format(TIME_FORMATTER), "reason", reason, "updatedAt", now));
    }

    private Map<String, Object> score(Map<String, Object> row, String now) {
        int maxHoldingsPerStock = investmentProperties.getMaxHoldingsPerStock();
        int currentDuplicates = MapUtils.integer(row, "completedBuyCount");
        int remainingHoldingsPerStock = maxHoldingsPerStock == 0 ? Integer.MAX_VALUE : Math.max(maxHoldingsPerStock - currentDuplicates, 0);
        String exclusion = exclusionReason(row, now, remainingHoldingsPerStock);

        BigDecimal rotationScore = rotationScore(row);
        return MapUtils.map("row", row, "candidateScore", rotationScore, "currentDuplicateCount", currentDuplicates, "remainingDuplicateCount", remainingHoldingsPerStock, "exclusionReason", exclusion);
    }

    private String exclusionReason(Map<String, Object> row, String now, int remainingHoldingsPerStock) {
        if (isUnsupportedInstrument(row)) {
            return "UNSUPPORTED_INSTRUMENT";
        }
        if (MapUtils.integer(row, "pendingBuyCount") > 0 || MapUtils.integer(row, "reservedBuyCount") > 0) {
            return "OPEN_BUY_ORDER_EXISTS";
        }
        if (remainingHoldingsPerStock <= 0) {
            return "MAX_HOLDINGS_PER_STOCK_REACHED";
        }
        String retryAfter = MapUtils.string(row, "retryAfter");
        if (retryAfter != null && retryAfter.compareTo(now) > 0) {
            return "RETRY_AFTER_NOT_REACHED";
        }
        return null;
    }

    private boolean isUnsupportedInstrument(Map<String, Object> row) {
        if (!investmentProperties.isIncludeEtf() && "Y".equalsIgnoreCase(MapUtils.string(row, "etp"))) {
            return true;
        }
        return "Y".equalsIgnoreCase(MapUtils.string(row, "spac"));
    }

    private BigDecimal rotationScore(Map<String, Object> row) {
        if (MapUtils.string(row, "lastSelectedAt") == null && MapUtils.string(row, "lastBuyAttemptAt") == null && MapUtils.string(row, "lastBuySuccessAt") == null) {
            return new BigDecimal("100");
        }
        return new BigDecimal("50");
    }

    private Comparator<Map<String, Object>> candidateComparator() {
        return Comparator.<Map<String, Object>, BigDecimal>comparing(candidate -> MapUtils.decimal(candidate, "candidateScore"), Comparator.reverseOrder())
                .thenComparing(candidate -> securityTypePriority(MapUtils.string(row(candidate), "securityGroupCode")))
                .thenComparing(candidate -> MapUtils.integer(candidate, "remainingDuplicateCount"), Comparator.reverseOrder())
                .thenComparing(candidate -> nullToOldest(MapUtils.string(row(candidate), "lastBuySuccessAt")))
                .thenComparing(candidate -> nullToOldest(MapUtils.string(row(candidate), "lastSelectedAt")))
                .thenComparing(candidate -> MapUtils.string(row(candidate), "symbol"));
    }

    private int securityTypePriority(String securityType) {
        return "ST".equalsIgnoreCase(securityType) ? 0 : 1;
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

    private Map<String, Object> toCandidate(Map<String, Object> scoredCandidate, int candidateRank) {
        Map<String, Object> row = row(scoredCandidate);
        return MapUtils.map("id", MapUtils.longValue(row, "id"), "symbol", MapUtils.string(row, "symbol"),
                "stockName", MapUtils.string(row, "stockName"), "marketCode", MapUtils.string(row, "marketCode"),
                "candidateRank", candidateRank,
                "tradingValueScore", MapUtils.value(row, "tradingValueScore"),
                "volumeScore", MapUtils.value(row, "volumeScore"),
                "volatilityScore", MapUtils.value(row, "volatilityScore"),
                "totalScore", MapUtils.decimal(scoredCandidate, "candidateScore"),
                "overseas", false, "validationOrder", false);
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
                "marketCode", MapUtils.string(row, "marketCode"),
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
