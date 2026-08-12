package com.won.autoinvestor.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "investment")
public class InvestmentProperties {

    private final Market market = new Market();
    private final Order order = new Order();
    private final Holding holding = new Holding();
    private final Candidate candidate = new Candidate();
    private final Strategy strategy = new Strategy();

    public Market getMarket() {
        return market;
    }

    public Order getOrder() {
        return order;
    }

    public Holding getHolding() {
        return holding;
    }

    public Candidate getCandidate() {
        return candidate;
    }

    public Strategy getStrategy() {
        return strategy;
    }

    public String getOrderUnitType() {
        return order.unitType;
    }

    public void setOrderUnitType(String orderUnitType) {
        order.unitType = orderUnitType;
    }

    public String getMarketType() {
        return market.type;
    }

    public void setMarketType(String marketType) {
        market.type = marketType;
    }

    public BigDecimal getUnitAmount() {
        return order.unitAmount;
    }

    public void setUnitAmount(BigDecimal unitAmount) {
        order.unitAmount = unitAmount;
    }

    public BigDecimal getUnitShares() {
        return order.unitShares;
    }

    public void setUnitShares(BigDecimal unitShares) {
        order.unitShares = unitShares;
    }

    public String getDomesticMarketCode() {
        return market.domesticMarketCode;
    }

    public void setDomesticMarketCode(String domesticMarketCode) {
        market.domesticMarketCode = domesticMarketCode;
    }

    public String getOverseasExchangeCode() {
        return market.overseasExchangeCode;
    }

    public void setOverseasExchangeCode(String overseasExchangeCode) {
        market.overseasExchangeCode = overseasExchangeCode;
    }

    public String getOverseasPriceExchangeCode() {
        return market.overseasPriceExchangeCode;
    }

    public void setOverseasPriceExchangeCode(String overseasPriceExchangeCode) {
        market.overseasPriceExchangeCode = overseasPriceExchangeCode;
    }

    public String getOverseasCurrencyCode() {
        return market.overseasCurrencyCode;
    }

    public void setOverseasCurrencyCode(String overseasCurrencyCode) {
        market.overseasCurrencyCode = overseasCurrencyCode;
    }

    public int getMaxHoldingsPerStock() {
        return holding.maxHoldingsPerStock;
    }

    public void setMaxHoldingsPerStock(int maxHoldingsPerStock) {
        holding.maxHoldingsPerStock = maxHoldingsPerStock;
    }

    public int getMaxHoldings() {
        return holding.maxHoldings;
    }

    public void setMaxHoldings(int maxHoldings) {
        holding.maxHoldings = maxHoldings;
    }

    public boolean isIncludeEtf() {
        return candidate.includeEtf;
    }

    public void setIncludeEtf(boolean includeEtf) {
        candidate.includeEtf = includeEtf;
    }

    public BigDecimal getTakeProfitRate() {
        if (strategy.takeProfit != null && strategy.takeProfit.getRate() != null) {
            return strategy.takeProfit.getRate();
        }
        return new BigDecimal("0.10");
    }

    public void setTakeProfitRate(BigDecimal takeProfitRate) {
        strategy.takeProfit.rate = takeProfitRate;
    }

    public RatePolicy getTakeProfit() {
        return strategy.takeProfit;
    }

    public void setTakeProfit(RatePolicy takeProfit) {
        strategy.takeProfit = takeProfit;
    }

    public RatePolicy getStopLoss() {
        return strategy.stopLoss;
    }

    public void setStopLoss(RatePolicy stopLoss) {
        strategy.stopLoss = stopLoss;
    }

    public int getGrayGraceTradingDays() {
        return strategy.gray.graceTradingDays;
    }

    public int getWhiteFlatGraceTradingDays() {
        return strategy.white.flatGraceTradingDays;
    }

    public static class Market {

        private String type = "OVERSEAS";
        private String domesticMarketCode = "ALL";
        private String overseasExchangeCode = "NASD";
        private String overseasPriceExchangeCode = "NAS";
        private String overseasCurrencyCode = "USD";

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDomesticMarketCode() {
            return domesticMarketCode;
        }

