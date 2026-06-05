package com.won.autoinvestor.pilot.domain;

import java.math.BigDecimal;

public class TradingLifecycleTarget {

    private Long id;
    private Long masterId;
    private String systemType;
    private String symbol;
    private String marketCurrency;
    private BigDecimal entryPrice;
    private BigDecimal entryQuantity;
    private BigDecimal entryAmount;
    private String entryTime;
    private String status;
    private String statusEnteredAt;
    private String grayEnteredAt;
    private String forceLiquidationFlag;
    private String updatedAt;
    private String masterStatus;
    private String masterSellTime;
    private BigDecimal lastPrice;
    private String tradedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMasterId() {
        return masterId;
    }

    public void setMasterId(Long masterId) {
        this.masterId = masterId;
    }

    public String getSystemType() {
        return systemType;
    }

    public void setSystemType(String systemType) {
        this.systemType = systemType;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getMarketCurrency() {
        return marketCurrency;
    }

    public void setMarketCurrency(String marketCurrency) {
        this.marketCurrency = marketCurrency;
    }

    public BigDecimal getEntryPrice() {
        return entryPrice;
    }

    public void setEntryPrice(BigDecimal entryPrice) {
        this.entryPrice = entryPrice;
    }

    public BigDecimal getEntryQuantity() {
        return entryQuantity;
    }

    public void setEntryQuantity(BigDecimal entryQuantity) {
        this.entryQuantity = entryQuantity;
    }

    public BigDecimal getEntryAmount() {
        return entryAmount;
    }

    public void setEntryAmount(BigDecimal entryAmount) {
        this.entryAmount = entryAmount;
    }

    public String getEntryTime() {
        return entryTime;
    }

    public void setEntryTime(String entryTime) {
        this.entryTime = entryTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusEnteredAt() {
        return statusEnteredAt;
    }

    public void setStatusEnteredAt(String statusEnteredAt) {
        this.statusEnteredAt = statusEnteredAt;
    }

    public String getGrayEnteredAt() {
        return grayEnteredAt;
    }

    public void setGrayEnteredAt(String grayEnteredAt) {
        this.grayEnteredAt = grayEnteredAt;
    }

    public String getForceLiquidationFlag() {
        return forceLiquidationFlag;
    }

    public void setForceLiquidationFlag(String forceLiquidationFlag) {
        this.forceLiquidationFlag = forceLiquidationFlag;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getMasterStatus() {
        return masterStatus;
    }

    public void setMasterStatus(String masterStatus) {
        this.masterStatus = masterStatus;
    }

    public String getMasterSellTime() {
        return masterSellTime;
    }

    public void setMasterSellTime(String masterSellTime) {
        this.masterSellTime = masterSellTime;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public void setLastPrice(BigDecimal lastPrice) {
        this.lastPrice = lastPrice;
    }

    public String getTradedAt() {
        return tradedAt;
    }

    public void setTradedAt(String tradedAt) {
        this.tradedAt = tradedAt;
    }
}
