package com.won.autoinvestor.trading.order;

import com.won.autoinvestor.broker.BrokerClient;
import com.won.autoinvestor.broker.domain.BuyOrderRequest;
import com.won.autoinvestor.broker.domain.OrderResult;
import com.won.autoinvestor.broker.domain.SellOrderRequest;
import com.won.autoinvestor.pilot.mapper.PilotMapper;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public class LiveOrderExecutor implements OrderExecutor {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final BrokerClient brokerClient;
    private final PilotMapper pilotMapper;

    public LiveOrderExecutor(BrokerClient brokerClient, PilotMapper pilotMapper) {
        this.brokerClient = brokerClient;
        this.pilotMapper = pilotMapper;
    }

    @Override
    public OrderResult buy(BuyOrderRequest request) {
        if (exists(request.idempotencyKey())) {
            String status = pilotMapper.selectOrderStatusByIdempotencyKey(request.idempotencyKey());
            return OrderResult.accepted(null, status == null ? "REUSED" : status, "live buy reused by idempotency key");
        }
        reserveOrder("BUY", request.stockCode(), request.orderQuantity().toPlainString(),
                request.orderPrice() == null ? null : request.orderPrice().toPlainString(),
                request.orderAmount().toPlainString(), request.reason(), request.decisionCycleId(),
                request.idempotencyKey(), request.instanceId(), request.maskedAccount(),
                request.exitReason() == null ? null : request.exitReason().name(),
                request.currentPrice() == null ? null : request.currentPrice().toPlainString(),
                request.currentPriceAt() == null ? null : request.currentPriceAt().format(TIME_FORMATTER));
        return brokerClient.buy(request);
    }

    @Override
    public OrderResult sell(SellOrderRequest request) {
        if (exists(request.idempotencyKey())) {
            String status = pilotMapper.selectOrderStatusByIdempotencyKey(request.idempotencyKey());
            return OrderResult.accepted(null, status == null ? "REUSED" : status, "live sell reused by idempotency key");
        }
        reserveOrder("SELL", request.stockCode(), request.orderQuantity().toPlainString(),
                request.orderPrice() == null ? null : request.orderPrice().toPlainString(),
                request.orderAmount().toPlainString(), request.reason(), request.decisionCycleId(),
                request.idempotencyKey(), request.instanceId(), request.maskedAccount(),
                request.exitReason() == null ? null : request.exitReason().name(),
                request.currentPrice() == null ? null : request.currentPrice().toPlainString(),
                request.currentPriceAt() == null ? null : request.currentPriceAt().format(TIME_FORMATTER));
        return brokerClient.sell(request);
    }

    private boolean exists(String idempotencyKey) {
        return idempotencyKey != null && !idempotencyKey.isBlank()
                && pilotMapper.countOrderByIdempotencyKey(idempotencyKey) > 0;
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
        pilotMapper.insertOrderRecordDetailed(
                null,
                stockCode,
                orderType,
                orderQuantity,
                orderPrice,
                orderAmount,
                "REQUESTED",
                errorMessage,
                OffsetDateTime.now().format(TIME_FORMATTER),
                idempotencyKey,
                decisionCycleId,
                instanceId,
                maskedAccount,
                null,
                exitReason,
                "N",
                currentPrice,
                currentPriceAt
        );
    }
}
