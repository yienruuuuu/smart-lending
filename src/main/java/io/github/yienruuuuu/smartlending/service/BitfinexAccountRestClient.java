package io.github.yienruuuuu.smartlending.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.yienruuuuu.smartlending.config.BitfinexProperties;
import io.github.yienruuuuu.smartlending.model.BitfinexBalanceHistoryEntry;
import io.github.yienruuuuu.smartlending.model.BitfinexMovementHistoryEntry;
import io.github.yienruuuuu.smartlending.model.WalletBalanceDto;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * 負責呼叫 Bitfinex 私有 authenticated REST API，處理簽章、nonce 與基礎錯誤轉換。
 */
@Component
public class BitfinexAccountRestClient {

    private static final Logger log = LoggerFactory.getLogger(BitfinexAccountRestClient.class);

    private final BitfinexProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final AtomicLong nonceCounter = new AtomicLong(System.currentTimeMillis() * 1000L);
    private final String restBaseUrl;

    public BitfinexAccountRestClient(
            BitfinexProperties properties,
            ObjectMapper objectMapper,
            RestTemplateBuilder restTemplateBuilder,
            @Value("${bitfinex.rest-base-url:https://api.bitfinex.com}") String restBaseUrl
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
        this.restBaseUrl = restBaseUrl;
    }

    /**
     * 查詢帳戶 wallets，並轉成較易閱讀的 DTO。
     */
    public List<WalletBalanceDto> getWallets() {
        JsonNode root = postAuthenticated("v2/auth/r/wallets", "{}");
        if (!root.isArray()) {
            throw new IllegalStateException("Unexpected wallets response: " + root);
        }

        List<WalletBalanceDto> wallets = new ArrayList<>();
        for (JsonNode walletNode : root) {
            wallets.add(new WalletBalanceDto(
                    walletNode.path(0).asText(),
                    walletNode.path(1).asText(),
                    decimalOrZero(walletNode, 2),
                    decimalOrZero(walletNode, 3),
                    decimalOrZero(walletNode, 4)
            ));
        }
        log.info("已從 Bitfinex REST API 取得 wallet 資料。count={}", wallets.size());
        return wallets;
    }

    public List<BitfinexMovementHistoryEntry> getMovementHistory(String currency, Instant since, Instant until) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("currency", currency);
        body.put("since", since.toEpochMilli());
        body.put("until", until.toEpochMilli());
        JsonNode root = postAuthenticatedV1("v1/history/movements", body);
        if (!root.isArray()) {
            throw new IllegalStateException("Unexpected movements response: " + root);
        }

        List<BitfinexMovementHistoryEntry> entries = new ArrayList<>();
        for (JsonNode item : root) {
            entries.add(new BitfinexMovementHistoryEntry(
                    item.path("id").asText(),
                    item.path("currency").asText(),
                    item.path("method").asText(null),
                    item.path("type").asText(null),
                    item.path("status").asText(null),
                    decimalOrZero(item, "amount").abs(),
                    decimalOrZero(item, "fees"),
                    instantOrNull(item.path("timestamp")),
                    item.path("txid").asText(null),
                    item.path("address").asText(null)
            ));
        }
        log.info("已從 Bitfinex v1 API 取得 movement history。currency={}, count={}", currency, entries.size());
        return entries;
    }

    public List<BitfinexBalanceHistoryEntry> getBalanceHistory(String currency, Instant since, Instant until) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("currency", currency);
        body.put("since", since.toEpochMilli());
        body.put("until", until.toEpochMilli());
        JsonNode root = postAuthenticatedV1("v1/history", body);
        if (!root.isArray()) {
            throw new IllegalStateException("Unexpected balance history response: " + root);
        }

        List<BitfinexBalanceHistoryEntry> entries = new ArrayList<>();
        for (JsonNode item : root) {
            entries.add(new BitfinexBalanceHistoryEntry(
                    item.path("currency").asText(),
                    decimalOrZero(item, "amount"),
                    decimalOrZero(item, "balance"),
                    item.path("description").asText(null),
                    instantOrNull(item.path("timestamp"))
            ));
        }
        log.info("已從 Bitfinex v1 API 取得 balance history。currency={}, count={}", currency, entries.size());
        return entries;
    }

    /**
     * 送出一筆 Bitfinex 私有 POST 請求並回傳解析後 JSON。
     */
    public JsonNode postAuthenticated(String apiPath, String body) {
        validateCredentials();

        String nonce = String.valueOf(nonceCounter.incrementAndGet());
        // Bitfinex v2 authenticated REST 簽章格式為 /api/{path}{nonce}{body}。
        String signaturePayload = "/api/" + apiPath + nonce + body;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("bfx-apikey", properties.getApiKey());
        headers.set("bfx-nonce", nonce);
        headers.set("bfx-signature", sign(signaturePayload, properties.getApiSecret()));

        String url = restBaseUrl + "/" + apiPath;
        try {
            String responseBody = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            ).getBody();
            return objectMapper.readTree(responseBody);
        } catch (RestClientException exception) {
            log.error("Bitfinex REST 請求失敗。endpoint={}", apiPath, exception);
            throw new IllegalStateException("Bitfinex REST request failed", exception);
        } catch (Exception exception) {
            log.error("解析 Bitfinex 回應失敗。endpoint={}", apiPath, exception);
            throw new IllegalStateException("Failed to parse Bitfinex response", exception);
        }
    }

    public JsonNode postAuthenticatedV1(String apiPath, ObjectNode payload) {
        validateCredentials();

        String nonce = String.valueOf(nonceCounter.incrementAndGet());
        payload.put("request", "/" + apiPath);
        payload.put("nonce", nonce);

        try {
            String body = objectMapper.writeValueAsString(payload);
            String encodedPayload = Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-BFX-APIKEY", properties.getApiKey());
            headers.set("X-BFX-PAYLOAD", encodedPayload);
            headers.set("X-BFX-SIGNATURE", sign(encodedPayload, properties.getApiSecret()));

            String url = restBaseUrl + "/" + apiPath;
            String responseBody = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            ).getBody();
            return objectMapper.readTree(responseBody);
        } catch (RestClientException exception) {
            log.error("Bitfinex v1 REST 請求失敗。endpoint={}", apiPath, exception);
            throw new IllegalStateException("Bitfinex v1 REST request failed", exception);
        } catch (Exception exception) {
            log.error("解析 Bitfinex v1 回應失敗。endpoint={}", apiPath, exception);
            throw new IllegalStateException("Failed to parse Bitfinex v1 response", exception);
        }
    }

    private void validateCredentials() {
        if (!StringUtils.hasText(properties.getApiKey()) || !StringUtils.hasText(properties.getApiSecret())) {
            throw new IllegalStateException("BITFINEX_API_KEY and BITFINEX_API_SECRET must be configured");
        }
    }

    private String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA384");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA384"));
            byte[] signed = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(signed.length * 2);
            for (byte value : signed) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign Bitfinex request", exception);
        }
    }

    private BigDecimal decimalOrZero(JsonNode node, int index) {
        JsonNode child = node.path(index);
        if (child.isMissingNode() || child.isNull()) {
            return BigDecimal.ZERO;
        }
        return child.decimalValue();
    }

    private BigDecimal decimalOrZero(JsonNode node, String field) {
        JsonNode child = node.path(field);
        if (child.isMissingNode() || child.isNull()) {
            return BigDecimal.ZERO;
        }
        return child.decimalValue();
    }

    private Instant instantOrNull(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        if (value == null || value.isBlank()) {
            return null;
        }
        long timestamp = Long.parseLong(value);
        return Instant.ofEpochMilli(timestamp);
    }
}
