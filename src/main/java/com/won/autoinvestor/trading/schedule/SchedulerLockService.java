package com.won.autoinvestor.trading.schedule;

import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SchedulerLockService {

    private final Set<String> runningSchedulers = ConcurrentHashMap.newKeySet();

    public boolean tryLock(String schedulerType) {
        return runningSchedulers.add(schedulerType);
    }

    public void unlock(String schedulerType) {
        runningSchedulers.remove(schedulerType);
    }
}
