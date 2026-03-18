package io.github.yienruuuuu.smartlending.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yienruuuuu.smartlending.config.BitfinexProperties;
import io.github.yienruuuuu.smartlending.model.WalletBalanceDto;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
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
        log.info("Fetched {} wallet rows from Bitfinex REST API", wallets.size());
        return wallets;
    }

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
            log.error("Bitfinex REST request failed: endpoint={}", apiPath, exception);
            throw new IllegalStateException("Bitfinex REST request failed", exception);
        } catch (Exception exception) {
            log.error("Failed to parse Bitfinex response: endpoint={}", apiPath, exception);
            throw new IllegalStateException("Failed to parse Bitfinex response", exception);
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
}
