package com.won.autoinvestor.pilot.service;

import com.won.autoinvestor.pilot.domain.BudgetAllocation;
import com.won.autoinvestor.pilot.mapper.PilotMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class DynamicBudgetAllocationService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PilotMapper pilotMapper;
    private final BigDecimal pilotAllocationRatio;
    private final BigDecimal totalWalletBalanceKrw;
    private final BigDecimal totalWalletBalanceUsd;
    private final BigDecimal minimumPilotBudgetKrw;
    private final BigDecimal minimumPilotBudgetUsd;
    private final BigDecimal minimumOperatingBalanceKrw;
    private final BigDecimal minimumOperatingBalanceUsd;

    public DynamicBudgetAllocationService(PilotMapper pilotMapper,
                                          @Value("${budget.pilot-allocation-ratio:0.10}") BigDecimal pilotAllocationRatio,
                                          @Value("${budget.total-wallet-balance.krw:0}") BigDecimal totalWalletBalanceKrw,
                                          @Value("${budget.total-wallet-balance.usd:0}") BigDecimal totalWalletBalanceUsd,
                                          @Value("${budget.minimum-pilot-budget.krw:100000}") BigDecimal minimumPilotBudgetKrw,
                                          @Value("${budget.minimum-pilot-budget.usd:0}") BigDecimal minimumPilotBudgetUsd,
                                          @Value("${budget.minimum-operating-balance.krw:100000}") BigDecimal minimumOperatingBalanceKrw,
                                          @Value("${budget.minimum-operating-balance.usd:0}") BigDecimal minimumOperatingBalanceUsd) {
        this.pilotMapper = pilotMapper;
        this.pilotAllocationRatio = pilotAllocationRatio;
        this.totalWalletBalanceKrw = totalWalletBalanceKrw;
        this.totalWalletBalanceUsd = totalWalletBalanceUsd;
        this.minimumPilotBudgetKrw = minimumPilotBudgetKrw;
        this.minimumPilotBudgetUsd = minimumPilotBudgetUsd;
        this.minimumOperatingBalanceKrw = minimumOperatingBalanceKrw;
        this.minimumOperatingBalanceUsd = minimumOperatingBalanceUsd;
    }

    public BudgetAllocation calculate(String marketCurrency) {
        String normalizedCurrency = normalizeCurrency(marketCurrency);
        BigDecimal totalWalletBalance = totalWalletBalanceFor(normalizedCurrency);
        BigDecimal minimumPilotBudget = minimumPilotBudgetFor(normalizedCurrency);
        BigDecimal pilotBudget = totalWalletBalance.multiply(pilotAllocationRatio).max(minimumPilotBudget);
        BigDecimal autobotBudget = totalWalletBalance.subtract(pilotBudget).max(ZERO);
        BigDecimal pilotUsedAmount = parseAmount(pilotMapper.sumOpenBuyAmountBySystemType("PILOT", normalizedCurrency));
        BigDecimal autobotUsedAmount = parseAmount(pilotMapper.sumOpenBuyAmountBySystemType("AUTOBOT", normalizedCurrency));

        BudgetAllocation allocation = new BudgetAllocation(
                normalizedCurrency,
                totalWalletBalance,
                pilotBudget,
                autobotBudget,
                pilotUsedAmount,
                autobotUsedAmount
        );
        pilotMapper.insertBudgetAllocationSnapshot(
                allocation.getMarketCurrency(),
                allocation.getTotalWalletBalance().toPlainString(),
                allocation.getPilotBudget().toPlainString(),
                allocation.getAutobotBudget().toPlainString(),
                allocation.getPilotUsedAmount().toPlainString(),
                allocation.getAutobotUsedAmount().toPlainString(),
                allocation.getPilotAvailableAmount().toPlainString(),
                allocation.getAutobotAvailableAmount().toPlainString(),
                now()
        );
        return allocation;
    }

    public boolean canAllocatePilot(String marketCurrency, BigDecimal orderAmount) {
        BudgetAllocation allocation = calculate(marketCurrency);
        if (activatePanicStopIfInvalid(allocation)) {
            return false;
        }
        return allocation.getPilotAvailableAmount().compareTo(orderAmount) >= 0;
    }

    public boolean canAllocateAutobot(String marketCurrency, BigDecimal orderAmount) {
        BudgetAllocation allocation = calculate(marketCurrency);
        if (activatePanicStopIfInvalid(allocation)) {
            return false;
        }
        return allocation.getAutobotAvailableAmount().compareTo(orderAmount) >= 0;
    }

    public void assertBudgetIntegrity(String marketCurrency) {
        BudgetAllocation allocation = calculate(marketCurrency);
        if (activatePanicStopIfInvalid(allocation)) {
            throw new IllegalStateException("panic stop activated by budget integrity mismatch: " + marketCurrency);
        }
    }

    private boolean activatePanicStopIfInvalid(BudgetAllocation allocation) {
        BigDecimal totalUsedAmount = allocation.getPilotUsedAmount().add(allocation.getAutobotUsedAmount());
        if (allocation.getTotalWalletBalance().compareTo(minimumOperatingBalanceFor(allocation.getMarketCurrency())) < 0) {
            pilotMapper.insertPanicStopEvent(
                    "MINIMUM_OPERATING_BALANCE_NOT_MET",
                    budgetDetail(allocation),
                    now()
            );
            return true;
        }
        if (totalUsedAmount.compareTo(allocation.getTotalWalletBalance()) > 0) {
            pilotMapper.insertPanicStopEvent(
                    "BUDGET_USAGE_EXCEEDS_WALLET_BALANCE",
                    budgetDetail(allocation),
                    now()
            );
            return true;
        }
        return false;
    }

    private String budgetDetail(BudgetAllocation allocation) {
        return "currency=" + allocation.getMarketCurrency()
                + ", totalWalletBalance=" + allocation.getTotalWalletBalance().toPlainString()
                + ", pilotBudget=" + allocation.getPilotBudget().toPlainString()
                + ", autobotBudget=" + allocation.getAutobotBudget().toPlainString()
                + ", pilotUsedAmount=" + allocation.getPilotUsedAmount().toPlainString()
                + ", autobotUsedAmount=" + allocation.getAutobotUsedAmount().toPlainString();
    }

    private BigDecimal totalWalletBalanceFor(String marketCurrency) {
        if ("KRW".equals(marketCurrency)) {
            return totalWalletBalanceKrw;
        }
        if ("USD".equals(marketCurrency)) {
            return totalWalletBalanceUsd;
        }
        throw new IllegalArgumentException("unsupported budget currency: " + marketCurrency);
    }

    private BigDecimal minimumPilotBudgetFor(String marketCurrency) {
        if ("KRW".equals(marketCurrency)) {
            return minimumPilotBudgetKrw;
        }
        if ("USD".equals(marketCurrency)) {
            return minimumPilotBudgetUsd;
        }
        throw new IllegalArgumentException("unsupported budget currency: " + marketCurrency);
    }

    private BigDecimal minimumOperatingBalanceFor(String marketCurrency) {
        if ("KRW".equals(marketCurrency)) {
            return minimumOperatingBalanceKrw;
        }
        if ("USD".equals(marketCurrency)) {
            return minimumOperatingBalanceUsd;
        }
        throw new IllegalArgumentException("unsupported budget currency: " + marketCurrency);
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return ZERO;
        }
        return new BigDecimal(value);
    }

    private String normalizeCurrency(String marketCurrency) {
        if (marketCurrency == null || marketCurrency.isBlank()) {
            throw new IllegalArgumentException("market currency is required");
        }
        return marketCurrency.toUpperCase();
    }

    private String now() {
        return OffsetDateTime.now().format(TIME_FORMATTER);
    }
}
