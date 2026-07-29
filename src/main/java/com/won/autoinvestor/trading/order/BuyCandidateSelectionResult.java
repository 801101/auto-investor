package com.won.autoinvestor.trading.order;

public record BuyCandidateSelectionResult(boolean selected,
                                          BuyCandidate candidate,
                                          OrderSizingResult sizingResult,
                                          String reason) {

    public static BuyCandidateSelectionResult selected(BuyCandidate candidate, OrderSizingResult sizingResult) {
        return new BuyCandidateSelectionResult(true, candidate, sizingResult, "SELECTED");
    }

    public static BuyCandidateSelectionResult none(String reason) {
        return new BuyCandidateSelectionResult(false, null, null, reason);
    }
}
