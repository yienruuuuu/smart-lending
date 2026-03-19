package io.github.yienruuuuu.smartlending.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yienruuuuu.smartlending.config.BitfinexProperties;
import io.github.yienruuuuu.smartlending.model.FundingTickerDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;

@Component
public class BitfinexFundingMarketRestClient {

    private static final Logger log = LoggerFactory.getLogger(BitfinexFundingMarketRestClient.class);
    private static final String DEFAULT_FUNDING_SYMBOL = "fUSD";

    private final BitfinexProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public BitfinexFundingMarketRestClient(
            BitfinexProperties properties,
            ObjectMapper objectMapper,
            RestTemplateBuilder restTemplateBuilder
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
    }

    public FundingTickerDto getFundingTicker(String symbol) {
        String resolvedSymbol = normalizeSymbol(symbol);
        String url = "%s/v2/ticker/%s".formatted(properties.getPublicBaseUrl(), resolvedSymbol);

        try {
            String responseBody = restTemplate.exchange(url, HttpMethod.GET, null, String.class).getBody();
            JsonNode root = objectMapper.readTree(responseBody);
            if (!root.isArray()) {
                throw new IllegalStateException("Unexpected funding ticker response: " + responseBody);
            }
            log.info("Fetched funding ticker: symbol={}, root={}", resolvedSymbol, root);
            return new FundingTickerDto(
                    resolvedSymbol,
                    decimalOrZero(root, 0),
                    decimalOrZero(root, 1),
                    decimalOrZero(root, 2),
                    decimalOrZero(root, 3),
                    decimalOrZero(root, 4),
                    decimalOrZero(root, 5),
                    decimalOrZero(root, 6),
                    decimalOrZero(root, 7),
                    decimalOrZero(root, 8),
                    decimalOrZero(root, 9),
                    decimalOrZero(root, 10),
                    decimalOrZero(root, 11),
                    decimalOrZero(root, 12),
                    decimalOrZero(root, 15)
            );
        } catch (RestClientException exception) {
            log.error("Bitfinex public ticker request failed: symbol={}, url={}", resolvedSymbol, url, exception);
            throw new IllegalStateException("Bitfinex public ticker request failed", exception);
        } catch (Exception exception) {
            log.error("Failed to parse Bitfinex funding ticker: symbol={}, url={}", resolvedSymbol, url, exception);
            throw new IllegalStateException("Failed to parse Bitfinex funding ticker", exception);
        }
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return DEFAULT_FUNDING_SYMBOL;
        }
        return symbol.trim();
    }

    private BigDecimal decimalOrZero(JsonNode node, int index) {
        JsonNode child = node.path(index);
        if (child.isMissingNode() || child.isNull()) {
            return BigDecimal.ZERO;
        }
        return child.decimalValue();
    }
}
