package com.won.autoinvestor.pilot.domain;

import java.math.BigDecimal;

public class BudgetAllocation {

    private final String marketCurrency;
    private final BigDecimal totalWalletBalance;
    private final BigDecimal pilotBudget;
    private final BigDecimal autobotBudget;
    private final BigDecimal pilotUsedAmount;
    private final BigDecimal autobotUsedAmount;
    private final BigDecimal pilotAvailableAmount;
    private final BigDecimal autobotAvailableAmount;

    public BudgetAllocation(String marketCurrency,
                            BigDecimal totalWalletBalance,
                            BigDecimal pilotBudget,
                            BigDecimal autobotBudget,
                            BigDecimal pilotUsedAmount,
                            BigDecimal autobotUsedAmount) {
        this.marketCurrency = marketCurrency;
        this.totalWalletBalance = totalWalletBalance;
        this.pilotBudget = pilotBudget;
        this.autobotBudget = autobotBudget;
        this.pilotUsedAmount = pilotUsedAmount;
        this.autobotUsedAmount = autobotUsedAmount;
        this.pilotAvailableAmount = pilotBudget.subtract(pilotUsedAmount);
        this.autobotAvailableAmount = autobotBudget.subtract(autobotUsedAmount);
    }

    public String getMarketCurrency() {
        return marketCurrency;
    }

    public BigDecimal getTotalWalletBalance() {
        return totalWalletBalance;
    }

    public BigDecimal getPilotBudget() {
        return pilotBudget;
    }

    public BigDecimal getAutobotBudget() {
        return autobotBudget;
    }

    public BigDecimal getPilotUsedAmount() {
        return pilotUsedAmount;
    }

    public BigDecimal getAutobotUsedAmount() {
        return autobotUsedAmount;
    }

    public BigDecimal getPilotAvailableAmount() {
        return pilotAvailableAmount;
    }

    public BigDecimal getAutobotAvailableAmount() {
        return autobotAvailableAmount;
    }
}
