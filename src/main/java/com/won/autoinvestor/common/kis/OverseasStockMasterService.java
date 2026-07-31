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
public class OverseasStockMasterService implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(OverseasStockMasterService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final InvestmentProperties investmentProperties;
    private final OverseasStockMasterProvider provider;
    private final PilotMapper mapper;

    public OverseasStockMasterService(InvestmentProperties investmentProperties,
                                      OverseasStockMasterProvider provider,
                                      PilotMapper mapper) {
        this.investmentProperties = investmentProperties;
        this.provider = provider;
        this.mapper = mapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"OVERSEAS".equalsIgnoreCase(investmentProperties.getMarketType())) {
            return;
        }
        int count = mapper.overseasCountMasterRows(MapUtils.map(
                "exchangeCode", investmentProperties.getOverseasExchangeCode(),
                "currencyCode", investmentProperties.getOverseasCurrencyCode()));
        if (count == 0) {
            logger.info("overseas stock master is empty. initial sync starts.");
            refresh();
        }
    }

    @Scheduled(fixedDelay = 86_400_000L, initialDelay = 86_400_000L)
    public void scheduledRefresh() {
        if ("OVERSEAS".equalsIgnoreCase(investmentProperties.getMarketType())) {
            refresh();
        }
    }

    @Transactional
    public void refresh() {
        String exchangeCode = investmentProperties.getOverseasExchangeCode();
        String priceExchangeCode = investmentProperties.getOverseasPriceExchangeCode();
        String currencyCode = investmentProperties.getOverseasCurrencyCode();

        List<Map<String, Object>> masters = provider.fetch(exchangeCode, priceExchangeCode, currencyCode);
        if (masters.isEmpty()) {
            String message = "overseas stock master refresh skipped because provider returned empty list";
            logger.warn(message);
            mapper.insertAuditLog(MapUtils.map("eventType", "OVERSEAS_MASTER_REFRESH_SKIPPED", "stockCode", null, "details", message, "createdAt", now()));
            return;
        }

        for (Map<String, Object> master : masters) {
            mapper.overseasUpsertMaster(master);
        }
        int buyableCount = mapper.overseasCountBuyableCandidates(MapUtils.map(
                "exchangeCode", exchangeCode,
                "priceExchangeCode", priceExchangeCode,
                "currencyCode", currencyCode));
        String message = "synced=" + masters.size() + ", buyableCandidates=" + buyableCount;
        logger.info("overseas stock master refreshed. {}", message);
        mapper.insertAuditLog(MapUtils.map("eventType", "OVERSEAS_MASTER_REFRESHED", "stockCode", null, "details", message, "createdAt", now()));
    }

    private String now() {
        return OffsetDateTime.now().format(TIME_FORMATTER);
    }
}
