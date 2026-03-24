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

/**
 * 負責呼叫 sub account 的 Bitfinex 私有 authenticated REST API，處理簽章、nonce 與基礎錯誤轉換。
 */
@Component
public class SubBitfinexAccountRestClient {

    private static final Logger log = LoggerFactory.getLogger(SubBitfinexAccountRestClient.class);

    private final BitfinexProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final AtomicLong nonceCounter = new AtomicLong(System.currentTimeMillis() * 1000L);
    private final String restBaseUrl;

    public SubBitfinexAccountRestClient(
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
     * 查詢 sub account 的 wallets，並轉成較易閱讀的 DTO。
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
        log.info("已從 sub account Bitfinex REST API 取得 wallet 資料。count={}", wallets.size());
        return wallets;
    }

    /**
     * 送出一筆 sub account Bitfinex 私有 POST 請求並回傳解析後 JSON。
     */
    public JsonNode postAuthenticated(String apiPath, String body) {
        validateCredentials();

        String nonce = String.valueOf(nonceCounter.incrementAndGet());
        String signaturePayload = "/api/" + apiPath + nonce + body;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("bfx-apikey", properties.getSubApiKey());
        headers.set("bfx-nonce", nonce);
        headers.set("bfx-signature", sign(signaturePayload, properties.getSubApiSecret()));

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
            log.error("Sub account Bitfinex REST 請求失敗。endpoint={}", apiPath, exception);
            throw new IllegalStateException("Sub account Bitfinex REST request failed", exception);
        } catch (Exception exception) {
            log.error("解析 sub account Bitfinex 回應失敗。endpoint={}", apiPath, exception);
            throw new IllegalStateException("Failed to parse sub account Bitfinex response", exception);
        }
    }

    private void validateCredentials() {
        if (!StringUtils.hasText(properties.getSubApiKey()) || !StringUtils.hasText(properties.getSubApiSecret())) {
            throw new IllegalStateException("SUB_BITFINEX_API_KEY and SUB_BITFINEX_API_SECRET must be configured");
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
            throw new IllegalStateException("Failed to sign sub account Bitfinex request", exception);
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
