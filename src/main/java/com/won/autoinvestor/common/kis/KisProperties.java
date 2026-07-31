package com.won.autoinvestor.common.kis;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kis")
public class KisProperties {

    private String accountMode = "PAPER";
    private String appKey = "";
    private String appSecret = "";
    private String accountNumber = "";
    private String accountProductCode = "";
    private String baseUrl = "";
    private String tokenPath = "/oauth2/tokenP";
    private String accessTokenCachePath = "./.kis-access-token-cache.properties";
    private String customerType = "P";
    private String orderDivision = "01";
    private final Domestic domestic = new Domestic();
    private final Overseas overseas = new Overseas();
    private final AccountProfile paper = AccountProfile.paper();
    private final AccountProfile real = AccountProfile.real();

    public String getAccountMode() {
        return accountMode;
    }

    public void setAccountMode(String accountMode) {
        this.accountMode = accountMode;
    }

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
        String profileBaseUrl = activeProfile().baseUrl;
        if (hasText(profileBaseUrl)) {
            return profileBaseUrl;
        }
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

    public String getAccessTokenCachePath() {
        return accessTokenCachePath;
    }

    public void setAccessTokenCachePath(String accessTokenCachePath) {
        this.accessTokenCachePath = accessTokenCachePath;
    }

    public String getCustomerType() {
        return customerType;
    }

    public void setCustomerType(String customerType) {
        this.customerType = customerType;
    }

    public String getOrderDivision() {
        return orderDivision;
    }

    public void setOrderDivision(String orderDivision) {
        this.orderDivision = orderDivision;
    }

    public Domestic getDomestic() {
        return domestic;
    }

    public Overseas getOverseas() {
        return overseas;
    }

    public AccountProfile getPaper() {
        return paper;
    }

    public AccountProfile getReal() {
        return real;
    }

    public boolean isConfigured() {
        return hasText(appKey)
                && hasText(appSecret)
                && hasText(accountNumber)
                && hasText(accountProductCode)
                && hasText(getBaseUrl());
    }

    public String getCurrentPricePath() {
        return domestic.currentPricePath;
    }

    public String getBalancePath() {
        return domestic.balancePath;
    }

    public String getOrderCashPath() {
        return domestic.orderCashPath;
    }

    public String getCurrentPriceTrId() {
        return activeValue(activeProfile().domesticCurrentPriceTrId, domestic.currentPriceTrId);
    }

    public String getBalanceTrId() {
        return activeValue(activeProfile().domesticBalanceTrId, domestic.balanceTrId);
    }

    public String getBuyTrId() {
        return activeValue(activeProfile().domesticBuyTrId, domestic.buyTrId);
    }

    public String getSellTrId() {
        return activeValue(activeProfile().domesticSellTrId, domestic.sellTrId);
    }

    public String getMarketDivisionCode() {
        return domestic.marketDivisionCode;
    }

    public String getOverseasPricePath() {
        return overseas.pricePath;
    }

    public String getOverseasBalancePath() {
        return overseas.balancePath;
    }

    public String getOverseasBuyableAmountPath() {
        return overseas.buyableAmountPath;
    }

    public String getOverseasOrderPath() {
        return overseas.orderPath;
    }

    public String getOverseasPriceTrId() {
        return activeValue(activeProfile().overseasPriceTrId, overseas.priceTrId);
    }

    public String getOverseasBalanceTrId() {
        return activeValue(activeProfile().overseasBalanceTrId, overseas.balanceTrId);
    }

    public String getOverseasBuyableAmountTrId() {
        return activeValue(activeProfile().overseasBuyableAmountTrId, overseas.buyableAmountTrId);
    }

    public String getOverseasBuyTrId() {
        return activeValue(activeProfile().overseasBuyTrId, overseas.buyTrId);
    }

    public String getOverseasSellTrId() {
        return activeValue(activeProfile().overseasSellTrId, overseas.sellTrId);
    }

    public boolean isPaperMode() {
        return "PAPER".equalsIgnoreCase(accountMode);
    }

    public boolean isRealMode() {
        return "REAL".equalsIgnoreCase(accountMode);
    }

    private AccountProfile activeProfile() {
        return isRealMode() ? real : paper;
    }

