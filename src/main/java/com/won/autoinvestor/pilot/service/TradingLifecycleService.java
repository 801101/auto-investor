package com.won.autoinvestor.pilot.service;

import com.won.autoinvestor.pilot.domain.TradingLifecycleTarget;
import com.won.autoinvestor.pilot.mapper.PilotMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class TradingLifecycleService {

    private static final Logger logger = LoggerFactory.getLogger(TradingLifecycleService.class);
    private static final BigDecimal TEN_PERCENT = new BigDecimal("0.10");
    private static final Duration GRAY_TIMEOUT = Duration.ofDays(3);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final PilotMapper pilotMapper;

    public TradingLifecycleService(PilotMapper pilotMapper) {
        this.pilotMapper = pilotMapper;
    }

    public void logInitialPurchase(String systemType,
                                   String symbol,
                                   String marketCurrency,
                                   BigDecimal buyPrice,
                                   BigDecimal buyQuantity,
                                   BigDecimal buyAmount,
                                   String buyTime) {
        pilotMapper.insertTradingHistoryMaster(
                systemType,
                symbol,
                marketCurrency,
                buyPrice.toPlainString(),
                buyQuantity.toPlainString(),
                buyAmount.toPlainString(),
                buyTime,
                buyTime
        );
        Long masterId = pilotMapper.selectLatestOpenTradingHistoryMasterId(systemType, symbol, buyTime);
        pilotMapper.insertActiveStatusTracker(
                systemType,
                masterId,
                symbol,
                marketCurrency,
                buyPrice.toPlainString(),
                buyQuantity.toPlainString(),
                buyAmount.toPlainString(),
                buyTime,
                buyTime
        );
        pilotMapper.insertTradingStatusHistoryLog(
                null,
                masterId,
                systemType,
                symbol,
                null,
                "WHITE",
                "BUY_EVENT",
                "MASTER_AND_TRACKER_INSERTED",
                "N",
                buyTime
        );
    }

    @Transactional
    public void rotateStatuses() {
        List<TradingLifecycleTarget> targets = pilotMapper.selectLifecycleTargetsWithLatestTicks();
        if (targets == null || targets.isEmpty()) {
            logger.warn("status rotation skipped: linked market data or active tracker is empty");
            return;
        }

        String rotatedAt = now();
        for (TradingLifecycleTarget target : targets) {
            if (!isValidTarget(target)) {
                logger.warn("status rotation skipped invalid target: symbol={}", target == null ? null : target.getSymbol());
                continue;
            }
            rotateOne(target, rotatedAt);
        }
    }

    @Transactional
    public void liquidateBlackAtMarketStart() {
        List<TradingLifecycleTarget> targets = pilotMapper.selectBlackLifecycleTargetsWithLatestTicks();
        if (targets == null || targets.isEmpty()) {
            logger.warn("market start liquidation skipped: BLACK tracker with linked market data is empty");
            return;
        }

        String soldAt = now();
        for (TradingLifecycleTarget target : targets) {
            if (!isValidTarget(target)) {
                logger.warn("black liquidation skipped invalid target: symbol={}", target == null ? null : target.getSymbol());
                continue;
            }
            if (hasIntegrityMismatch(target)) {
                String reason = integrityMismatchReason(target);
                pilotMapper.markActiveStatusBadSector(target.getId(), soldAt, reason);
                pilotMapper.insertTradingStatusHistoryLog(
                        target.getId(),
                        target.getMasterId(),
                        target.getSystemType(),
                        target.getSymbol(),
                        target.getStatus(),
                        "BAD_SECTOR",
                        "INTEGRITY_CHECK",
                        reason,
                        forceFlag(target),
                        soldAt
                );
                continue;
            }
            BigDecimal pnlRatio = target.getLastPrice()
                    .subtract(target.getEntryPrice())
                    .divide(target.getEntryPrice(), 8, RoundingMode.HALF_UP);
            pilotMapper.updateTradingHistorySell(
                    target.getSystemType(),
                    target.getMasterId(),
                    target.getSymbol(),
                    target.getLastPrice().toPlainString(),
                    soldAt,
                    pnlRatio.toPlainString(),
                    forceFlag(target)
            );
            pilotMapper.insertTrainingDatasetFromMaster(target.getMasterId(), soldAt);
            pilotMapper.insertTradingStatusHistoryLog(
                    target.getId(),
                    target.getMasterId(),
                    target.getSystemType(),
                    target.getSymbol(),
                    target.getStatus(),
                    "CLOSED",
                    "SELL_EVENT",
                    "BLACK_MARKET_START_LIQUIDATION",
                    forceFlag(target),
                    soldAt
            );
            if ("PILOT".equalsIgnoreCase(target.getSystemType())) {
                pilotMapper.closePilotHolding(target.getSymbol(), soldAt);
                pilotMapper.closePilotBalance(target.getSymbol(), soldAt);
            } else if ("AUTOBOT".equalsIgnoreCase(target.getSystemType())) {
                pilotMapper.closeAutobotHolding(target.getSymbol(), soldAt);
            }
            pilotMapper.deleteActiveStatusTracker(target.getId());
        }
    }

    private void rotateOne(TradingLifecycleTarget target, String rotatedAt) {
        BigDecimal ratio = target.getLastPrice()
                .subtract(target.getEntryPrice())
                .divide(target.getEntryPrice(), 8, RoundingMode.HALF_UP);
        BigDecimal absoluteRatio = ratio.abs();

        if (hasIntegrityMismatch(target)) {
            String reason = integrityMismatchReason(target);
            pilotMapper.markActiveStatusBadSector(target.getId(), rotatedAt, reason);
            pilotMapper.insertTradingStatusHistoryLog(
                    target.getId(),
                    target.getMasterId(),
                    target.getSystemType(),
                    target.getSymbol(),
                    target.getStatus(),
                    "BAD_SECTOR",
                    "INTEGRITY_CHECK",
                    reason,
                    forceFlag(target),
                    rotatedAt
            );
            return;
        }

        if (absoluteRatio.compareTo(TEN_PERCENT) >= 0) {
            transition(target, "BLACK", rotatedAt, target.getGrayEnteredAt(), "Y", rotatedAt,
                    "CRITICAL_EXCEPTION", "PRICE_DEVIATION_10_PERCENT_FORCE_LIQUIDATION");
            return;
        }

        if ("GRAY".equals(target.getStatus()) && isGrayExpired(target, rotatedAt)) {
            transition(target, "BLACK", rotatedAt, target.getGrayEnteredAt(), forceFlag(target), rotatedAt,
                    "GRAY_TIMEOUT", "GRAY_TIMEOUT_EXCEEDED_3_DAYS");
            pilotMapper.insertAssetGradeDecision(
                    target.getSymbol(),
                    "BLACK",
                    "GRAY_TIMEOUT_EXCEEDED_3_DAYS",
                    rotatedAt
            );
            return;
        }

        if ("WHITE".equals(target.getStatus()) && ratio.signum() < 0) {
            transition(target, "GRAY", rotatedAt, rotatedAt, forceFlag(target), rotatedAt,
                    "STANDARD_ROTATION", "NEGATIVE_DRIFT_ENTER_GRAY_MONITORING");
            return;
        }

        if ("GRAY".equals(target.getStatus()) && ratio.signum() >= 0) {
            transition(target, "WHITE", rotatedAt, null, forceFlag(target), rotatedAt,
                    "STANDARD_ROTATION", "PRICE_RECOVERED_EXIT_GRAY_MONITORING");
            return;
        }

        transition(target, target.getStatus(), target.getStatusEnteredAt(), target.getGrayEnteredAt(), forceFlag(target), rotatedAt,
                "BATCH_HEARTBEAT", "NO_STATUS_CHANGE");
    }

    private void transition(TradingLifecycleTarget target,
                            String newStatus,
                            String statusEnteredAt,
                            String grayEnteredAt,
                            String forceLiquidationFlag,
                            String updatedAt,
                            String eventType,
                            String reason) {
        pilotMapper.updateActiveStatus(
                target.getId(),
                newStatus,
                statusEnteredAt,
                grayEnteredAt,
                forceLiquidationFlag,
                updatedAt
        );

        if (!newStatus.equals(target.getStatus()) || "Y".equals(forceLiquidationFlag) && !"Y".equals(forceFlag(target))) {
            pilotMapper.insertTradingStatusHistoryLog(
                    target.getId(),
                    target.getMasterId(),
                    target.getSystemType(),
                    target.getSymbol(),
                    target.getStatus(),
                    newStatus,
                    eventType,
                    reason,
                    forceLiquidationFlag,
                    updatedAt
            );
        }
    }

    private boolean isGrayExpired(TradingLifecycleTarget target, String rotatedAt) {
        if (target.getGrayEnteredAt() == null || target.getGrayEnteredAt().isBlank()) {
            return false;
        }
        try {
            OffsetDateTime grayEnteredAt = OffsetDateTime.parse(target.getGrayEnteredAt(), TIME_FORMATTER);
            OffsetDateTime now = OffsetDateTime.parse(rotatedAt, TIME_FORMATTER);
            return Duration.between(grayEnteredAt, now).compareTo(GRAY_TIMEOUT) >= 0;
        } catch (RuntimeException e) {
            logger.warn("failed to parse gray_entered_at: symbol={}, grayEnteredAt={}", target.getSymbol(), target.getGrayEnteredAt());
            return false;
        }
    }

    private boolean isValidTarget(TradingLifecycleTarget target) {
        return target != null
                && hasText(target.getSystemType())
                && hasText(target.getSymbol())
                && target.getId() != null
                && target.getEntryPrice() != null
                && target.getEntryPrice().signum() > 0
                && target.getLastPrice() != null
                && target.getLastPrice().signum() > 0;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean hasIntegrityMismatch(TradingLifecycleTarget target) {
        return target.getMasterId() == null
                || !hasText(target.getMasterStatus())
                || !"OPEN".equals(target.getMasterStatus())
                || hasText(target.getMasterSellTime());
    }

    private String integrityMismatchReason(TradingLifecycleTarget target) {
        if (target.getMasterId() == null) {
            return "MASTER_RECORD_NOT_FOUND";
        }
        if (!hasText(target.getMasterStatus())) {
            return "MASTER_STATUS_EMPTY";
        }
        if (!"OPEN".equals(target.getMasterStatus())) {
            return "MASTER_STATUS_NOT_OPEN_" + target.getMasterStatus();
        }
        if (hasText(target.getMasterSellTime())) {
            return "MASTER_ALREADY_HAS_SELL_TIME";
        }
        return "TRACKER_MASTER_STATE_MISMATCH";
    }

    private String forceFlag(TradingLifecycleTarget target) {
        return "Y".equalsIgnoreCase(target.getForceLiquidationFlag()) ? "Y" : "N";
    }

    private String now() {
        return OffsetDateTime.now().format(TIME_FORMATTER);
    }
}
