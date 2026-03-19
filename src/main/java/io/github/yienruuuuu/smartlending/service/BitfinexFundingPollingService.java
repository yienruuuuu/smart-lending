package io.github.yienruuuuu.smartlending.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.yienruuuuu.smartlending.config.BitfinexProperties;
import io.github.yienruuuuu.smartlending.model.PollingStatusDto;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class BitfinexFundingPollingService {

    private static final Logger log = LoggerFactory.getLogger(BitfinexFundingPollingService.class);

    private final BitfinexProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final AtomicBoolean pollingEnabled = new AtomicBoolean(true);

    private volatile JsonNode lastPollSummary = NullNode.getInstance();

    public BitfinexFundingPollingService(
            BitfinexProperties properties,
            ObjectMapper objectMapper,
            RestTemplateBuilder restTemplateBuilder
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder.build();
    }

    // 以固定週期輪詢公開 funding book，降低 websocket 長連線造成的解析與重連複雜度。
    @Scheduled(initialDelay = 2000L, fixedDelayString = "#{${bitfinex.polling-interval-seconds:10} * 1000}")
    public void pollFundingBooks() {
        if (!pollingEnabled.get()) {
            return;
        }
        for (String symbol : properties.getFundingSymbols()) {
            pollFundingBook(symbol);
        }
    }

    public PollingStatusDto startPolling() {
        pollingEnabled.set(true);
        log.info("Funding polling started by REST request");
        return getPollingStatus();
    }

    public PollingStatusDto stopPolling() {
        pollingEnabled.set(false);
        log.info("Funding polling stopped by REST request");
        return getPollingStatus();
    }

    public PollingStatusDto getPollingStatus() {
        return new PollingStatusDto(
                pollingEnabled.get(),
                properties.getPollingIntervalSeconds(),
                lastPollSummary
        );
    }

    public JsonNode pollFundingBookOnce(String symbol) {
        return pollFundingBook(symbol);
    }

    private JsonNode pollFundingBook(String symbol) {
        String url = "%s/v2/book/%s/%s?len=%d".formatted(
                properties.getPublicBaseUrl(),
                symbol,
                properties.getBookPrecision(),
                properties.getBookLength()
        );

        log.info("Polling funding url={}", url);

        try {
            String responseBody = restTemplate.exchange(url, HttpMethod.GET, null, String.class).getBody();
            JsonNode root = objectMapper.readTree(responseBody);
            if (!root.isArray()) {
                throw new IllegalStateException("Unexpected funding book response: " + responseBody);
            }

            ObjectNode summary = buildPollSummary(symbol, url, root);
            lastPollSummary = summary;
            log.info("Funding poll completed: {}", objectMapper.writeValueAsString(summary));

            for (JsonNode offerNode : root) {
                log.info("Funding offer raw: {}", objectMapper.writeValueAsString(buildOfferLog(symbol, offerNode)));
            }
            return summary;
        } catch (Exception exception) {
            log.error("Failed to poll funding book: symbol={}, url={}", symbol, url, exception);
            throw new IllegalStateException("Failed to poll funding book", exception);
        }
    }

    private ObjectNode buildPollSummary(String symbol, String url, JsonNode root) {
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("symbol", symbol);
        summary.put("url", url);
        summary.put("entryCount", root.size());
        summary.put("polledAt", OffsetDateTime.now().toString());
        summary.set("raw", root.deepCopy());
        return summary;
    }

    private ObjectNode buildOfferLog(String symbol, JsonNode offerNode) {
        ObjectNode logNode = objectMapper.createObjectNode();
        logNode.put("symbol", symbol);
        logNode.set("raw", offerNode.deepCopy());
        logNode.set("decoded", decodeOffer(offerNode));
        return logNode;
    }

    private ObjectNode decodeOffer(JsonNode offerNode) {
        ObjectNode decoded = objectMapper.createObjectNode();
        long offerId = offerNode.path(0).asLong();
        int period = offerNode.path(1).asInt();
        BigDecimal rate = offerNode.path(2).decimalValue();
        BigDecimal amount = offerNode.path(3).decimalValue();

        decoded.put("offerId", offerId);
        decoded.put("period", period);
        decoded.put("rate", rate.toPlainString());
        decoded.put("amount", amount.toPlainString());
        decoded.put("side", amount.signum() >= 0 ? "BID" : "ASK");

        ArrayNode rawFieldNames = objectMapper.createArrayNode();
        rawFieldNames.add("offerId");
        rawFieldNames.add("period");
        rawFieldNames.add("rate");
        rawFieldNames.add("amount");
        decoded.set("rawFieldOrder", rawFieldNames);
        return decoded;
    }
}
