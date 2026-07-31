package com.won.autoinvestor.common.kis;

import com.won.autoinvestor.common.util.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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
public class KisDomesticStockMasterProvider implements DomesticStockMasterProvider {

    private static final Logger logger = LoggerFactory.getLogger(KisDomesticStockMasterProvider.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final Charset CP949 = Charset.forName("CP949");
    private static final String BASE_URL = "https://new.real.download.dws.co.kr/common/master/";
    private static final int[] KOSPI_WIDTHS = {
            2, 1, 4, 4, 4,
            1, 1, 1, 1, 1,
            1, 1, 1, 1, 1,
            1, 1, 1, 1, 1,
            1, 1, 1, 1, 1,
            1, 1, 1, 1, 1,
            1, 9, 5, 5, 1,
            1, 1, 2, 1, 1,
            1, 2, 2, 2, 3,
            1, 3, 12, 12, 8,
            15, 21, 2, 7, 1,
            1, 1, 1, 1, 9,
            9, 9, 5, 9, 8,
            9, 3, 1, 1, 1
    };
    private static final int[] KOSDAQ_WIDTHS = {
            2, 1,
            4, 4, 4, 1, 1,
            1, 1, 1, 1, 1,
            1, 1, 1, 1, 1,
            1, 1, 1, 1, 1,
            1, 1, 1, 1, 9,
            5, 5, 1, 1, 1,
            2, 1, 1, 1, 2,
            2, 2, 3, 1, 3,
            12, 12, 8, 15, 21,
            2, 7, 1, 1, 1,
            1, 9, 9, 9, 5,
            9, 8, 9, 3, 1,
            1, 1
    };

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public List<Map<String, Object>> fetch(String marketCode) {
        List<String> markets = markets(marketCode);
        List<Map<String, Object>> result = new ArrayList<>();
        for (String market : markets) {
            result.addAll(downloadAndParse(market));
        }
        return result;
    }

    private List<String> markets(String marketCode) {
        if ("KOSPI".equalsIgnoreCase(marketCode)) {
            return List.of("KOSPI");
        }
        if ("KOSDAQ".equalsIgnoreCase(marketCode)) {
            return List.of("KOSDAQ");
        }
        return List.of("KOSPI", "KOSDAQ");
    }

    private List<Map<String, Object>> downloadAndParse(String marketCode) {
        String lower = marketCode.toLowerCase(Locale.ROOT);
        String url = BASE_URL + lower + "_code.mst.zip";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logger.warn("domestic stock master download failed. marketCode={}, status={}", marketCode, response.statusCode());
                return List.of();
            }
            return parseZip(response.body(), marketCode);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while downloading domestic stock master", e);
        } catch (Exception e) {
            logger.warn("domestic stock master download failed. marketCode={}, message={}", marketCode, e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> parseZip(byte[] zipBytes, String marketCode) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes), CP949)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith(".mst")) {
                    String content = new String(zipInputStream.readAllBytes(), CP949);
                    return parseContent(content, marketCode);
                }
            }
        }
        return List.of();
    }

    private List<Map<String, Object>> parseContent(String content, String marketCode) {
        String now = OffsetDateTime.now().format(TIME_FORMATTER);
        List<Map<String, Object>> masters = new ArrayList<>();
        for (String row : content.split("\\R")) {
            if (row == null || row.isBlank()) {
                continue;
            }
            int metadataLength = "KOSPI".equalsIgnoreCase(marketCode) ? 228 : 222;
            if (row.length() <= metadataLength + 21) {
                continue;
            }
            String part1 = row.substring(0, row.length() - metadataLength);
            String part2 = row.substring(row.length() - metadataLength);
            String symbol = slice(part1, 0, 9);
            String standardCode = slice(part1, 9, 21);
            String stockName = part1.substring(Math.min(21, part1.length())).trim();
            if (symbol.isBlank()) {
                continue;
            }
            Map<String, Object> metadata = "KOSPI".equalsIgnoreCase(marketCode)
                    ? parseKospiMetadata(part2)
                    : parseKosdaqMetadata(part2);
            masters.add(MapUtils.map(
                    "id", null,
                    "symbol", symbol,
                    "stockName", stockName,
                    "marketCode", marketCode.toUpperCase(Locale.ROOT),
                    "standardCode", standardCode,
                    "securityGroupCode", MapUtils.string(metadata, "securityGroupCode"),
                    "etp", MapUtils.bool(metadata, "etp"),
                    "spac", MapUtils.bool(metadata, "spac"),
                    "tradable", MapUtils.bool(metadata, "tradable"),
                    "active", MapUtils.bool(metadata, "active"),
                    "lastPrice", MapUtils.decimal(metadata, "basePrice"),
                    "marketCap", MapUtils.decimal(metadata, "marketCap"),
                    "tradingVolume", MapUtils.decimal(metadata, "previousVolume"),
                    "excludedReason", MapUtils.string(metadata, "excludedReason"),
                    "createdAt", now,
                    "updatedAt", now,
                    "lastSyncedAt", now
            ));
        }
        logger.info("domestic stock master parsed. marketCode={}, count={}", marketCode, masters.size());
        return masters;
    }

    private Map<String, Object> parseKospiMetadata(String value) {
        List<String> fields = splitFixed(value, KOSPI_WIDTHS);
        String groupCode = field(fields, 0);
        String etp = field(fields, 12);
        String spac = field(fields, 19);
        BigDecimal basePrice = decimal(field(fields, 31));
        String halted = field(fields, 34);
        String liquidation = field(fields, 35);
        String managed = field(fields, 36);
        BigDecimal previousVolume = decimal(field(fields, 47));
        BigDecimal marketCap = decimal(field(fields, 65));
        return metadata(groupCode, etp, spac, halted, liquidation, managed, basePrice, previousVolume, marketCap);
    }

    private Map<String, Object> parseKosdaqMetadata(String value) {
        List<String> fields = splitFixed(value, KOSDAQ_WIDTHS);
        String groupCode = field(fields, 0);
        String lowLiquidity = field(fields, 6);
        String etp = field(fields, 8);
        String spac = field(fields, 18);
        BigDecimal basePrice = decimal(field(fields, 26));
        String halted = field(fields, 29);
        String liquidation = field(fields, 30);
        String managed = field(fields, 31);
        BigDecimal previousVolume = decimal(field(fields, 41));
        BigDecimal marketCap = decimal(field(fields, 59));
        Map<String, Object> metadata = metadata(groupCode, etp, spac, halted, liquidation, managed, basePrice, previousVolume, marketCap);
        if ("Y".equalsIgnoreCase(lowLiquidity)) {
            return withExcludedReason(metadata, "LOW_LIQUIDITY");
        }
        return metadata;
    }

    private Map<String, Object> metadata(String groupCode,
                                         String etp,
                                         String spac,
                                         String halted,
                                         String liquidation,
                                         String managed,
                                         BigDecimal basePrice,
                                         BigDecimal previousVolume,
                                         BigDecimal marketCap) {
        List<String> reasons = new ArrayList<>();
        if ("Y".equalsIgnoreCase(halted)) {
            reasons.add("TRADING_HALTED");
        }
        if ("Y".equalsIgnoreCase(liquidation)) {
            reasons.add("LIQUIDATION_TRADING");
        }
        if ("Y".equalsIgnoreCase(managed)) {
            reasons.add("MANAGED_STOCK");
        }
        if ("Y".equalsIgnoreCase(spac)) {
            reasons.add("SPAC");
        }
        boolean active = reasons.isEmpty();
        boolean tradable = active && basePrice.signum() > 0;
        return MapUtils.map(
                "securityGroupCode", groupCode,
                "etp", "Y".equalsIgnoreCase(etp),
                "spac", "Y".equalsIgnoreCase(spac),
                "tradable", tradable,
                "active", active,
                "basePrice", basePrice,
                "marketCap", marketCap,
                "previousVolume", previousVolume,
                "excludedReason", reasons.isEmpty() ? null : String.join(",", reasons)
        );
    }

    private String slice(String value, int start, int end) {
        if (value.length() <= start) {
            return "";
        }
        return value.substring(start, Math.min(end, value.length())).trim();
    }

    private BigDecimal decimal(String value) {
        String normalized = value == null ? "" : value.replace(",", "").trim();
        if (normalized.isBlank() || !normalized.matches("-?\\d+(\\.\\d+)?")) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(normalized);
    }

    private List<String> splitFixed(String value, int[] widths) {
        List<String> fields = new ArrayList<>();
        int offset = 0;
        for (int width : widths) {
            int end = Math.min(offset + width, value.length());
            fields.add(value.substring(offset, end).trim());
            offset = end;
        }
        return fields;
    }

    private String field(List<String> fields, int index) {
        if (index < 0 || index >= fields.size()) {
            return "";
        }
        return fields.get(index);
    }

    private Map<String, Object> withExcludedReason(Map<String, Object> metadata, String reason) {
        String excludedReason = MapUtils.string(metadata, "excludedReason");
        String mergedReason = excludedReason == null || excludedReason.isBlank()
                ? reason
                : excludedReason + "," + reason;
        metadata.put("tradable", false);
        metadata.put("active", false);
        metadata.put("excludedReason", mergedReason);
        return metadata;
    }

}
