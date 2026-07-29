package com.won.autoinvestor.trading.service;

import com.won.autoinvestor.pilot.mapper.PilotMapper;
import com.won.autoinvestor.trading.domain.LifecycleEventType;
import com.won.autoinvestor.trading.domain.TradeLifecycleEvent;
import com.won.autoinvestor.trading.domain.TradingStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TradeLifecycleHistoryServiceTest {

    @Test
    void lifecycleEventsArePersistedWithIdempotencyKey() {
        AtomicInteger inserts = new AtomicInteger();
        PilotMapper mapper = mapper(inserts);
        TradeLifecycleHistoryService service = new TradeLifecycleHistoryService(mapper);

        service.recordEvent(event(LifecycleEventType.WHITE_TO_GRAY, "life-1:white-gray"));

        assertEquals(1, inserts.get());
    }

    @Test
    void priceEvaluatedEventTypeDoesNotExist() {
        for (LifecycleEventType eventType : LifecycleEventType.values()) {
            assertFalse(eventType.name().contains("PRICE_EVALUATED"));
            assertFalse(eventType.name().contains("DAILY_HIGH_UPDATED"));
            assertFalse(eventType.name().contains("DAILY_LOW_UPDATED"));
        }
    }

    private TradeLifecycleEvent event(LifecycleEventType eventType, String idempotencyKey) {
        return new TradeLifecycleEvent(
                1L,
                eventType,
                TradingStatus.WHITE,
                TradingStatus.GRAY,
                new BigDecimal("990"),
                new BigDecimal("1000"),
                new BigDecimal("1000"),
                new BigDecimal("1010"),
                new BigDecimal("990"),
                BigDecimal.ONE,
                new BigDecimal("-0.01"),
                0,
                "PRICE_DECLINE",
                null,
                null,
                idempotencyKey,
                OffsetDateTime.now()
        );
    }

    private PilotMapper mapper(AtomicInteger inserts) {
        return (PilotMapper) Proxy.newProxyInstance(
                PilotMapper.class.getClassLoader(),
                new Class[]{PilotMapper.class},
                (proxy, method, args) -> {
                    if ("insertTradeLifecycleHistory".equals(method.getName())) {
                        inserts.incrementAndGet();
                        return null;
                    }
                    if (method.getReturnType().isPrimitive()) {
                        return 0;
                    }
                    return null;
                }
        );
    }
}
