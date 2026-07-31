package com.won.autoinvestor.common.kis;

import com.won.autoinvestor.common.util.MapUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Properties;

@Component
public class KisAccessTokenManager {

    private static final Logger logger = LoggerFactory.getLogger(KisAccessTokenManager.class);

    private final KisProperties kisProperties;
    private final RestClient restClient;

    private Map<String, Object> cachedToken;

    public KisAccessTokenManager(KisProperties kisProperties) {
        this.kisProperties = kisProperties;
        this.restClient = RestClient.builder()
                .baseUrl(kisProperties.getBaseUrl())
                .build();
    }

    public synchronized Map<String, Object> getValidToken() {
        if (cachedToken != null && expiresAt(cachedToken).isAfter(OffsetDateTime.now().plusMinutes(5))) {
            return cachedToken;
        }

        if (!kisProperties.isConfigured()) {
            throw new IllegalStateException("KIS credentials are not configured. Set KIS_APP_KEY, KIS_APP_SECRET, KIS_ACCOUNT_NUMBER, and KIS_ACCOUNT_PRODUCT_CODE.");
        }

        Map<String, Object> diskToken = loadCachedToken();
        if (diskToken != null) {
            cachedToken = diskToken;
            logger.info("KIS access token loaded from local cache. expiresAt={}", expiresAt(diskToken));
            return cachedToken;
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
        cachedToken = MapUtils.map("token", token, "expiresAt", expiresAt);
        saveCachedToken(cachedToken);
        logger.info("KIS access token issued. expiresAt={}", expiresAt);
        return cachedToken;
    }

    private Map<String, Object> loadCachedToken() {
        Path path = tokenCachePath();
        if (!Files.isRegularFile(path)) {
            return null;
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
            if (!fingerprint().equals(properties.getProperty("fingerprint"))) {
                return null;
            }

            String token = properties.getProperty("accessToken", "");
            String expiresAtValue = properties.getProperty("expiresAt", "");
            if (token.isBlank() || expiresAtValue.isBlank()) {
                return null;
            }

            OffsetDateTime expiresAt = OffsetDateTime.parse(expiresAtValue);
            if (!expiresAt.isAfter(OffsetDateTime.now().plusMinutes(5))) {
                return null;
            }
            return MapUtils.map("token", token, "expiresAt", expiresAt);
        } catch (RuntimeException | IOException e) {
            logger.warn("failed to read KIS access token cache. cache will be ignored.");
            return null;
        }
    }

    private void saveCachedToken(Map<String, Object> accessToken) {
        Path path = tokenCachePath();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Properties properties = new Properties();
            properties.setProperty("fingerprint", fingerprint());
            properties.setProperty("accessToken", MapUtils.string(accessToken, "token"));
            properties.setProperty("expiresAt", expiresAt(accessToken).toString());
            try (OutputStream outputStream = Files.newOutputStream(path)) {
                properties.store(outputStream, "KIS local access token cache. Do not commit.");
            }
        } catch (RuntimeException | IOException e) {
            logger.warn("failed to save KIS access token cache. token remains in memory only.");
        }
    }

    private Path tokenCachePath() {
        return Path.of(kisProperties.getAccessTokenCachePath()).toAbsolutePath().normalize();
    }

    private String fingerprint() {
        String source = kisProperties.getAccountMode()
                + "|" + kisProperties.getBaseUrl()
                + "|" + kisProperties.getAppKey()
                + "|" + kisProperties.getAccountNumber()
                + "|" + kisProperties.getAccountProductCode();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 12 && i < digest.length; i++) {
                builder.append(String.format("%02x", digest[i]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
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

    private OffsetDateTime expiresAt(Map<String, Object> accessToken) {
        return MapUtils.offsetDateTime(accessToken, "expiresAt");
    }
}
