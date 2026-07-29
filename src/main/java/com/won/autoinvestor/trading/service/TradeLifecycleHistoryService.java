package com.won.autoinvestor.trading.service;

import com.won.autoinvestor.pilot.mapper.PilotMapper;
import com.won.autoinvestor.trading.domain.LifecycleEventType;
import com.won.autoinvestor.trading.domain.TradeLifecycleEvent;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class TradeLifecycleHistoryService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final PilotMapper pilotMapper;

    public TradeLifecycleHistoryService(PilotMapper pilotMapper) {
        this.pilotMapper = pilotMapper;
    }

    public void recordEvent(TradeLifecycleEvent event) {
        if (!shouldPersist(event.eventType())) {
            return;
        }
        String key = event.idempotencyKey();
        if (key == null || key.isBlank()) {
            key = defaultIdempotencyKey(event);
        }
        pilotMapper.insertTradeLifecycleHistory(
                event.lifecycleId(),
                event.eventType().name(),
                event.previousState() == null ? null : event.previousState().name(),
                event.newState() == null ? null : event.newState().name(),
                amount(event.currentPrice()),
                amount(event.averageBuyPrice()),
                amount(event.referencePrice()),
                amount(event.highestPrice()),
                amount(event.lowestPrice()),
                amount(event.holdingQuantity()),
                amount(event.returnRate()),
                event.grayTradingDays(),
                event.reason(),
                event.orderId(),
                event.executionId(),
                key,
                event.occurredAt() == null ? OffsetDateTime.now().format(TIME_FORMATTER) : event.occurredAt().format(TIME_FORMATTER)
        );
    }

    private boolean shouldPersist(LifecycleEventType eventType) {
        return eventType != null;
    }

    private String defaultIdempotencyKey(TradeLifecycleEvent event) {
        return event.lifecycleId() + ":" + event.eventType() + ":" + nullable(event.newState())
                + ":" + nullable(event.grayTradingDays()) + ":" + nullable(event.orderId()) + ":" + nullable(event.executionId());
    }

    private String amount(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private String nullable(Object value) {
        return value == null ? "" : value.toString();
    }
}
