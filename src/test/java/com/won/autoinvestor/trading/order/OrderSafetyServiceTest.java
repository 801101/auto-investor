package com.won.autoinvestor.trading.order;

import com.won.autoinvestor.broker.domain.AccountBalance;
import com.won.autoinvestor.pilot.mapper.PilotMapper;
import com.won.autoinvestor.trading.config.InvestmentProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderSafetyServiceTest {

    @Test
    void buyBlockedWhenLiveMarketClosed() {
        OrderSafetyResult result = service(0, 0, 0, 0)
                .validateBuy("005930", BigDecimal.ONE, new BigDecimal("1000"), balance("10000"), false);

        assertFalse(result.orderAllowed());
        assertEquals("MARKET_CLOSED", result.reason());
    }

    @Test
    void buyBlockedWhenDuplicateOrderExists() {
        OrderSafetyResult result = service(0, 1, 0, 0)
                .validateBuy("005930", BigDecimal.ONE, new BigDecimal("1000"), balance("10000"), true);

        assertFalse(result.orderAllowed());
        assertEquals("DUPLICATE_OPEN_ORDER", result.reason());
    }

    @Test
    void buyBlockedWhenCashIsInsufficient() {
        OrderSafetyResult result = service(0, 0, 0, 0)
                .validateBuy("005930", BigDecimal.ONE, new BigDecimal("1000"), balance("999"), true);

        assertFalse(result.orderAllowed());
        assertEquals("INSUFFICIENT_CASH", result.reason());
    }

    @Test
    void buyAllowedWhenSafetyChecksPass() {
        OrderSafetyResult result = service(0, 0, 0, 0)
                .validateBuy("005930", BigDecimal.ONE, new BigDecimal("1000"), balance("1000"), true);

        assertTrue(result.orderAllowed());
    }

    @Test
    void sellBlackBlockedWhenDuplicateOrderExists() {
        OrderSafetyResult result = service(0, 1, 0, 0)
                .validateSell("005930", BigDecimal.ONE);

        assertFalse(result.orderAllowed());
        assertEquals("DUPLICATE_OPEN_ORDER", result.reason());
    }

    private OrderSafetyService service(int activePositionCount,
                                       int openOrderCount,
                                       int activePositions,
                                       int maxHoldings) {
        InvestmentProperties properties = new InvestmentProperties();
        properties.setMaxHoldings(maxHoldings);
        PilotMapper mapper = (PilotMapper) Proxy.newProxyInstance(
                PilotMapper.class.getClassLoader(),
                new Class[]{PilotMapper.class},
                (proxy, method, args) -> {
                    if ("countActivePositionByStockCode".equals(method.getName())) {
                        return activePositionCount;
                    }
                    if ("countOpenOrderByStockCode".equals(method.getName())) {
                        return openOrderCount;
                    }
                    if ("countActivePositions".equals(method.getName())) {
                        return activePositions;
                    }
                    if (method.getReturnType().isPrimitive()) {
                        return 0;
                    }
                    return null;
                }
        );
        return new OrderSafetyService(mapper, properties);
    }

    private AccountBalance balance(String cashBalance) {
        return new AccountBalance(new BigDecimal(cashBalance), new BigDecimal(cashBalance));
    }
}
