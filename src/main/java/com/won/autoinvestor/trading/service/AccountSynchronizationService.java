package com.won.autoinvestor.trading.service;

import com.won.autoinvestor.broker.BrokerClient;
import com.won.autoinvestor.pilot.mapper.PilotMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class AccountSynchronizationService {

    private static final Logger logger = LoggerFactory.getLogger(AccountSynchronizationService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final BrokerClient brokerClient;
    private final PilotMapper pilotMapper;

    public AccountSynchronizationService(BrokerClient brokerClient, PilotMapper pilotMapper) {
        this.brokerClient = brokerClient;
        this.pilotMapper = pilotMapper;
    }

    public void syncAccount() {
        try {
            brokerClient.getAccountBalance();
            brokerClient.getHoldings();
            pilotMapper.insertAuditLog("ACCOUNT_SYNC", null, "account and holdings synchronized", now());
            logger.info("account synchronization completed");
        } catch (UnsupportedOperationException e) {
            pilotMapper.insertAuditLog("ACCOUNT_SYNC_SKIPPED", null, e.getMessage(), now());
            logger.warn("account synchronization skipped: {}", e.getMessage());
        } catch (RuntimeException e) {
            pilotMapper.insertAuditLog("ACCOUNT_SYNC_FAILED", null, e.getMessage(), now());
            logger.error("account synchronization failed", e);
        }
    }

    private String now() {
        return OffsetDateTime.now().format(TIME_FORMATTER);
    }
}
