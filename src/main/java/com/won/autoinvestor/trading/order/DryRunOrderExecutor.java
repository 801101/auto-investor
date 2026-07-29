package com.won.autoinvestor.trading.order;

import com.won.autoinvestor.broker.domain.BuyOrderRequest;
import com.won.autoinvestor.broker.domain.OrderResult;
import com.won.autoinvestor.broker.domain.SellOrderRequest;
import com.won.autoinvestor.pilot.mapper.PilotMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public class DryRunOrderExecutor implements OrderExecutor {

    private static final Logger logger = LoggerFactory.getLogger(DryRunOrderExecutor.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final PilotMapper pilotMapper;

    public DryRunOrderExecutor(PilotMapper pilotMapper) {
        this.pilotMapper = pilotMapper;
    }

    @Override
    public OrderResult buy(BuyOrderRequest request) {
        String requestedAt = now();
        if (exists(request.idempotencyKey())) {
            String status = pilotMapper.selectOrderStatusByIdempotencyKey(request.idempotencyKey());
            logger.info("[DRY_RUN_BUY_REUSED] stockCode={}, idempotencyKey={}, status={}",
                    request.stockCode(), request.idempotencyKey(), status);
            return OrderResult.accepted(null, status == null ? "REUSED" : status, "dry-run buy reused by idempotency key");
        }
        logger.info("[DRY_RUN_BUY] stockCode={}, quantity={}, amount={}, price={}, reason={}",
                request.stockCode(), request.orderQuantity(), request.orderAmount(), request.orderPrice(), request.reason());
        pilotMapper.insertOrderRecordDetailed(
                null,
                request.stockCode(),
                "BUY",
                request.orderQuantity().toPlainString(),
                request.orderPrice() == null ? null : request.orderPrice().toPlainString(),
                request.orderAmount().toPlainString(),
                "DRY_RUN",
                null,
                requestedAt,
                request.idempotencyKey(),
                request.decisionCycleId(),
                request.instanceId(),
                request.maskedAccount(),
                null,
                request.exitReason() == null ? null : request.exitReason().name(),
                "Y",
                request.currentPrice() == null ? null : request.currentPrice().toPlainString(),
                request.currentPriceAt() == null ? null : request.currentPriceAt().format(TIME_FORMATTER)
        );
        pilotMapper.insertAuditLog("DRY_RUN_BUY", request.stockCode(), request.toString(), requestedAt);
        return OrderResult.accepted(null, "DRY_RUN", "dry-run buy recorded");
    }

    @Override
    public OrderResult sell(SellOrderRequest request) {
        String requestedAt = now();
        if (exists(request.idempotencyKey())) {
            String status = pilotMapper.selectOrderStatusByIdempotencyKey(request.idempotencyKey());
            logger.info("[DRY_RUN_SELL_REUSED] stockCode={}, idempotencyKey={}, status={}",
                    request.stockCode(), request.idempotencyKey(), status);
            return OrderResult.accepted(null, status == null ? "REUSED" : status, "dry-run sell reused by idempotency key");
        }
        logger.info("[DRY_RUN_SELL] stockCode={}, quantity={}, amount={}, price={}, reason={}",
                request.stockCode(), request.orderQuantity(), request.orderAmount(), request.orderPrice(), request.reason());
        pilotMapper.insertOrderRecordDetailed(
                null,
                request.stockCode(),
                "SELL",
                request.orderQuantity().toPlainString(),
                request.orderPrice() == null ? null : request.orderPrice().toPlainString(),
                request.orderAmount().toPlainString(),
                "DRY_RUN",
                null,
                requestedAt,
                request.idempotencyKey(),
                request.decisionCycleId(),
                request.instanceId(),
                request.maskedAccount(),
                null,
                request.exitReason() == null ? null : request.exitReason().name(),
                "Y",
                request.currentPrice() == null ? null : request.currentPrice().toPlainString(),
                request.currentPriceAt() == null ? null : request.currentPriceAt().format(TIME_FORMATTER)
        );
        pilotMapper.insertAuditLog("DRY_RUN_SELL", request.stockCode(), request.toString(), requestedAt);
        return OrderResult.accepted(null, "DRY_RUN", "dry-run sell recorded");
    }

    private boolean exists(String idempotencyKey) {
        return idempotencyKey != null && !idempotencyKey.isBlank()
                && pilotMapper.countOrderByIdempotencyKey(idempotencyKey) > 0;
    }

    private String now() {
        return OffsetDateTime.now().format(TIME_FORMATTER);
    }
}
