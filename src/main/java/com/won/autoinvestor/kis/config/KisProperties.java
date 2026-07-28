package com.won.autoinvestor.kis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kis")
public class KisProperties {

    private String appKey = "";
    private String appSecret = "";
    private String accountNumber = "";
    private String accountProductCode = "";
    private String baseUrl = "https://openapi.koreainvestment.com:9443";
    private String tokenPath = "/oauth2/tokenP";
    private String currentPricePath = "/uapi/domestic-stock/v1/quotations/inquire-price";
    private String balancePath = "/uapi/domestic-stock/v1/trading/inquire-balance";
    private String orderCashPath = "/uapi/domestic-stock/v1/trading/order-cash";
    private String currentPriceTrId = "FHKST01010100";
    private String balanceTrId = "TTTC8434R";
    private String buyTrId = "TTTC0802U";
    private String sellTrId = "TTTC0801U";
    private String customerType = "P";
    private String marketDivisionCode = "J";
    private String orderDivision = "01";

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getAccountProductCode() {
        return accountProductCode;
    }

    public void setAccountProductCode(String accountProductCode) {
        this.accountProductCode = accountProductCode;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getTokenPath() {
        return tokenPath;
    }

    public void setTokenPath(String tokenPath) {
        this.tokenPath = tokenPath;
    }

    public String getCurrentPricePath() {
        return currentPricePath;
    }

    public void setCurrentPricePath(String currentPricePath) {
        this.currentPricePath = currentPricePath;
    }

    public String getBalancePath() {
        return balancePath;
    }

    public void setBalancePath(String balancePath) {
        this.balancePath = balancePath;
    }

    public String getOrderCashPath() {
        return orderCashPath;
    }

    public void setOrderCashPath(String orderCashPath) {
        this.orderCashPath = orderCashPath;
    }

    public String getCurrentPriceTrId() {
        return currentPriceTrId;
    }

    public void setCurrentPriceTrId(String currentPriceTrId) {
        this.currentPriceTrId = currentPriceTrId;
    }

    public String getBalanceTrId() {
        return balanceTrId;
    }

    public void setBalanceTrId(String balanceTrId) {
        this.balanceTrId = balanceTrId;
    }

    public String getBuyTrId() {
        return buyTrId;
    }

    public void setBuyTrId(String buyTrId) {
        this.buyTrId = buyTrId;
    }

    public String getSellTrId() {
        return sellTrId;
    }

    public void setSellTrId(String sellTrId) {
        this.sellTrId = sellTrId;
    }

    public String getCustomerType() {
        return customerType;
    }

    public void setCustomerType(String customerType) {
        this.customerType = customerType;
    }

    public String getMarketDivisionCode() {
        return marketDivisionCode;
    }

    public void setMarketDivisionCode(String marketDivisionCode) {
        this.marketDivisionCode = marketDivisionCode;
    }

    public String getOrderDivision() {
        return orderDivision;
    }

    public void setOrderDivision(String orderDivision) {
        this.orderDivision = orderDivision;
    }

    public boolean isConfigured() {
        return hasText(appKey)
                && hasText(appSecret)
                && hasText(accountNumber)
                && hasText(accountProductCode)
                && hasText(baseUrl);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
