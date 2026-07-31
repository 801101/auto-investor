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
            return accepted(null, status == null ? "REUSED" : status, "live buy reused by idempotency key");
        }
        reserveOrder("BUY", MapUtils.string(request, "stockCode"), MapUtils.decimal(request, "orderQuantity").toPlainString(),
                MapUtils.value(request, "orderPrice") == null ? null : MapUtils.decimal(request, "orderPrice").toPlainString(),
                MapUtils.decimal(request, "orderAmount").toPlainString(), MapUtils.string(request, "reason"), MapUtils.string(request, "decisionCycleId"),
                MapUtils.string(request, "idempotencyKey"), MapUtils.string(request, "instanceId"), MapUtils.string(request, "maskedAccount"),
                MapUtils.value(request, "exitReason") == null ? null : MapUtils.string(request, "exitReason"),
                MapUtils.value(request, "currentPrice") == null ? null : MapUtils.decimal(request, "currentPrice").toPlainString(),
                MapUtils.offsetDateTime(request, "currentPriceAt") == null ? null : MapUtils.offsetDateTime(request, "currentPriceAt").format(TIME_FORMATTER));
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
            return accepted(null, status == null ? "REUSED" : status, "live sell reused by idempotency key");
        }
        reserveOrder("SELL", MapUtils.string(request, "stockCode"), MapUtils.decimal(request, "orderQuantity").toPlainString(),
                MapUtils.value(request, "orderPrice") == null ? null : MapUtils.decimal(request, "orderPrice").toPlainString(),
                MapUtils.decimal(request, "orderAmount").toPlainString(), MapUtils.string(request, "reason"), MapUtils.string(request, "decisionCycleId"),
                MapUtils.string(request, "idempotencyKey"), MapUtils.string(request, "instanceId"), MapUtils.string(request, "maskedAccount"),
                MapUtils.value(request, "exitReason") == null ? null : MapUtils.string(request, "exitReason"),
                MapUtils.value(request, "currentPrice") == null ? null : MapUtils.decimal(request, "currentPrice").toPlainString(),
                MapUtils.offsetDateTime(request, "currentPriceAt") == null ? null : MapUtils.offsetDateTime(request, "currentPriceAt").format(TIME_FORMATTER));
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

    private void reserveOrder(String orderType,
                              String stockCode,
                              String orderQuantity,
                              String orderPrice,
                              String orderAmount,
                              String errorMessage,
                              String decisionCycleId,
                              String idempotencyKey,
                              String instanceId,
                              String maskedAccount,
                              String exitReason,
                              String currentPrice,
                              String currentPriceAt) {
        String requestedAt = OffsetDateTime.now().format(TIME_FORMATTER);
        tradingMapper.insertOrderRecordDetailed(MapUtils.map(
                "brokerOrderId", null,
                "stockCode", stockCode,
                "orderType", orderType,
                "orderQuantity", orderQuantity,
                "orderPrice", orderPrice,
                "orderAmount", orderAmount,
                "orderStatus", "ORDERING",
                "errorMessage", errorMessage,
                "requestedAt", requestedAt,
                "idempotencyKey", idempotencyKey,
                "decisionCycleId", decisionCycleId,
                "instanceId", instanceId,
                "maskedAccount", maskedAccount,
                "skipReason", null,
                "exitReason", exitReason,
                "dryRun", "N",
                "currentPrice", currentPrice,
                "currentPriceAt", currentPriceAt
        ));
    }

    private void updateBrokerResult(String idempotencyKey, Map<String, Object> result) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || result == null) {
            return;
        }
        if (!MapUtils.bool(result, "accepted")) {
            tradingMapper.deleteOrderByIdempotencyKey(MapUtils.map("idempotencyKey", idempotencyKey));
            return;
        }
        tradingMapper.updateOrderBrokerResultByIdempotencyKey(MapUtils.map(
                "idempotencyKey", idempotencyKey,
                "brokerOrderId", MapUtils.string(result, "brokerOrderId"),
                "orderStatus", "ACCEPTED",
                "errorMessage", null,
                "updatedAt", OffsetDateTime.now().format(TIME_FORMATTER)
        ));
    }

    private Map<String, Object> accepted(String brokerOrderId, String status, String message) {
        return MapUtils.map("accepted", true, "brokerOrderId", brokerOrderId, "status", status, "message", message);
    }

    private Map<String, Object> rejected(String message) {
        return MapUtils.map("accepted", false, "brokerOrderId", null, "status", "REJECTED", "message", message);
    }
}
