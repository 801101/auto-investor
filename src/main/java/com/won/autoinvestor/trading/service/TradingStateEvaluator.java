package com.won.autoinvestor.trading.service;

import com.won.autoinvestor.trading.config.InvestmentProperties;
import com.won.autoinvestor.trading.domain.TradingEvaluationContext;
import com.won.autoinvestor.trading.domain.TradingEvaluationResult;
import com.won.autoinvestor.trading.domain.TradingStatus;
import org.springframework.stereotype.Component;

@Component
public class TradingStateEvaluator {

    private final InvestmentProperties investmentProperties;

    public TradingStateEvaluator(InvestmentProperties investmentProperties) {
        this.investmentProperties = investmentProperties;
    }

    public TradingEvaluationResult evaluate(TradingEvaluationContext context) {
        if (context.getStatus() == TradingStatus.BLACK) {
            return new TradingEvaluationResult(TradingStatus.BLACK, "BLACK_SELL_ONLY");
        }
        if (context.getStatus() == TradingStatus.WHITE) {
            return evaluateWhite(context);
        }
        return evaluateGray(context);
    }

    private TradingEvaluationResult evaluateWhite(TradingEvaluationContext context) {
        if (context.getProfitRate().compareTo(investmentProperties.getTakeProfitRate()) >= 0) {
            return new TradingEvaluationResult(TradingStatus.BLACK, "TAKE_PROFIT");
        }
        if (context.getCurrentPrice().compareTo(context.getLastEvaluatedPrice()) < 0) {
            return new TradingEvaluationResult(TradingStatus.GRAY, "PRICE_DECLINE");
        }
        return new TradingEvaluationResult(TradingStatus.WHITE, "WHITE_KEEP");
    }

    private TradingEvaluationResult evaluateGray(TradingEvaluationContext context) {
        if (context.getGrayTradingDays() >= investmentProperties.getGrayMaxTradingDays()) {
            return new TradingEvaluationResult(TradingStatus.BLACK, "GRAY_TRADING_DAY_TIMEOUT");
        }
        if (context.getCurrentPrice().compareTo(context.getLastEvaluatedPrice()) > 0
                && context.getCurrentValuationAmount().compareTo(context.getInvestedAmount()) >= 0) {
            return new TradingEvaluationResult(TradingStatus.WHITE, "GRAY_RECOVERED");
        }
        return new TradingEvaluationResult(TradingStatus.GRAY, "GRAY_KEEP");
    }

    public TradingEvaluationResult evaluateUntilStable(TradingEvaluationContext context) {
        TradingEvaluationContext currentContext = context;
        TradingEvaluationResult lastResult = new TradingEvaluationResult(context.getStatus(), "INITIAL");

        for (int i = 0; i < 3; i++) {
            TradingEvaluationResult next = evaluate(currentContext);
            lastResult = next;
            if (next.getStatus() == currentContext.getStatus() || next.getStatus() == TradingStatus.BLACK) {
                break;
            }
            currentContext = currentContext.withStatus(next.getStatus());
        }

        return lastResult;
    }
}
