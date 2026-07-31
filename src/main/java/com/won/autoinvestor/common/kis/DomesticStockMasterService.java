package com.won.autoinvestor.common.kis;

import com.won.autoinvestor.common.config.InvestmentProperties;
import com.won.autoinvestor.common.util.MapUtils;
import com.won.autoinvestor.pilot.PilotMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class DomesticStockMasterService implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DomesticStockMasterService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final InvestmentProperties investmentProperties;
    private final DomesticStockMasterProvider provider;
    private final PilotMapper mapper;

    public DomesticStockMasterService(InvestmentProperties investmentProperties,
                                      DomesticStockMasterProvider provider,
                                      PilotMapper mapper) {
        this.investmentProperties = investmentProperties;
        this.provider = provider;
        this.mapper = mapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"DOMESTIC".equalsIgnoreCase(investmentProperties.getMarketType())) {
            return;
        }
        if (mapper.domesticCountMasterRows(MapUtils.map("marketCode", investmentProperties.getDomesticMarketCode())) == 0) {
            logger.info("domestic stock master is empty. initial sync starts.");
            refresh();
        }
    }

    @Scheduled(fixedDelay = 86_400_000L, initialDelay = 86_400_000L)
    public void scheduledRefresh() {
        if ("DOMESTIC".equalsIgnoreCase(investmentProperties.getMarketType())) {
            refresh();
        }
    }

    @Transactional
    public void refresh() {
        String marketCode = investmentProperties.getDomesticMarketCode();
        List<Map<String, Object>> masters = provider.fetch(marketCode);
        if (masters.isEmpty()) {
            String message = "domestic stock master refresh skipped because provider returned empty list";
            logger.warn(message);
            mapper.insertAuditLog(MapUtils.map("eventType", "DOMESTIC_MASTER_REFRESH_SKIPPED", "stockCode", null, "details", message, "createdAt", now()));
            return;
        }

        for (Map<String, Object> master : masters) {
            mapper.domesticUpsertMaster(master);
        }
        int buyableCount = mapper.domesticCountBuyableCandidates(MapUtils.map("marketCode", marketCode));
        String message = "synced=" + masters.size() + ", buyableCandidates=" + buyableCount;
        logger.info("domestic stock master refreshed. {}", message);
        mapper.insertAuditLog(MapUtils.map("eventType", "DOMESTIC_MASTER_REFRESHED", "stockCode", null, "details", message, "createdAt", now()));
    }

    private String now() {
        return OffsetDateTime.now().format(TIME_FORMATTER);
    }
}
