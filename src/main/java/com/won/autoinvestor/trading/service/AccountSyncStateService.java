package com.won.autoinvestor.trading.service;

import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class AccountSyncStateService {

    private volatile boolean lastSyncSuccessful = true;
    private volatile OffsetDateTime lastSyncAt;
    private volatile String lastFailureMessage;

    public void recordSuccess() {
        lastSyncSuccessful = true;
        lastSyncAt = OffsetDateTime.now();
        lastFailureMessage = null;
    }

    public void recordFailure(String message) {
        lastSyncSuccessful = false;
        lastSyncAt = OffsetDateTime.now();
        lastFailureMessage = message;
    }

    public boolean isLastSyncSuccessful() {
        return lastSyncSuccessful;
    }

    public OffsetDateTime getLastSyncAt() {
        return lastSyncAt;
    }

    public String getLastFailureMessage() {
        return lastFailureMessage;
    }
}
