package com.won.autoinvestor.trading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalTime;

@ConfigurationProperties(prefix = "market")
public class MarketProperties {

    private String timezone = "Asia/Seoul";
    private LocalTime regularOpenTime = LocalTime.of(9, 0);
    private LocalTime regularCloseTime = LocalTime.of(15, 20);
    private boolean allowPreMarketOrder = false;
    private boolean allowAfterHoursOrder = false;

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public LocalTime getRegularOpenTime() {
        return regularOpenTime;
    }

    public void setRegularOpenTime(LocalTime regularOpenTime) {
        this.regularOpenTime = regularOpenTime;
    }

    public LocalTime getRegularCloseTime() {
        return regularCloseTime;
    }

    public void setRegularCloseTime(LocalTime regularCloseTime) {
        this.regularCloseTime = regularCloseTime;
    }

    public boolean isAllowPreMarketOrder() {
        return allowPreMarketOrder;
    }

    public void setAllowPreMarketOrder(boolean allowPreMarketOrder) {
        this.allowPreMarketOrder = allowPreMarketOrder;
    }

    public boolean isAllowAfterHoursOrder() {
        return allowAfterHoursOrder;
    }

    public void setAllowAfterHoursOrder(boolean allowAfterHoursOrder) {
        this.allowAfterHoursOrder = allowAfterHoursOrder;
    }
}
