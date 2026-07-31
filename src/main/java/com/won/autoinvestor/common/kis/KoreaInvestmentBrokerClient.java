package com.won.autoinvestor.common.kis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.won.autoinvestor.common.util.MapUtils;
import com.won.autoinvestor.common.config.InvestmentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;

@Component
public class KoreaInvestmentBrokerClient implements BrokerClient {

    private static final Logger logger = LoggerFactory.getLogger(KoreaInvestmentBrokerClient.class);
    private final KisProperties kisProperties;
    private final InvestmentProperties investmentProperties;
    private final KisAccessTokenManager tokenManager;
    private final RestClient restClient;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private JsonNode cachedBalanceResponse;
    private OffsetDateTime cachedBalanceResponseAt;
    private long lastApiRequestAtMillis = 0L;

    public KoreaInvestmentBrokerClient(KisProperties kisProperties,
                                       InvestmentProperties investmentProperties,
                                       KisAccessTokenManager tokenManager) {
        this.kisProperties = kisProperties;
        this.investmentProperties = investmentProperties;
        this.tokenManager = tokenManager;
        this.restClient = RestClient.builder()
                .baseUrl(kisProperties.getBaseUrl())
                .build();
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Map<String, Object> issueAccessToken() {
        return tokenManager.getValidToken();
    }

    @Override
    public Map<String, Object> getAccountBalance() {
        JsonNode response = balanceResponse();
        JsonNode summary = first(response.path("output2"));
        BigDecimal cashBalance = isOverseasMarket()
                ? decimalFirst(summary, "frcr_dncl_amt_2", "frcr_buy_amt_smtl1", "cashBalance")
                : decimal(summary, "dnca_tot_amt");
        BigDecimal totalValuationAmount = isOverseasMarket()
                ? decimalFirst(summary, "tot_evlu_pfls_amt", "ovrs_tot_pfls", "frcr_evlu_tota", "totalValuationAmount")
                : decimal(summary, "tot_evlu_amt");
        logger.info("KIS account balance synchronized. account={}, cashBalance={}, totalValuationAmount={}",
                maskedAccountNumber(), cashBalance, totalValuationAmount);
        return MapUtils.map("cashBalance", cashBalance, "totalValuationAmount", totalValuationAmount);
    }

    @Override
    public Map<String, Object> getBuyableBalance(String stockCode, BigDecimal orderPrice) {
        if (!isOverseasMarket()) {
            return getAccountBalance();
        }
        JsonNode response = authenticatedGet(kisProperties.getOverseasBuyableAmountPath(), kisProperties.getOverseasBuyableAmountTrId(), Map.of(
                "CANO", kisProperties.getAccountNumber(),
                "ACNT_PRDT_CD", kisProperties.getAccountProductCode(),
                "OVRS_EXCG_CD", investmentProperties.getOverseasExchangeCode(),
                "OVRS_ORD_UNPR", plain(orderPrice),
                "ITEM_CD", stockCode
        ));
        JsonNode output = first(response.path("output"));
        BigDecimal buyableAmount = decimalFirst(output,
                "ord_psbl_frcr_amt",
                "ovrs_ord_psbl_amt",
                "frcr_ord_psbl_amt1",
                "echm_af_ord_psbl_amt");
        logger.info("KIS overseas buyable amount synchronized. account={}, stockCode={}, buyableAmount={}",
                maskedAccountNumber(), stockCode, buyableAmount);
        return MapUtils.map("cashBalance", buyableAmount, "totalValuationAmount", buyableAmount);
    }

    @Override
    public List<Map<String, Object>> getHoldings() {
        JsonNode response = balanceResponse();
        List<Map<String, Object>> holdings = new ArrayList<>();
        JsonNode rows = response.path("output1");
        if (rows.isArray()) {
            for (JsonNode row : rows) {
                BigDecimal quantity = isOverseasMarket()
                        ? decimalFirst(row, "ovrs_cblc_qty", "hldg_qty", "ord_psbl_qty")
                        : decimal(row, "hldg_qty");
                if (quantity.signum() <= 0) {
                    continue;
                }
                holdings.add(MapUtils.map(
                        "stockCode", textFirst(row, "ovrs_pdno", "pdno"),
                        "stockName", textFirst(row, "ovrs_item_name", "prdt_name"),
                        "quantity", quantity,
                        "averagePrice", decimalFirst(row, "pchs_avg_pric", "avg_unpr3")
                ));
            }
        }
        logger.info("KIS holdings synchronized. account={}, holdingCount={}", maskedAccountNumber(), holdings.size());
        return holdings;
    }

    @Override
    public Map<String, Object> getCurrentPrice(String stockCode) {
        JsonNode response;
        BigDecimal price;
        if (isOverseasMarket()) {
            response = authenticatedGet(kisProperties.getOverseasPricePath(), kisProperties.getOverseasPriceTrId(), Map.of(
                    "AUTH", "",
                    "EXCD", investmentProperties.getOverseasPriceExchangeCode(),
                    "SYMB", stockCode
            ));
            price = decimalFirst(response.path("output"), "last", "stck_prpr", "ovrs_nmix_prpr");
        } else {
            response = authenticatedGet(kisProperties.getCurrentPricePath(), kisProperties.getCurrentPriceTrId(), Map.of(
                    "FID_COND_MRKT_DIV_CODE", kisProperties.getMarketDivisionCode(),
                    "FID_INPUT_ISCD", stockCode
            ));
            price = decimal(response.path("output"), "stck_prpr");
        }
        logger.info("KIS current price synchronized. stockCode={}, price={}", stockCode, price);
        return MapUtils.map("stockCode", stockCode, "price", price, "receivedAt", OffsetDateTime.now());
    }

    @Override
    public Map<String, Object> buy(Map<String, Object> request) {
        if (isOverseasMarket()) {
            return overseasOrder("BUY", overseasBuyTrId(), MapUtils.string(request, "stockCode"),
                    MapUtils.decimal(request, "orderQuantity"), MapUtils.decimal(request, "orderPrice"));
        }
        return order("BUY", kisProperties.getBuyTrId(), MapUtils.string(request, "stockCode"),
                MapUtils.decimal(request, "orderQuantity"), MapUtils.decimal(request, "orderPrice"), MapUtils.decimal(request, "orderAmount"));
    }

    @Override
    public Map<String, Object> sell(Map<String, Object> request) {
        if (isOverseasMarket()) {
            return overseasOrder("SELL", overseasSellTrId(), MapUtils.string(request, "stockCode"),
                    MapUtils.decimal(request, "orderQuantity"), MapUtils.decimal(request, "orderPrice"));
        }
        return order("SELL", kisProperties.getSellTrId(), MapUtils.string(request, "stockCode"),
                MapUtils.decimal(request, "orderQuantity"), MapUtils.decimal(request, "orderPrice"), MapUtils.decimal(request, "orderAmount"));
    }

    private Map<String, Object> order(String orderType,
                                      String transactionId,
                                      String stockCode,
                                      BigDecimal orderQuantity,
                                      BigDecimal orderPrice,
                                      BigDecimal orderAmount) {
        JsonNode response;
        try {
            response = authenticatedPost(kisProperties.getOrderCashPath(), transactionId, Map.of(
                    "CANO", kisProperties.getAccountNumber(),
                    "ACNT_PRDT_CD", kisProperties.getAccountProductCode(),
                    "PDNO", stockCode,
                    "ORD_DVSN", kisProperties.getOrderDivision(),
                    "ORD_QTY", orderQuantity.toPlainString(),
                    "ORD_UNPR", orderPrice == null ? "0" : orderPrice.toPlainString()
            ), true);
        } catch (RestClientResponseException e) {
            return rejected(rejectedMessage(e));
        }
        JsonNode output = response.path("output");
        String brokerOrderId = output.path("ODNO").asText(null);
        String message = response.path("msg1").asText(orderType + " order requested");
        String status = response.path("rt_cd").asText("UNKNOWN");
        logger.info("KIS {} order response. stockCode={}, brokerOrderId={}, status={}, message={}",
                orderType, stockCode, brokerOrderId, status, message);
        if (!"0".equals(status)) {
            return rejected(orderResponseSummary(response));
        }
        return accepted(brokerOrderId, status, message);
    }

    private Map<String, Object> overseasOrder(String orderType,
                                              String transactionId,
                                              String stockCode,
                                              BigDecimal orderQuantity,
                                              BigDecimal orderPrice) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("CANO", kisProperties.getAccountNumber());
        body.put("ACNT_PRDT_CD", kisProperties.getAccountProductCode());
        body.put("OVRS_EXCG_CD", investmentProperties.getOverseasExchangeCode());
        body.put("PDNO", stockCode);
        body.put("ORD_QTY", plain(orderQuantity));
        body.put("OVRS_ORD_UNPR", plain(orderPrice));
        body.put("CTAC_TLNO", "");
        body.put("MGCO_APTM_ODNO", "");
        body.put("SLL_TYPE", "SELL".equals(orderType) ? "00" : "");
        body.put("ORD_SVR_DVSN_CD", "0");
        body.put("ORD_DVSN", "00");

        JsonNode response;
        try {
            response = authenticatedPostJsonString(kisProperties.getOverseasOrderPath(), transactionId, body);
        } catch (RestClientResponseException e) {
            return rejected(rejectedMessage(e));
        } catch (RuntimeException e) {
            return rejected(e.getMessage());
        }
        JsonNode output = response.path("output");
        String brokerOrderId = textFirst(output, "ODNO", "odno");
        String message = response.path("msg1").asText(orderType + " overseas order requested");
        String status = response.path("rt_cd").asText("UNKNOWN");
        logger.info("KIS overseas {} order response. stockCode={}, brokerOrderId={}, status={}, message={}",
                orderType, stockCode, brokerOrderId, status, message);
        if (!"0".equals(status)) {
            return rejected(orderResponseSummary(response));
        }
        return accepted(brokerOrderId, status, message);
    }

