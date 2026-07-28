package com.won.autoinvestor.kis;

import com.won.autoinvestor.broker.domain.AccessToken;
import com.won.autoinvestor.kis.config.KisProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
public class KisAccessTokenManager {

    private static final Logger logger = LoggerFactory.getLogger(KisAccessTokenManager.class);

    private final KisProperties kisProperties;
    private final RestClient restClient;

    private AccessToken cachedToken;

    public KisAccessTokenManager(KisProperties kisProperties) {
        this.kisProperties = kisProperties;
        this.restClient = RestClient.builder()
                .baseUrl(kisProperties.getBaseUrl())
                .build();
    }

    public synchronized AccessToken getValidToken() {
        if (cachedToken != null && cachedToken.expiresAt().isAfter(OffsetDateTime.now().plusMinutes(5))) {
            return cachedToken;
        }

        if (!kisProperties.isConfigured()) {
            throw new IllegalStateException("KIS credentials are not configured. Set KIS_APP_KEY, KIS_APP_SECRET, KIS_ACCOUNT_NUMBER, and KIS_ACCOUNT_PRODUCT_CODE.");
        }

        JsonNode response = restClient.post()
                .uri(kisProperties.getTokenPath())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "grant_type", "client_credentials",
                        "appkey", kisProperties.getAppKey(),
                        "appsecret", kisProperties.getAppSecret()
                ))
                .retrieve()
                .body(JsonNode.class);

        if (response == null || response.path("access_token").asText("").isBlank()) {
            throw new IllegalStateException("KIS access token response did not contain access_token");
        }

        String token = response.path("access_token").asText();
        OffsetDateTime expiresAt = resolveExpiresAt(response);
        cachedToken = new AccessToken(token, expiresAt);
        logger.info("KIS access token issued. expiresAt={}", expiresAt);
        return cachedToken;
    }

    private OffsetDateTime resolveExpiresAt(JsonNode response) {
        String explicitExpiration = response.path("access_token_token_expired").asText("");
        if (!explicitExpiration.isBlank()) {
            try {
                return LocalDateTime.parse(explicitExpiration, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        .atZone(ZoneId.systemDefault())
                        .toOffsetDateTime();
            } catch (RuntimeException ignored) {
                logger.warn("failed to parse KIS token expiration. falling back to expires_in");
            }
        }

        long expiresIn = response.path("expires_in").asLong(86400L);
        return OffsetDateTime.now().plusSeconds(Math.max(60L, expiresIn));
    }
}
