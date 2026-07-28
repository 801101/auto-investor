package com.won.autoinvestor.kis;

import com.fasterxml.jackson.databind.JsonNode;
import com.won.autoinvestor.broker.BrokerClient;
import com.won.autoinvestor.broker.domain.AccessToken;
import com.won.autoinvestor.broker.domain.AccountBalance;
import com.won.autoinvestor.broker.domain.BrokerHolding;
import com.won.autoinvestor.broker.domain.BrokerOrder;
import com.won.autoinvestor.broker.domain.BuyOrderRequest;
import com.won.autoinvestor.broker.domain.CurrentPrice;
import com.won.autoinvestor.broker.domain.OrderResult;
import com.won.autoinvestor.broker.domain.OrderStatus;
import com.won.autoinvestor.broker.domain.SellOrderRequest;
import com.won.autoinvestor.kis.config.KisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;

@Component
public class KoreaInvestmentBrokerClient implements BrokerClient {

    private static final Logger logger = LoggerFactory.getLogger(KoreaInvestmentBrokerClient.class);

    private final KisProperties kisProperties;
    private final KisAccessTokenManager tokenManager;
    private final RestClient restClient;

    public KoreaInvestmentBrokerClient(KisProperties kisProperties, KisAccessTokenManager tokenManager) {
        this.kisProperties = kisProperties;
        this.tokenManager = tokenManager;
        this.restClient = RestClient.builder()
                .baseUrl(kisProperties.getBaseUrl())
                .build();
    }

    @Override
    public AccessToken issueAccessToken() {
        return tokenManager.getValidToken();
    }

    @Override
    public AccountBalance getAccountBalance() {
        JsonNode response = authenticatedGet(kisProperties.getBalancePath(), kisProperties.getBalanceTrId(), balanceQueryParams());
        JsonNode summary = first(response.path("output2"));
        BigDecimal cashBalance = decimal(summary, "dnca_tot_amt");
        BigDecimal totalValuationAmount = decimal(summary, "tot_evlu_amt");
        logger.info("KIS account balance synchronized. account={}, cashBalance={}, totalValuationAmount={}",
                maskedAccountNumber(), cashBalance, totalValuationAmount);
        return new AccountBalance(cashBalance, totalValuationAmount);
    }

    @Override
    public List<BrokerHolding> getHoldings() {
        JsonNode response = authenticatedGet(kisProperties.getBalancePath(), kisProperties.getBalanceTrId(), balanceQueryParams());
        List<BrokerHolding> holdings = new ArrayList<>();
        JsonNode rows = response.path("output1");
        if (rows.isArray()) {
            for (JsonNode row : rows) {
                BigDecimal quantity = decimal(row, "hldg_qty");
                if (quantity.signum() <= 0) {
                    continue;
                }
                holdings.add(new BrokerHolding(
                        row.path("pdno").asText(),
                        row.path("prdt_name").asText(),
                        quantity,
                        decimal(row, "pchs_avg_pric")
                ));
            }
        }
        logger.info("KIS holdings synchronized. account={}, holdingCount={}", maskedAccountNumber(), holdings.size());
        return holdings;
    }

    @Override
    public CurrentPrice getCurrentPrice(String stockCode) {
        JsonNode response = authenticatedGet(kisProperties.getCurrentPricePath(), kisProperties.getCurrentPriceTrId(), Map.of(
                "FID_COND_MRKT_DIV_CODE", kisProperties.getMarketDivisionCode(),
                "FID_INPUT_ISCD", stockCode
        ));
        BigDecimal price = decimal(response.path("output"), "stck_prpr");
        logger.info("KIS current price synchronized. stockCode={}, price={}", stockCode, price);
        return new CurrentPrice(stockCode, price, OffsetDateTime.now());
    }

    @Override
    public OrderResult buy(BuyOrderRequest request) {
        return order("BUY", kisProperties.getBuyTrId(), request.stockCode(), request.orderQuantity(), request.orderPrice(), request.orderAmount());
    }

    @Override
    public OrderResult sell(SellOrderRequest request) {
        return order("SELL", kisProperties.getSellTrId(), request.stockCode(), request.orderQuantity(), request.orderPrice(), request.orderAmount());
    }

    @Override
    public List<BrokerOrder> getOpenOrders() {
        throw new UnsupportedOperationException("TODO: configure KIS open-orders path and transaction ID from official OpenAPI spec");
    }

    @Override
    public OrderStatus getOrderStatus(String orderId) {
        throw new UnsupportedOperationException("TODO: configure KIS order-status path and transaction ID from official OpenAPI spec");
    }

    private OrderResult order(String orderType,
                              String transactionId,
                              String stockCode,
                              BigDecimal orderQuantity,
                              BigDecimal orderPrice,
                              BigDecimal orderAmount) {
        JsonNode response = authenticatedPost(kisProperties.getOrderCashPath(), transactionId, Map.of(
                "CANO", kisProperties.getAccountNumber(),
                "ACNT_PRDT_CD", kisProperties.getAccountProductCode(),
                "PDNO", stockCode,
                "ORD_DVSN", kisProperties.getOrderDivision(),
                "ORD_QTY", orderQuantity.toPlainString(),
                "ORD_UNPR", orderPrice == null ? "0" : orderPrice.toPlainString()
        ));
        JsonNode output = response.path("output");
        String brokerOrderId = output.path("ODNO").asText(null);
        String message = response.path("msg1").asText(orderType + " order requested");
        logger.info("KIS {} order response. stockCode={}, brokerOrderId={}, message={}", orderType, stockCode, brokerOrderId, message);
        return OrderResult.accepted(brokerOrderId, response.path("rt_cd").asText("UNKNOWN"), message);
    }

    private Map<String, String> balanceQueryParams() {
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

    private JsonNode authenticatedGet(String path, String transactionId, Map<String, String> queryParams) {
        AccessToken accessToken = tokenManager.getValidToken();
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
        AccessToken accessToken = tokenManager.getValidToken();
        return restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> applyCommonHeaders(headers, accessToken, transactionId))
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    private void applyCommonHeaders(org.springframework.http.HttpHeaders headers, AccessToken accessToken, String transactionId) {
        headers.setBearerAuth(accessToken.token());
        headers.set("appkey", kisProperties.getAppKey());
        headers.set("appsecret", kisProperties.getAppSecret());
        headers.set("tr_id", transactionId);
        headers.set("custtype", kisProperties.getCustomerType());
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

    private String maskedAccountNumber() {
        String accountNumber = kisProperties.getAccountNumber();
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }
}
