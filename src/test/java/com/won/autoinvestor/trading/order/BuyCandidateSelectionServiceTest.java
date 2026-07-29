package com.won.autoinvestor.trading.order;

import com.won.autoinvestor.broker.domain.AccountBalance;
import com.won.autoinvestor.broker.domain.CurrentPrice;
import com.won.autoinvestor.trading.config.InvestmentProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuyCandidateSelectionServiceTest {

    @Test
    void priorityIsReassignedAfterUnbuyableCandidateIsExcluded() {
        InvestmentProperties properties = new InvestmentProperties();
        properties.setUnitAmount(new BigDecimal("10000"));
        BuyCandidateSelectionService service = service(properties);

        BuyCandidateSelectionResult result = service.selectBestBuyableCandidate(
                List.of(
                        candidate("A", "95", "20000", false),
                        candidate("B", "90", "8000", false),
                        candidate("C", "85", "5000", false)
                ),
                balance("10000"),
                BigDecimal.ZERO
        );

        assertTrue(result.selected());
        assertEquals("B", result.candidate().stockCode());
    }

    @Test
    void allUnbuyableCandidatesEndCycleNormally() {
        InvestmentProperties properties = new InvestmentProperties();
        properties.setUnitAmount(new BigDecimal("1000"));
        BuyCandidateSelectionService service = service(properties);

        BuyCandidateSelectionResult result = service.selectBestBuyableCandidate(
                List.of(
                        candidate("A", "95", "20000", false),
                        candidate("B", "90", "8000", false)
                ),
                balance("1000"),
                BigDecimal.ZERO
        );

        assertFalse(result.selected());
        assertEquals("NO_BUYABLE_CANDIDATE", result.reason());
    }

    @Test
    void duplicateHoldingIsExcludedWhenDuplicateBuyIsDisabled() {
        InvestmentProperties properties = new InvestmentProperties();
        properties.setAllowDuplicateStock(false);
        BuyCandidateSelectionService service = service(properties);

        BuyCandidateSelectionResult result = service.selectBestBuyableCandidate(
                List.of(
                        candidate("A", "95", "100", true),
                        candidate("B", "90", "100", false)
                ),
                balance("10000"),
                BigDecimal.ZERO
        );

        assertTrue(result.selected());
        assertEquals("B", result.candidate().stockCode());
    }

    @Test
    void duplicateHoldingCanBeSelectedWhenDuplicateBuyIsEnabled() {
        InvestmentProperties properties = new InvestmentProperties();
        properties.setAllowDuplicateStock(true);
        BuyCandidateSelectionService service = service(properties);

        BuyCandidateSelectionResult result = service.selectBestBuyableCandidate(
                List.of(candidate("A", "95", "100", true)),
                balance("10000"),
                BigDecimal.ZERO
        );

        assertTrue(result.selected());
        assertEquals("A", result.candidate().stockCode());
        assertEquals(new BigDecimal("10"), result.sizingResult().quantity());
    }

    @Test
    void duplicateCandidateCodeIsCheckedOnlyOncePerCycle() {
        InvestmentProperties properties = new InvestmentProperties();
        properties.setUnitAmount(new BigDecimal("1000"));
        BuyCandidateSelectionService service = service(properties);

        BuyCandidateSelectionResult result = service.selectBestBuyableCandidate(
                List.of(
                        candidate("A", "95", "2000", false),
                        candidate("A", "94", "100", false),
                        candidate("B", "90", "100", false)
                ),
                balance("1000"),
                BigDecimal.ZERO
        );

        assertTrue(result.selected());
        assertEquals("B", result.candidate().stockCode());
    }

    private BuyCandidateSelectionService service(InvestmentProperties properties) {
        return new BuyCandidateSelectionService(properties, new OrderSizingService(properties));
    }

    private BuyCandidate candidate(String stockCode, String score, String price, boolean alreadyHeld) {
        return new BuyCandidate(
                stockCode,
                new BigDecimal(score),
                new CurrentPrice(stockCode, new BigDecimal(price), OffsetDateTime.now()),
                alreadyHeld,
                true
        );
    }

    private AccountBalance balance(String value) {
        return new AccountBalance(new BigDecimal(value), new BigDecimal(value));
    }
}
