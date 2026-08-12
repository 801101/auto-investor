package com.won.autoinvestor.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "runtime")
public class RuntimeProperties {

    private String instanceId = "local-01";
    private boolean tradingEnabled = false;
    private long kisBatchIntervalMinutes = 20L;
    private long internalBatchIntervalMinutes = 5L;

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public boolean isTradingEnabled() {
        return tradingEnabled;
    }

    public void setTradingEnabled(boolean tradingEnabled) {
        this.tradingEnabled = tradingEnabled;
    }

    public long getKisBatchIntervalMinutes() {
        return kisBatchIntervalMinutes;
    }

    public void setKisBatchIntervalMinutes(long kisBatchIntervalMinutes) {
        this.kisBatchIntervalMinutes = kisBatchIntervalMinutes;
    }

    public long getInternalBatchIntervalMinutes() {
        return internalBatchIntervalMinutes;
    }

    public void setInternalBatchIntervalMinutes(long internalBatchIntervalMinutes) {
        this.internalBatchIntervalMinutes = internalBatchIntervalMinutes;
    }
}