    private Map<String, Object> accepted(String brokerOrderId, String status, String message) {
        return MapUtils.map("accepted", true, "brokerOrderId", brokerOrderId, "status", status, "message", message);
    }

    private Map<String, Object> rejected(String message) {
        return MapUtils.map("accepted", false, "brokerOrderId", null, "status", "REJECTED", "message", message);
    }

    private String orderResponseSummary(JsonNode response) {
        if (response == null) {
            return "{}";
        }
        return "{\"rt_cd\":\"" + escapeJson(response.path("rt_cd").asText("")) + "\","
                + "\"msg_cd\":\"" + escapeJson(response.path("msg_cd").asText("")) + "\","
                + "\"msg1\":\"" + escapeJson(response.path("msg1").asText("")) + "\"}";
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Map<String, String> balanceQueryParams() {
        if (isOverseasMarket()) {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("CANO", kisProperties.getAccountNumber());
            params.put("ACNT_PRDT_CD", kisProperties.getAccountProductCode());
            params.put("OVRS_EXCG_CD", investmentProperties.getOverseasExchangeCode());
            params.put("TR_CRCY_CD", investmentProperties.getOverseasCurrencyCode());
            params.put("CTX_AREA_FK200", "");
            params.put("CTX_AREA_NK200", "");
            return params;
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("CANO", kisProperties.getAccountNumber());
        params.put("ACNT_PRDT_CD", kisProperties.getAccountProductCode());
        params.put("AFHR_FLPR_YN", "N");
        params.put("OFL_YN", "");
        params.put("INQR_DVSN", "02");
        params.put("UNPR_DVSN", "01");
        params.put("FUND_STTL_ICLD_YN", "N");
        params.put("FNCG_AMT_AUTO_RDPT_YN", "N");
        params.put("PRCS_DVSN", "01");
        params.put("CTX_AREA_FK100", "");
        params.put("CTX_AREA_NK100", "");
        return params;
    }

    private synchronized JsonNode balanceResponse() {
        OffsetDateTime now = OffsetDateTime.now();
        if (cachedBalanceResponse != null
                && cachedBalanceResponseAt != null
                && cachedBalanceResponseAt.isAfter(now.minusSeconds(2))) {
            return cachedBalanceResponse;
        }

        cachedBalanceResponse = isOverseasMarket()
                ? authenticatedGet(kisProperties.getOverseasBalancePath(), kisProperties.getOverseasBalanceTrId(), balanceQueryParams())
                : authenticatedGet(kisProperties.getBalancePath(), kisProperties.getBalanceTrId(), balanceQueryParams());
        cachedBalanceResponseAt = now;
        return cachedBalanceResponse;
    }

    private JsonNode authenticatedGet(String path, String transactionId, Map<String, String> queryParams) {
        Map<String, Object> accessToken = tokenManager.getValidToken();
        throttleApiRequest();
        return restClient.get()
                .uri(builder -> {
                    var uriBuilder = builder.path(path);
                    queryParams.forEach(uriBuilder::queryParam);
                    return uriBuilder.build();
                })
                .headers(headers -> applyCommonHeaders(headers, accessToken, transactionId))
                .retrieve()
                .body(JsonNode.class);
    }

    private JsonNode authenticatedPost(String path, String transactionId, Map<String, String> body) {
        return authenticatedPost(path, transactionId, body, false);
    }

    private JsonNode authenticatedPost(String path, String transactionId, Map<String, String> body, boolean includeHashKey) {
        Map<String, Object> accessToken = tokenManager.getValidToken();
        String hashKey = null;
        if (includeHashKey) {
            hashKey = issueHashKey(accessToken, body);
        }
        throttleApiRequest();
        String finalHashKey = hashKey;
        return restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    applyCommonHeaders(headers, accessToken, transactionId);
                    if (finalHashKey != null) {
                        headers.set("hashkey", finalHashKey);
                    }
                })
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    private JsonNode authenticatedPostJsonString(String path, String transactionId, Map<String, String> body) {
        Map<String, Object> accessToken = tokenManager.getValidToken();
        throttleApiRequest();
        try {
            String requestBody = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(kisProperties.getBaseUrl() + path))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/plain")
                    .header("authorization", "Bearer " + MapUtils.string(accessToken, "token"))
                    .header("appkey", kisProperties.getAppKey())
                    .header("appsecret", kisProperties.getAppSecret())
                    .header("tr_id", transactionId)
                    .header("tr_cont", "")
                    .header("custtype", kisProperties.getCustomerType())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(trimResponse(response.body()));
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling KIS overseas order API", e);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private JsonNode hashKeyPost(Map<String, Object> accessToken, Map<String, String> body) {
        throttleApiRequest();
        return restClient.post()
                .uri("/uapi/hashkey")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    headers.set("appkey", kisProperties.getAppKey());
                    headers.set("appsecret", kisProperties.getAppSecret());
                    headers.set("authorization", "Bearer " + MapUtils.string(accessToken, "token"));
                    headers.set("custtype", kisProperties.getCustomerType());
                })
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    private String issueHashKey(Map<String, Object> accessToken, Map<String, String> body) {
        JsonNode response = hashKeyPost(accessToken, body);
        String hash = textFirst(response, "HASH", "hash");
        if (hash == null || hash.isBlank()) {
            throw new IllegalStateException("KIS hashkey response did not contain HASH");
        }
        return hash;
    }

