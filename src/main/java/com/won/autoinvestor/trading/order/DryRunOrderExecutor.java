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
        logger.info("[DRY_RUN_BUY] stockCode={}, quantity={}, amount={}, price={}, reason={}",
                request.stockCode(), request.orderQuantity(), request.orderAmount(), request.orderPrice(), request.reason());
        pilotMapper.insertOrderRecord(
                null,
                request.stockCode(),
                "BUY",
                request.orderQuantity().toPlainString(),
                request.orderPrice() == null ? null : request.orderPrice().toPlainString(),
                request.orderAmount().toPlainString(),
                "DRY_RUN",
                null,
                requestedAt
        );
        pilotMapper.insertAuditLog("DRY_RUN_BUY", request.stockCode(), request.toString(), requestedAt);
        return OrderResult.accepted(null, "DRY_RUN", "dry-run buy recorded");
    }

    @Override
    public OrderResult sell(SellOrderRequest request) {
        String requestedAt = now();
        logger.info("[DRY_RUN_SELL] stockCode={}, quantity={}, amount={}, price={}, reason={}",
                request.stockCode(), request.orderQuantity(), request.orderAmount(), request.orderPrice(), request.reason());
        pilotMapper.insertOrderRecord(
                null,
                request.stockCode(),
                "SELL",
                request.orderQuantity().toPlainString(),
                request.orderPrice() == null ? null : request.orderPrice().toPlainString(),
                request.orderAmount().toPlainString(),
                "DRY_RUN",
                null,
                requestedAt
        );
        pilotMapper.insertAuditLog("DRY_RUN_SELL", request.stockCode(), request.toString(), requestedAt);
        return OrderResult.accepted(null, "DRY_RUN", "dry-run sell recorded");
    }

    private String now() {
        return OffsetDateTime.now().format(TIME_FORMATTER);
    }
}
