package com.won.autoinvestor.trading.order;

import com.won.autoinvestor.broker.domain.AccountBalance;
import com.won.autoinvestor.trading.config.InvestmentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class BuyCandidateSelectionService {

    private static final Logger logger = LoggerFactory.getLogger(BuyCandidateSelectionService.class);

    private final InvestmentProperties investmentProperties;
    private final OrderSizingService orderSizingService;

    public BuyCandidateSelectionService(InvestmentProperties investmentProperties, OrderSizingService orderSizingService) {
        this.investmentProperties = investmentProperties;
        this.orderSizingService = orderSizingService;
    }

    public BuyCandidateSelectionResult selectBestBuyableCandidate(List<BuyCandidate> candidates,
                                                                  AccountBalance accountBalance,
                                                                  BigDecimal currentTotalHoldingQuantity) {
        if (candidates == null || candidates.isEmpty()) {
            return BuyCandidateSelectionResult.none("NO_CANDIDATES");
        }
        if (accountBalance == null || accountBalance.cashBalance() == null || accountBalance.cashBalance().signum() <= 0) {
            return BuyCandidateSelectionResult.none("BALANCE_UNAVAILABLE");
        }

        Set<String> checked = new HashSet<>();
        List<BuyCandidate> sorted = candidates.stream()
                .sorted(Comparator.comparing(BuyCandidate::score, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(BuyCandidate::stockCode))
                .toList();

        for (BuyCandidate candidate : sorted) {
            if (!checked.add(candidate.stockCode())) {
                continue;
            }

            String exclusionReason = exclusionReason(candidate);
            if (exclusionReason != null) {
                logger.info("[BUY_CANDIDATE_EXCLUDED] stockCode={}, reason={}", candidate.stockCode(), exclusionReason);
                continue;
            }

            OrderSizingResult sizingResult = orderSizingService.calculateBuyQuantity(
                    candidate.currentPrice(),
                    accountBalance,
                    currentTotalHoldingQuantity
            );
            if (!sizingResult.orderable()) {
                logger.info("[BUY_CANDIDATE_EXCLUDED] stockCode={}, reason={}", candidate.stockCode(), sizingResult.reason());
                continue;
            }

            logger.info("[BUY_CANDIDATE_SELECTED] stockCode={}, score={}, quantity={}, expectedAmount={}",
                    candidate.stockCode(), candidate.score(), sizingResult.quantity(), sizingResult.expectedAmount());
            return BuyCandidateSelectionResult.selected(candidate, sizingResult);
        }

        return BuyCandidateSelectionResult.none("NO_BUYABLE_CANDIDATE");
    }

    private String exclusionReason(BuyCandidate candidate) {
        if (candidate == null || candidate.stockCode() == null || candidate.stockCode().isBlank()) {
            return "INVALID_CANDIDATE";
        }
        if (!candidate.tradable()) {
            return "NOT_TRADABLE";
        }
        if (!investmentProperties.isAllowDuplicateStock() && candidate.alreadyHeld()) {
            return "DUPLICATE_NOT_ALLOWED";
        }
        return null;
    }
}