        public void setDomesticMarketCode(String domesticMarketCode) {
            this.domesticMarketCode = domesticMarketCode;
        }

        public String getOverseasExchangeCode() {
            return overseasExchangeCode;
        }

        public void setOverseasExchangeCode(String overseasExchangeCode) {
            this.overseasExchangeCode = overseasExchangeCode;
        }

        public String getOverseasPriceExchangeCode() {
            return overseasPriceExchangeCode;
        }

        public void setOverseasPriceExchangeCode(String overseasPriceExchangeCode) {
            this.overseasPriceExchangeCode = overseasPriceExchangeCode;
        }

        public String getOverseasCurrencyCode() {
            return overseasCurrencyCode;
        }

        public void setOverseasCurrencyCode(String overseasCurrencyCode) {
            this.overseasCurrencyCode = overseasCurrencyCode;
        }
    }

    public static class Order {

        private String unitType = "SHARE";
        private BigDecimal unitAmount = new BigDecimal("1.00");
        private BigDecimal unitShares = BigDecimal.ONE;

        public String getUnitType() {
            return unitType;
        }

        public void setUnitType(String unitType) {
            this.unitType = unitType;
        }

        public BigDecimal getUnitAmount() {
            return unitAmount;
        }

        public void setUnitAmount(BigDecimal unitAmount) {
            this.unitAmount = unitAmount;
        }

        public BigDecimal getUnitShares() {
            return unitShares;
        }

        public void setUnitShares(BigDecimal unitShares) {
            this.unitShares = unitShares;
        }
    }

    public static class Holding {

        private int maxHoldingsPerStock = 1;
        private int maxHoldings = 50;

        public int getMaxHoldingsPerStock() {
            return maxHoldingsPerStock;
        }

        public void setMaxHoldingsPerStock(int maxHoldingsPerStock) {
            this.maxHoldingsPerStock = maxHoldingsPerStock;
        }

        public int getMaxHoldings() {
            return maxHoldings;
        }

        public void setMaxHoldings(int maxHoldings) {
            this.maxHoldings = maxHoldings;
        }
    }

    public static class Candidate {

        private boolean includeEtf = false;

        public boolean isIncludeEtf() {
            return includeEtf;
        }

        public void setIncludeEtf(boolean includeEtf) {
            this.includeEtf = includeEtf;
        }
    }

    public static class Strategy {

        private RatePolicy takeProfit = new RatePolicy(new BigDecimal("0.10"));
        private RatePolicy stopLoss = new RatePolicy(new BigDecimal("-0.10"));
        private WhitePolicy white = new WhitePolicy();
        private GrayPolicy gray = new GrayPolicy();

        public RatePolicy getTakeProfit() {
            return takeProfit;
        }

        public void setTakeProfit(RatePolicy takeProfit) {
            this.takeProfit = takeProfit;
        }

        public RatePolicy getStopLoss() {
            return stopLoss;
        }

        public void setStopLoss(RatePolicy stopLoss) {
            this.stopLoss = stopLoss;
        }

        public WhitePolicy getWhite() {
            return white;
        }

        public void setWhite(WhitePolicy white) {
            this.white = white;
        }

        public GrayPolicy getGray() {
            return gray;
        }

        public void setGray(GrayPolicy gray) {
            this.gray = gray;
        }

    }

    public static class WhitePolicy {

        private int flatGraceTradingDays = 3;

        public int getFlatGraceTradingDays() {
            return flatGraceTradingDays;
        }

        public void setFlatGraceTradingDays(int flatGraceTradingDays) {
            this.flatGraceTradingDays = flatGraceTradingDays;
        }
    }

    public static class GrayPolicy {

        private int graceTradingDays = 3;

        public int getGraceTradingDays() {
            return graceTradingDays;
        }

        public void setGraceTradingDays(int graceTradingDays) {
            this.graceTradingDays = graceTradingDays;
        }
    }

    public static class RatePolicy {

        private BigDecimal rate;

        public RatePolicy() {
        }

        public RatePolicy(BigDecimal rate) {
            this.rate = rate;
        }

        public BigDecimal getRate() {
            return rate;
        }

        public void setRate(BigDecimal rate) {
            this.rate = rate;
        }
    }
}
