package com.won.autoinvestor.trading.service;

import com.won.autoinvestor.trading.config.InvestmentProperties;
import com.won.autoinvestor.trading.domain.ExitReason;
import com.won.autoinvestor.trading.domain.TradingEvaluationContext;
import com.won.autoinvestor.trading.domain.TradingEvaluationResult;
import com.won.autoinvestor.trading.domain.TradingStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TradingStateEvaluatorTest {

    private final TradingStateEvaluator evaluator = new TradingStateEvaluator(new InvestmentProperties());

    @Test
    void whiteKeepsWhenPriceRises() {
        assertEquals(TradingStatus.WHITE, evaluate(TradingStatus.WHITE, "1000", "1010", "1000", "1010", "0.01", 0));
    }

    @Test
    void whiteKeepsWhenPriceIsSame() {
        assertEquals(TradingStatus.WHITE, evaluate(TradingStatus.WHITE, "1000", "1000", "1000", "1000", "0.00", 0));
    }

    @Test
    void whiteMovesToGrayWhenPriceDeclines() {
        assertEquals(TradingStatus.GRAY, evaluate(TradingStatus.WHITE, "1000", "990", "1000", "990", "-0.01", 0));
    }

    @Test
    void whiteMovesToBlackWhenTakeProfitReached() {
        assertEquals(TradingStatus.BLACK, evaluate(TradingStatus.WHITE, "1000", "1100", "1000", "1100", "0.10", 0));
    }

    @Test
    void whiteMovesToBlackWhenStopLossEnabledAndReached() {
        InvestmentProperties properties = new InvestmentProperties();
        properties.getStopLoss().setEnabled(true);
        TradingStateEvaluator stopLossEvaluator = new TradingStateEvaluator(properties);

        TradingEvaluationResult result = stopLossEvaluator.evaluate(
                context(TradingStatus.WHITE, "1000", "900", "1000", "900", "-0.10", 0));

        assertEquals(TradingStatus.BLACK, result.getStatus());
        assertEquals(ExitReason.STOP_LOSS, result.getExitReason());
    }

    @Test
    void grayMovesToBlackWhenStopLossEnabledAndReached() {
        InvestmentProperties properties = new InvestmentProperties();
        properties.getStopLoss().setEnabled(true);
        TradingStateEvaluator stopLossEvaluator = new TradingStateEvaluator(properties);

        TradingEvaluationResult result = stopLossEvaluator.evaluate(
                context(TradingStatus.GRAY, "1000", "900", "1000", "900", "-0.10", 1));

        assertEquals(TradingStatus.BLACK, result.getStatus());
        assertEquals(ExitReason.STOP_LOSS, result.getExitReason());
    }

    @Test
    void whiteTakeProfitWinsOverPriceDecline() {
        assertEquals(TradingStatus.BLACK, evaluate(TradingStatus.WHITE, "1200", "1100", "1000", "1100", "0.10", 0));
    }

    @Test
    void grayKeepsBeforeTimeoutWhenNotRecovered() {
        assertEquals(TradingStatus.GRAY, evaluate(TradingStatus.GRAY, "1000", "990", "1000", "990", "-0.01", 2));
    }

    @Test
    void grayMovesToBlackOnTradingDayTimeout() {
        TradingEvaluationResult result = evaluator.evaluate(context(TradingStatus.GRAY, "990", "1000", "1000", "1000", "0.00", 3));
        assertEquals(TradingStatus.BLACK, result.getStatus());
        assertEquals(ExitReason.GRAY_TIMEOUT, result.getExitReason());
    }

    @Test
    void grayMovesToWhiteWhenPriceRisesAndPrincipalRecovered() {
        assertEquals(TradingStatus.WHITE, evaluate(TradingStatus.GRAY, "990", "1001", "1000", "1001", "0.001", 1));
    }

    @Test
    void grayKeepsWhenPriceRisesButPrincipalNotRecovered() {
        assertEquals(TradingStatus.GRAY, evaluate(TradingStatus.GRAY, "990", "995", "1000", "999", "-0.001", 1));
    }

    @Test
    void grayKeepsWhenPrincipalRecoveredButPriceDidNotRise() {
        assertEquals(TradingStatus.GRAY, evaluate(TradingStatus.GRAY, "1000", "1000", "1000", "1000", "0.00", 1));
    }

    @Test
    void graySurgeMovesToWhiteFirst() {
        assertEquals(TradingStatus.WHITE, evaluate(TradingStatus.GRAY, "990", "1111", "1000", "1111", "0.111", 1));
    }

    @Test
    void repeatedEvaluationCanMoveGrayWhiteBlack() {
        TradingEvaluationResult result = evaluator.evaluateUntilStable(context(TradingStatus.GRAY, "990", "1111", "1000", "1111", "0.111", 1));
        assertEquals(TradingStatus.BLACK, result.getStatus());
    }

    @Test
    void repeatedEvaluationMovesWhiteGray() {
        TradingEvaluationResult result = evaluator.evaluateUntilStable(context(TradingStatus.WHITE, "1000", "990", "1000", "990", "-0.01", 0));
        assertEquals(TradingStatus.GRAY, result.getStatus());
    }

    @Test
    void repeatedEvaluationMovesWhiteBlack() {
        TradingEvaluationResult result = evaluator.evaluateUntilStable(context(TradingStatus.WHITE, "1000", "1100", "1000", "1100", "0.10", 0));
        assertEquals(TradingStatus.BLACK, result.getStatus());
    }

    @Test
    void repeatedEvaluationStopsAtBlack() {
        TradingEvaluationResult result = evaluator.evaluateUntilStable(context(TradingStatus.BLACK, "1000", "900", "1000", "900", "-0.10", 0));
        assertEquals(TradingStatus.BLACK, result.getStatus());
    }

    private TradingStatus evaluate(TradingStatus status,
                                   String lastEvaluatedPrice,
                                   String currentPrice,
                                   String investedAmount,
                                   String valuationAmount,
                                   String profitRate,
                                   long grayTradingDays) {
        return evaluator.evaluate(context(status, lastEvaluatedPrice, currentPrice, investedAmount, valuationAmount, profitRate, grayTradingDays))
                .getStatus();
    }

    private TradingEvaluationContext context(TradingStatus status,
                                             String lastEvaluatedPrice,
                                             String currentPrice,
                                             String investedAmount,
                                             String valuationAmount,
                                             String profitRate,
                                             long grayTradingDays) {
        return new TradingEvaluationContext(
                status,
                new BigDecimal("1000"),
                new BigDecimal(lastEvaluatedPrice),
                new BigDecimal(currentPrice),
                new BigDecimal(investedAmount),
                new BigDecimal(valuationAmount),
                new BigDecimal(profitRate),
                grayTradingDays
        );
    }
}
