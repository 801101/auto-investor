package com.won.autoinvestor.common.trade;

import org.springframework.stereotype.Service;

@Service
public class AccountSyncStateService {

    private volatile boolean lastSyncSuccessful = true;
    private volatile String lastFailureMessage;

    public void recordSuccess() {
        lastSyncSuccessful = true;
        lastFailureMessage = null;
    }

    public void recordFailure(String message) {
        lastSyncSuccessful = false;
        lastFailureMessage = message;
    }

    public boolean isLastSyncSuccessful() {
        return lastSyncSuccessful;
    }
}