    private synchronized void throttleApiRequest() {
        long now = System.currentTimeMillis();
        long waitMillis = 2000L - (now - lastApiRequestAtMillis);
        if (waitMillis > 0) {
            try {
                Thread.sleep(waitMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while throttling KIS API request", e);
            }
        }
        lastApiRequestAtMillis = System.currentTimeMillis();
    }

    private void applyCommonHeaders(org.springframework.http.HttpHeaders headers, Map<String, Object> accessToken, String transactionId) {
        headers.set("authorization", "Bearer " + MapUtils.string(accessToken, "token"));
        headers.set("appkey", kisProperties.getAppKey());
        headers.set("appsecret", kisProperties.getAppSecret());
        headers.set("tr_id", transactionId);
        headers.set("tr_cont", "");
        headers.set("custtype", kisProperties.getCustomerType());
        headers.set("User-Agent", "auto-investor-local/1.0");
        headers.setAccept(List.of(MediaType.TEXT_PLAIN));
    }

    private JsonNode first(JsonNode node) {
        if (node != null && node.isArray() && !node.isEmpty()) {
            return node.get(0);
        }
        return node == null ? com.fasterxml.jackson.databind.node.MissingNode.getInstance() : node;
    }

    private BigDecimal decimal(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText("0").replace(",", "").trim();
        if (value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value);
    }

    private BigDecimal decimalFirst(JsonNode node, String... fieldNames) {
        if (node == null || fieldNames == null) {
            return BigDecimal.ZERO;
        }
        for (String fieldName : fieldNames) {
            JsonNode valueNode = node.path(fieldName);
            if (valueNode.isMissingNode() || valueNode.isNull()) {
                continue;
            }
            String value = valueNode.asText("").replace(",", "").trim();
            if (!value.isBlank()) {
                return new BigDecimal(value);
            }
        }
        return BigDecimal.ZERO;
    }

    private String textFirst(JsonNode node, String... fieldNames) {
        if (node == null || fieldNames == null) {
            return "";
        }
        for (String fieldName : fieldNames) {
            String value = node.path(fieldName).asText("").trim();
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String plain(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private String rejectedMessage(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return e.getMessage();
        }
        return trimResponse(body);
    }

    private String trimResponse(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        return body.length() > 500 ? body.substring(0, 500) : body;
    }

    private boolean isOverseasMarket() {
        return "OVERSEAS".equalsIgnoreCase(investmentProperties.getMarketType());
    }

    private String overseasBuyTrId() {
        return kisProperties.getOverseasBuyTrId();
    }

    private String overseasSellTrId() {
        return kisProperties.getOverseasSellTrId();
    }

    private String maskedAccountNumber() {
        String accountNumber = kisProperties.getAccountNumber();
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }
}