    private String activeValue(String profileValue, String fallbackValue) {
        return hasText(profileValue) ? profileValue : fallbackValue;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public static class Domestic {

        private String currentPricePath = "/uapi/domestic-stock/v1/quotations/inquire-price";
        private String balancePath = "/uapi/domestic-stock/v1/trading/inquire-balance";
        private String orderCashPath = "/uapi/domestic-stock/v1/trading/order-cash";
        private String currentPriceTrId = "FHKST01010100";
        private String balanceTrId = "VTTC8434R";
        private String buyTrId = "VTTC0012U";
        private String sellTrId = "VTTC0011U";
        private String marketDivisionCode = "J";

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

        public String getMarketDivisionCode() {
            return marketDivisionCode;
        }

        public void setMarketDivisionCode(String marketDivisionCode) {
            this.marketDivisionCode = marketDivisionCode;
        }
    }

    public static class Overseas {

        private String pricePath = "/uapi/overseas-price/v1/quotations/price";
        private String balancePath = "/uapi/overseas-stock/v1/trading/inquire-balance";
        private String buyableAmountPath = "/uapi/overseas-stock/v1/trading/inquire-psamount";
        private String orderPath = "/uapi/overseas-stock/v1/trading/order";
        private String priceTrId = "HHDFS00000300";
        private String balanceTrId = "VTTS3012R";
        private String buyableAmountTrId = "VTTS3007R";
        private String buyTrId = "VTTT1002U";
        private String sellTrId = "VTTT1001U";

        public String getPricePath() {
            return pricePath;
        }

        public void setPricePath(String pricePath) {
            this.pricePath = pricePath;
        }

        public String getBalancePath() {
            return balancePath;
        }

        public void setBalancePath(String balancePath) {
            this.balancePath = balancePath;
        }

        public String getBuyableAmountPath() {
            return buyableAmountPath;
        }

        public void setBuyableAmountPath(String buyableAmountPath) {
            this.buyableAmountPath = buyableAmountPath;
        }

        public String getOrderPath() {
            return orderPath;
        }

        public void setOrderPath(String orderPath) {
            this.orderPath = orderPath;
        }

        public String getPriceTrId() {
            return priceTrId;
        }

        public void setPriceTrId(String priceTrId) {
            this.priceTrId = priceTrId;
        }

        public String getBalanceTrId() {
            return balanceTrId;
        }

        public void setBalanceTrId(String balanceTrId) {
            this.balanceTrId = balanceTrId;
        }

        public String getBuyableAmountTrId() {
            return buyableAmountTrId;
        }

        public void setBuyableAmountTrId(String buyableAmountTrId) {
            this.buyableAmountTrId = buyableAmountTrId;
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
    }

    public static class AccountProfile {

        private String baseUrl;
        private String domesticCurrentPriceTrId;
        private String domesticBalanceTrId;
        private String domesticBuyTrId;
        private String domesticSellTrId;
        private String overseasPriceTrId;
        private String overseasBalanceTrId;
        private String overseasBuyableAmountTrId;
        private String overseasBuyTrId;
        private String overseasSellTrId;

        static AccountProfile paper() {
            AccountProfile profile = new AccountProfile();
            profile.baseUrl = "https://openapivts.koreainvestment.com:29443";
            profile.domesticCurrentPriceTrId = "FHKST01010100";
            profile.domesticBalanceTrId = "VTTC8434R";
            profile.domesticBuyTrId = "VTTC0012U";
            profile.domesticSellTrId = "VTTC0011U";
            profile.overseasPriceTrId = "HHDFS00000300";
            profile.overseasBalanceTrId = "VTTS3012R";
            profile.overseasBuyableAmountTrId = "VTTS3007R";
            profile.overseasBuyTrId = "VTTT1002U";
            profile.overseasSellTrId = "VTTT1001U";
            return profile;
        }

        static AccountProfile real() {
            AccountProfile profile = new AccountProfile();
            profile.baseUrl = "https://openapi.koreainvestment.com:9443";
            profile.domesticCurrentPriceTrId = "FHKST01010100";
            profile.domesticBalanceTrId = "TTTC8434R";
            profile.domesticBuyTrId = "TTTC0012U";
            profile.domesticSellTrId = "TTTC0011U";
            profile.overseasPriceTrId = "HHDFS00000300";
            profile.overseasBalanceTrId = "TTTS3012R";
            profile.overseasBuyableAmountTrId = "TTTS3007R";
            profile.overseasBuyTrId = "TTTT1002U";
            profile.overseasSellTrId = "TTTT1006U";
            return profile;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getDomesticCurrentPriceTrId() {
            return domesticCurrentPriceTrId;
        }

        public void setDomesticCurrentPriceTrId(String domesticCurrentPriceTrId) {
            this.domesticCurrentPriceTrId = domesticCurrentPriceTrId;
        }

        public String getDomesticBalanceTrId() {
            return domesticBalanceTrId;
        }

        public void setDomesticBalanceTrId(String domesticBalanceTrId) {
            this.domesticBalanceTrId = domesticBalanceTrId;
        }

        public String getDomesticBuyTrId() {
            return domesticBuyTrId;
        }

        public void setDomesticBuyTrId(String domesticBuyTrId) {
            this.domesticBuyTrId = domesticBuyTrId;
        }

        public String getDomesticSellTrId() {
            return domesticSellTrId;
        }

        public void setDomesticSellTrId(String domesticSellTrId) {
            this.domesticSellTrId = domesticSellTrId;
        }

        public String getOverseasPriceTrId() {
            return overseasPriceTrId;
        }

        public void setOverseasPriceTrId(String overseasPriceTrId) {
            this.overseasPriceTrId = overseasPriceTrId;
        }

        public String getOverseasBalanceTrId() {
            return overseasBalanceTrId;
        }

        public void setOverseasBalanceTrId(String overseasBalanceTrId) {
            this.overseasBalanceTrId = overseasBalanceTrId;
        }

        public String getOverseasBuyableAmountTrId() {
            return overseasBuyableAmountTrId;
        }

        public void setOverseasBuyableAmountTrId(String overseasBuyableAmountTrId) {
            this.overseasBuyableAmountTrId = overseasBuyableAmountTrId;
        }

        public String getOverseasBuyTrId() {
            return overseasBuyTrId;
        }

        public void setOverseasBuyTrId(String overseasBuyTrId) {
            this.overseasBuyTrId = overseasBuyTrId;
        }

        public String getOverseasSellTrId() {
            return overseasSellTrId;
        }

        public void setOverseasSellTrId(String overseasSellTrId) {
            this.overseasSellTrId = overseasSellTrId;
        }
    }
}
