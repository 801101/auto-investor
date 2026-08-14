package com.won.autoinvestor.common.trade;

import com.won.autoinvestor.common.kis.BrokerClient;
import com.won.autoinvestor.common.util.MapUtils;
import com.won.autoinvestor.pilot.PilotMapper;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class LiveOrderExecutor implements OrderExecutor {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final BrokerClient brokerClient;
    private final PilotMapper tradingMapper;

    public LiveOrderExecutor(BrokerClient brokerClient, PilotMapper tradingMapper) {
        this.brokerClient = brokerClient;
        this.tradingMapper = tradingMapper;
    }

    @Override
    public Map<String, Object> buy(Map<String, Object> request) {
        if (exists(MapUtils.string(request, "idempotencyKey"))) {
            String status = tradingMapper.selectOrderStatusByIdempotencyKey(MapUtils.map("idempotencyKey", MapUtils.string(request, "idempotencyKey")));
            return reused(status, "live buy reused by idempotency key");
        }
        reserveOrder("BUY", request);
        try {
            Map<String, Object> result = brokerClient.buy(request);
            updateBrokerResult(MapUtils.string(request, "idempotencyKey"), result);
            return result;
        } catch (RuntimeException e) {
            updateBrokerResult(MapUtils.string(request, "idempotencyKey"), rejected(e.getMessage()));
            throw e;
        }
    }

    @Override
    public Map<String, Object> sell(Map<String, Object> request) {
        if (exists(MapUtils.string(request, "idempotencyKey"))) {
            String status = tradingMapper.selectOrderStatusByIdempotencyKey(MapUtils.map("idempotencyKey", MapUtils.string(request, "idempotencyKey")));
            return reused(status, "live sell reused by idempotency key");
        }
        reserveOrder("SELL", request);
        try {
            Map<String, Object> result = brokerClient.sell(request);
            updateBrokerResult(MapUtils.string(request, "idempotencyKey"), result);
            return result;
        } catch (RuntimeException e) {
            updateBrokerResult(MapUtils.string(request, "idempotencyKey"), rejected(e.getMessage()));
            throw e;
        }
    }

    private boolean exists(String idempotencyKey) {
        return idempotencyKey != null && !idempotencyKey.isBlank()
                && tradingMapper.countOrderByIdempotencyKey(MapUtils.map("idempotencyKey", idempotencyKey)) > 0;
    }

    private void reserveOrder(String orderType, Map<String, Object> request) {
        String requestedAt = OffsetDateTime.now().format(TIME_FORMATTER);
        tradingMapper.insertOrderRecordDetailed(MapUtils.map(
                 "brokerOrderId", null,
                 "positionId", MapUtils.value(request, "positionId") == null ? null : MapUtils.longValue(request, "positionId"),
                 "lifecycleKey", MapUtils.string(request, "lifecycleKey"),
                "stockCode", MapUtils.string(request, "stockCode"),
                "orderType", orderType,
                "orderQuantity", MapUtils.decimal(request, "orderQuantity").toPlainString(),
                "orderPrice", MapUtils.value(request, "orderPrice") == null ? null : MapUtils.decimal(request, "orderPrice").toPlainString(),
                "orderAmount", MapUtils.decimal(request, "orderAmount").toPlainString(),
                "orderStatus", "ORDERING",
                "errorMessage", null,
                "requestedAt", requestedAt,
                "idempotencyKey", MapUtils.string(request, "idempotencyKey"),
                "decisionCycleId", MapUtils.string(request, "decisionCycleId"),
                "instanceId", MapUtils.string(request, "instanceId"),
                "maskedAccount", MapUtils.string(request, "maskedAccount"),
                "skipReason", null,
                "exitReason", MapUtils.value(request, "exitReason") == null ? null : MapUtils.string(request, "exitReason"),
                "dryRun", "N",
                "currentPrice", MapUtils.value(request, "currentPrice") == null ? null : MapUtils.decimal(request, "currentPrice").toPlainString(),
                "currentPriceAt", MapUtils.offsetDateTime(request, "currentPriceAt") == null ? null : MapUtils.offsetDateTime(request, "currentPriceAt").format(TIME_FORMATTER),
                "candidateRank", MapUtils.value(request, "candidateRank"),
                "tradingValueScore", MapUtils.value(request, "tradingValueScore"),
                "volumeScore", MapUtils.value(request, "volumeScore"),
                "volatilityScore", MapUtils.value(request, "volatilityScore"),
                "totalScore", MapUtils.value(request, "totalScore"),
                "positionStatus", MapUtils.value(request, "positionStatus"),
                "averageBuyPrice", MapUtils.value(request, "averageBuyPrice"),
                "highestPrice", MapUtils.value(request, "highestPrice"),
                "lowestPrice", MapUtils.value(request, "lowestPrice"),
                "returnRate", MapUtils.value(request, "returnRate"),
                 "grayTradingDays", MapUtils.value(request, "grayTradingDays")
                 ,"orderSource", MapUtils.value(request, "orderSource") == null ? "BROKER_ORDER" : MapUtils.string(request, "orderSource")
         ));
    }

    private void updateBrokerResult(String idempotencyKey, Map<String, Object> result) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || result == null) {
            return;
        }
        if (!MapUtils.bool(result, "accepted")) {
            tradingMapper.updateOrderBrokerResultByIdempotencyKey(MapUtils.map(
                    "idempotencyKey", idempotencyKey,
                    "brokerOrderId", MapUtils.string(result, "brokerOrderId"),
                    "brokerOrderOrgNo", MapUtils.string(result, "brokerOrderOrgNo"),
                    "orderStatus", "REJECTED",
                    "brokerStatus", "REJECTED",
                    "errorMessage", MapUtils.string(result, "message"),
                    "updatedAt", OffsetDateTime.now().format(TIME_FORMATTER)
            ));
            return;
        }
        tradingMapper.updateOrderBrokerResultByIdempotencyKey(MapUtils.map(
                "idempotencyKey", idempotencyKey,
                "brokerOrderId", MapUtils.string(result, "brokerOrderId"),
                "brokerOrderOrgNo", MapUtils.string(result, "brokerOrderOrgNo"),
                "orderStatus", "ACCEPTED",
                "brokerStatus", "ACCEPTED",
                "errorMessage", null,
                "updatedAt", OffsetDateTime.now().format(TIME_FORMATTER)
        ));
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

    private Map<String, Object> rejected(String message) {
        return MapUtils.map("accepted", false, "brokerOrderId", null, "status", "REJECTED", "message", message);
    }
}
