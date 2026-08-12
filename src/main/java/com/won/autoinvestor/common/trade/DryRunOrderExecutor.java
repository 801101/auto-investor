package com.won.autoinvestor.common.trade;

import com.won.autoinvestor.common.util.MapUtils;
import com.won.autoinvestor.pilot.PilotMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class DryRunOrderExecutor implements OrderExecutor {

    private static final Logger logger = LoggerFactory.getLogger(DryRunOrderExecutor.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final PilotMapper tradingMapper;

    public DryRunOrderExecutor(PilotMapper tradingMapper) {
        this.tradingMapper = tradingMapper;
    }

    @Override
    public Map<String, Object> buy(Map<String, Object> request) {
        String requestedAt = now();
        if (exists(MapUtils.string(request, "idempotencyKey"))) {
            String status = tradingMapper.selectOrderStatusByIdempotencyKey(MapUtils.map("idempotencyKey", MapUtils.string(request, "idempotencyKey")));
            logger.info("[DRY_RUN_BUY_REUSED] stockCode={}, idempotencyKey={}, status={}",
                    MapUtils.string(request, "stockCode"), MapUtils.string(request, "idempotencyKey"), status);
            return reused(status, "dry-run buy reused by idempotency key");
        }
        logger.info("[DRY_RUN_BUY] stockCode={}, quantity={}, amount={}, price={}, reason={}",
                MapUtils.string(request, "stockCode"), MapUtils.decimal(request, "orderQuantity"),
                MapUtils.decimal(request, "orderAmount"), MapUtils.decimal(request, "orderPrice"), MapUtils.string(request, "reason"));
        tradingMapper.insertOrderRecordDetailed(orderRecord(request, "BUY", "DRY_RUN", null, requestedAt, "Y"));
        tradingMapper.insertAuditLog(MapUtils.map("eventType", "DRY_RUN_BUY", "stockCode", MapUtils.string(request, "stockCode"), "details", request.toString(), "createdAt", requestedAt));
        return accepted(null, "DRY_RUN", "dry-run buy recorded");
    }

    @Override
    public Map<String, Object> sell(Map<String, Object> request) {
        String requestedAt = now();
        if (exists(MapUtils.string(request, "idempotencyKey"))) {
            String status = tradingMapper.selectOrderStatusByIdempotencyKey(MapUtils.map("idempotencyKey", MapUtils.string(request, "idempotencyKey")));
            logger.info("[DRY_RUN_SELL_REUSED] stockCode={}, idempotencyKey={}, status={}",
                    MapUtils.string(request, "stockCode"), MapUtils.string(request, "idempotencyKey"), status);
            return reused(status, "dry-run sell reused by idempotency key");
        }
        logger.info("[DRY_RUN_SELL] stockCode={}, quantity={}, amount={}, price={}, reason={}",
                MapUtils.string(request, "stockCode"), MapUtils.decimal(request, "orderQuantity"),
                MapUtils.decimal(request, "orderAmount"), MapUtils.decimal(request, "orderPrice"), MapUtils.string(request, "reason"));
        tradingMapper.insertOrderRecordDetailed(orderRecord(request, "SELL", "DRY_RUN", null, requestedAt, "Y"));
        tradingMapper.insertAuditLog(MapUtils.map("eventType", "DRY_RUN_SELL", "stockCode", MapUtils.string(request, "stockCode"), "details", request.toString(), "createdAt", requestedAt));
        return accepted(null, "DRY_RUN", "dry-run sell recorded");
    }

    private boolean exists(String idempotencyKey) {
        return idempotencyKey != null && !idempotencyKey.isBlank()
                && tradingMapper.countOrderByIdempotencyKey(MapUtils.map("idempotencyKey", idempotencyKey)) > 0;
    }

    private String now() {
        return OffsetDateTime.now().format(TIME_FORMATTER);
    }

    private Map<String, Object> accepted(String brokerOrderId, String status, String message) {
        return MapUtils.map("accepted", true, "brokerOrderId", brokerOrderId, "status", status, "message", message);
    }

    private Map<String, Object> reused(String status, String message) {
        String reusedStatus = status == null ? "REUSED" : status;
        boolean orderAccepted = !"REJECTED".equalsIgnoreCase(reusedStatus)
                && !"FAILED".equalsIgnoreCase(reusedStatus)
                && !"CANCELLED".equalsIgnoreCase(reusedStatus)
                && !"BLOCKED".equalsIgnoreCase(reusedStatus)
                && !"SKIPPED".equalsIgnoreCase(reusedStatus);
        return MapUtils.map("accepted", orderAccepted, "brokerOrderId", null, "status", reusedStatus, "message", message);
    }

    private Map<String, Object> orderRecord(Map<String, Object> request,
                                            String orderType,
                                            String orderStatus,
                                            String errorMessage,
                                            String requestedAt,
                                            String dryRun) {
        return MapUtils.map(
                "brokerOrderId", null,
                "stockCode", MapUtils.string(request, "stockCode"),
                "orderType", orderType,
                "orderQuantity", MapUtils.decimal(request, "orderQuantity").toPlainString(),
                "orderPrice", MapUtils.value(request, "orderPrice") == null ? null : MapUtils.decimal(request, "orderPrice").toPlainString(),
                "orderAmount", MapUtils.decimal(request, "orderAmount").toPlainString(),
                "orderStatus", orderStatus,
                "errorMessage", errorMessage,
                "requestedAt", requestedAt,
                "idempotencyKey", MapUtils.string(request, "idempotencyKey"),
                "decisionCycleId", MapUtils.string(request, "decisionCycleId"),
                "instanceId", MapUtils.string(request, "instanceId"),
                "maskedAccount", MapUtils.string(request, "maskedAccount"),
                "skipReason", null,
                "exitReason", MapUtils.value(request, "exitReason") == null ? null : MapUtils.string(request, "exitReason"),
                "dryRun", dryRun,
                "currentPrice", MapUtils.value(request, "currentPrice") == null ? null : MapUtils.decimal(request, "currentPrice").toPlainString(),
                "currentPriceAt", MapUtils.offsetDateTime(request, "currentPriceAt") == null ? null : MapUtils.offsetDateTime(request, "currentPriceAt").format(TIME_FORMATTER)
        );
    }
}
