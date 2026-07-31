package com.won.autoinvestor.common.kis;

import com.won.autoinvestor.common.util.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class KisOverseasStockMasterProvider implements OverseasStockMasterProvider {

    private static final Logger logger = LoggerFactory.getLogger(KisOverseasStockMasterProvider.class);
    private static final String MASTER_URL_TEMPLATE = "https://new.real.download.dws.co.kr/common/master/%smst.cod.zip";
    private static final Charset MASTER_CHARSET = Charset.forName("CP949");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public List<Map<String, Object>> fetch(String exchangeCode,
                                           String priceExchangeCode,
                                           String currencyCode) {
        String marketCode = toKisMarketCode(exchangeCode, priceExchangeCode);
        if (marketCode == null) {
            logger.warn("overseas stock master skipped. unsupported exchangeCode={}, priceExchangeCode={}",
                    exchangeCode, priceExchangeCode);
            return List.of();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MASTER_URL_TEMPLATE.formatted(marketCode)))
                    .GET()
                    .build();
            HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logger.warn("overseas stock master download failed. marketCode={}, status={}", marketCode, response.statusCode());
                return List.of();
            }
            return parseZip(response.body(), exchangeCode, priceExchangeCode, currencyCode);
        } catch (IOException e) {
            logger.warn("overseas stock master download or parse failed. marketCode={}, message={}", marketCode, e.getMessage());
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("overseas stock master download interrupted. marketCode={}", marketCode);
            return List.of();
        }
    }

    private List<Map<String, Object>> parseZip(java.io.InputStream body,
                                               String exchangeCode,
                                               String priceExchangeCode,
                                               String currencyCode) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(body);
             InputStreamReader inputStreamReader = new InputStreamReader(nextCodEntry(zipInputStream), MASTER_CHARSET);
             BufferedReader reader = new BufferedReader(inputStreamReader)) {
            List<Map<String, Object>> masters = new ArrayList<>();
            String now = OffsetDateTime.now().format(TIME_FORMATTER);
            String line;
            while ((line = reader.readLine()) != null) {
                Map<String, Object> master = parseLine(line, exchangeCode, priceExchangeCode, currencyCode, now);
                if (master != null) {
                    masters.add(master);
                }
            }
            logger.info("overseas stock master parsed. exchangeCode={}, rowCount={}", exchangeCode, masters.size());
            return masters;
        }
    }

    private ZipInputStream nextCodEntry(ZipInputStream zipInputStream) throws IOException {
        ZipEntry entry;
        while ((entry = zipInputStream.getNextEntry()) != null) {
            if (!entry.isDirectory() && entry.getName().toLowerCase(Locale.ROOT).endsWith(".cod")) {
                return zipInputStream;
            }
        }
        throw new IOException("KIS overseas master .cod entry not found");
    }

    private Map<String, Object> parseLine(String line,
                                          String exchangeCode,
                                          String priceExchangeCode,
                                          String currencyCode,
                                          String now) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] columns = line.split("\\t", -1);
        if (columns.length < 24) {
            logger.debug("overseas stock master line skipped by unexpected column count={}", columns.length);
            return null;
        }

        String symbol = columns[4].trim();
        String koreaName = columns[6].trim();
        String englishName = columns[7].trim();
        String securityType = columns[8].trim();
        String rowCurrency = columns[9].trim();
        BigDecimal basePrice = decimal(columns[12]);
        String classificationCode = columns[22].trim();

        boolean validType = "2".equals(securityType) || "3".equals(securityType);
        boolean excludedInstrument = securityType.equals("1")
                || securityType.equals("4")
                || "002".equals(classificationCode)
                || "003".equals(classificationCode)
                || "004".equals(classificationCode);
        boolean tradable = !symbol.isBlank()
                && currencyCode.equalsIgnoreCase(rowCurrency)
                && validType
                && !excludedInstrument
                && basePrice.compareTo(BigDecimal.ZERO) > 0;
        String excludedReason = tradable ? null : exclusionReason(symbol, rowCurrency, securityType, basePrice, classificationCode);

        return MapUtils.map(
                "id", null,
                "symbol", symbol,
                "stockName", koreaName.isBlank() ? englishName : koreaName,
                "exchangeCode", exchangeCode,
                "priceExchangeCode", priceExchangeCode,
                "currencyCode", rowCurrency.isBlank() ? currencyCode : rowCurrency,
                "securityType", securityType,
                "tradable", tradable,
                "active", true,
                "fractionalTradable", "UNKNOWN",
                "lastPrice", basePrice,
                "marketCap", null,
                "tradingVolume", null,
                "lastSelectedAt", null,
                "lastBuyAttemptAt", null,
                "lastBuySuccessAt", null,
                "consecutiveFailures", 0,
                "retryAfter", null,
                "excludedReason", excludedReason,
                "createdAt", now,
                "updatedAt", now,
                "lastSyncedAt", now
        );
    }

    private String exclusionReason(String symbol,
                                   String currencyCode,
                                   String securityType,
                                   BigDecimal basePrice,
                                   String classificationCode) {
        if (symbol == null || symbol.isBlank()) {
            return "EMPTY_SYMBOL";
        }
        if (!"USD".equalsIgnoreCase(currencyCode)) {
            return "NON_USD";
        }
        if (!("2".equals(securityType) || "3".equals(securityType))) {
            return "UNSUPPORTED_SECURITY_TYPE_" + securityType;
        }
        if ("002".equals(classificationCode) || "003".equals(classificationCode) || "004".equals(classificationCode)) {
            return "UNSUPPORTED_CLASSIFICATION_" + classificationCode;
        }
        if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) <= 0) {
            return "INVALID_BASE_PRICE";
        }
        return "FILTERED";
    }

    private BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.trim().replace(",", ""));
    }

    private String toKisMarketCode(String exchangeCode, String priceExchangeCode) {
        String exchange = exchangeCode == null ? "" : exchangeCode.toUpperCase(Locale.ROOT);
        String priceExchange = priceExchangeCode == null ? "" : priceExchangeCode.toUpperCase(Locale.ROOT);
        if ("NASD".equals(exchange) || "NAS".equals(priceExchange)) {
            return "nas";
        }
        if ("NYSE".equals(exchange) || "NYS".equals(priceExchange)) {
            return "nys";
        }
        if ("AMEX".equals(exchange) || "AMS".equals(priceExchange)) {
            return "ams";
        }
        return null;
    }
}
