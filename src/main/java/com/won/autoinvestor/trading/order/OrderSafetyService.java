package com.won.autoinvestor.trading.order;

import com.won.autoinvestor.broker.domain.AccountBalance;
import com.won.autoinvestor.pilot.mapper.PilotMapper;
import com.won.autoinvestor.trading.config.InvestmentProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class OrderSafetyService {

    private final PilotMapper pilotMapper;
    private final InvestmentProperties investmentProperties;

    public OrderSafetyService(PilotMapper pilotMapper, InvestmentProperties investmentProperties) {
        this.pilotMapper = pilotMapper;
        this.investmentProperties = investmentProperties;
    }

    public OrderSafetyResult validateBuy(String stockCode,
                                         BigDecimal orderQuantity,
                                         BigDecimal orderAmount,
                                         AccountBalance accountBalance,
                                         boolean marketOpen) {
        if (!marketOpen) {
            return OrderSafetyResult.blocked("MARKET_CLOSED");
        }
        if (orderQuantity == null || orderQuantity.signum() <= 0) {
            return OrderSafetyResult.blocked("ZERO_QUANTITY");
        }
        if (orderAmount == null || accountBalance == null || accountBalance.cashBalance().compareTo(orderAmount) < 0) {
            return OrderSafetyResult.blocked("INSUFFICIENT_CASH");
        }
        if (investmentProperties.getMaxHoldings() > 0
                && pilotMapper.countActivePositions() >= investmentProperties.getMaxHoldings()) {
            return OrderSafetyResult.blocked("MAX_HOLDINGS_REACHED");
        }
        if (investmentProperties.getMaxPerStock() <= 1 && pilotMapper.countActivePositionByStockCode(stockCode) > 0) {
            return OrderSafetyResult.blocked("DUPLICATE_HOLDING");
        }
        if (pilotMapper.countOpenOrderByStockCode(stockCode) > 0) {
            return OrderSafetyResult.blocked("DUPLICATE_OPEN_ORDER");
        }
        return OrderSafetyResult.allowed();
    }

    public OrderSafetyResult validateSell(String stockCode, BigDecimal orderQuantity) {
        if (orderQuantity == null || orderQuantity.signum() <= 0) {
            return OrderSafetyResult.blocked("ZERO_QUANTITY");
        }
        if (pilotMapper.countOpenOrderByStockCode(stockCode) > 0) {
            return OrderSafetyResult.blocked("DUPLICATE_OPEN_ORDER");
        }
        return OrderSafetyResult.allowed();
    }
}
