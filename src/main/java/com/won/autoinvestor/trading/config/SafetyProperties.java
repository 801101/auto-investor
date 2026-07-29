package com.won.autoinvestor.trading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "safety")
public class SafetyProperties {

    private boolean killSwitchEnabled = false;
    private boolean rejectOrderWhenBalanceSyncFailed = true;
    private long rejectOrderWhenPriceStaleSeconds = 30;
    private boolean rejectOrderWhenAccountMismatch = true;

    public boolean isKillSwitchEnabled() {
        return killSwitchEnabled;
    }

    public void setKillSwitchEnabled(boolean killSwitchEnabled) {
        this.killSwitchEnabled = killSwitchEnabled;
    }

    public boolean isRejectOrderWhenBalanceSyncFailed() {
        return rejectOrderWhenBalanceSyncFailed;
    }

    public void setRejectOrderWhenBalanceSyncFailed(boolean rejectOrderWhenBalanceSyncFailed) {
        this.rejectOrderWhenBalanceSyncFailed = rejectOrderWhenBalanceSyncFailed;
    }

    public long getRejectOrderWhenPriceStaleSeconds() {
        return rejectOrderWhenPriceStaleSeconds;
    }

    public void setRejectOrderWhenPriceStaleSeconds(long rejectOrderWhenPriceStaleSeconds) {
        this.rejectOrderWhenPriceStaleSeconds = rejectOrderWhenPriceStaleSeconds;
    }

    public boolean isRejectOrderWhenAccountMismatch() {
        return rejectOrderWhenAccountMismatch;
    }

    public void setRejectOrderWhenAccountMismatch(boolean rejectOrderWhenAccountMismatch) {
        this.rejectOrderWhenAccountMismatch = rejectOrderWhenAccountMismatch;
    }
}
