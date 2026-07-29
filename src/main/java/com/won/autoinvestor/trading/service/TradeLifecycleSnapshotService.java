package com.won.autoinvestor.trading.service;

import com.won.autoinvestor.pilot.mapper.PilotMapper;
import com.won.autoinvestor.trading.domain.TradeLifecycleSnapshot;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class TradeLifecycleSnapshotService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final PilotMapper pilotMapper;

    public TradeLifecycleSnapshotService(PilotMapper pilotMapper) {
        this.pilotMapper = pilotMapper;
    }

    public void updateCurrentState(TradeLifecycleSnapshot snapshot) {
        String evaluatedAt = time(snapshot.evaluatedAt());
        pilotMapper.updatePositionLifecycleSnapshot(
                snapshot.lifecycleId(),
                snapshot.status().name(),
                amount(snapshot.currentPrice()),
                amount(snapshot.averageBuyPrice()),
                amount(snapshot.referencePrice()),
                amount(snapshot.highestPrice()),
                amount(snapshot.lowestPrice()),
                amount(snapshot.holdingQuantity()),
                amount(snapshot.returnRate()),
                snapshot.grayTradingDays(),
                evaluatedAt,
                evaluatedAt
        );
    }

    private String amount(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private String time(OffsetDateTime value) {
        return (value == null ? OffsetDateTime.now() : value).format(TIME_FORMATTER);
    }
}
