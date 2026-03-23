package io.github.yienruuuuu.smartlending.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yienruuuuu.smartlending.config.BitfinexProperties;
import io.github.yienruuuuu.smartlending.model.FundingLendbookRateDistributionDto;
import io.github.yienruuuuu.smartlending.model.FundingLendbookSummaryDto;
import io.github.yienruuuuu.smartlending.model.FundingRateBucketDto;
import io.github.yienruuuuu.smartlending.model.FundingTickerDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class BitfinexFundingMarketRestClient {

    private static final Logger log = LoggerFactory.getLogger(BitfinexFundingMarketRestClient.class);
    private static final String DEFAULT_FUNDING_SYMBOL = "fUSD";
    private static final String DEFAULT_FUNDING_CURRENCY = "USD";
    private static final int DEFAULT_LIMIT_ASKS = 10000;
    private static final int DEFAULT_RATE_SCALE = 1;

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
                .setConnectTimeout(Duration.ofSeconds(properties.getMarketConnectTimeoutSeconds()))
                .setReadTimeout(Duration.ofSeconds(properties.getMarketReadTimeoutSeconds()))
                .build();
    }

    public FundingTickerDto getFundingTicker(String symbol) {
        String resolvedSymbol = normalizeSymbol(symbol);
        String url = buildTickerUrl(resolvedSymbol);

        try {
            String responseBody = restTemplate.exchange(url, HttpMethod.GET, null, String.class).getBody();
            JsonNode root = objectMapper.readTree(responseBody);
            if (!root.isArray()) {
                throw new IllegalStateException("Unexpected funding ticker response: " + responseBody);
            }
            log.info("Fetched funding ticker: symbol={}", resolvedSymbol);
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

    public FundingLendbookSummaryDto getFundingLendbookSummary(String currency, Integer limitAsks, Integer minPeriodExclusive) {
        String resolvedCurrency = normalizeCurrency(currency);
        String fundingSymbol = toFundingSymbol(resolvedCurrency);
        int resolvedLimitAsks = normalizeLimitAsks(limitAsks);
        String url = buildLendbookUrl(resolvedCurrency, resolvedLimitAsks);
        String tickerUrl = buildTickerUrl(fundingSymbol);

        try {
            JsonNode asks = fetchLendbookAsks(url);

            BigDecimal totalAskAmount = BigDecimal.ZERO;
            BigDecimal frrAskAmountFromBook = BigDecimal.ZERO;
            BigDecimal amountBeforeFirstFrrAsk = BigDecimal.ZERO;
            boolean seenFirstFrrAsk = false;
            int askCount = 0;
            int frrAskCount = 0;

            for (JsonNode ask : asks) {
                int period = ask.path("period").asInt();
                if (shouldSkipByMinPeriod(period, minPeriodExclusive)) {
                    continue;
                }

                BigDecimal amount = decimalTextOrZero(ask, "amount");
                boolean isFrr = isFrrOffer(ask.path("frr").asText());

                totalAskAmount = totalAskAmount.add(amount);
                askCount++;

                if (isFrr) {
                    seenFirstFrrAsk = true;
                    frrAskAmountFromBook = frrAskAmountFromBook.add(amount);
                    frrAskCount++;
                } else if (!seenFirstFrrAsk) {
                    amountBeforeFirstFrrAsk = amountBeforeFirstFrrAsk.add(amount);
                }
            }

            FundingTickerDto ticker = getFundingTicker(fundingSymbol);
            BigDecimal frrAmountAvailableFromTicker = ticker.frrAmountAvailable();
            BigDecimal fixedRateAskAmountFromBook = totalAskAmount.subtract(frrAskAmountFromBook);
            BigDecimal nonFrrOrderBookAmountByTicker = totalAskAmount.subtract(frrAmountAvailableFromTicker);
            int fixedRateAskCount = askCount - frrAskCount;
            BigDecimal frrShareFromBook = totalAskAmount.signum() == 0
                    ? BigDecimal.ZERO
                    : frrAskAmountFromBook.divide(totalAskAmount, 8, RoundingMode.HALF_UP);

            log.info("Fetched funding lendbook summary: currency={}, asks={}, frrAsks={}, limitAsks={}, minPeriodExclusive={}, totalAskAmount={}, frrAmountAvailable={}",
                    resolvedCurrency, askCount, frrAskCount, resolvedLimitAsks, minPeriodExclusive, totalAskAmount, frrAmountAvailableFromTicker);

            return new FundingLendbookSummaryDto(
                    resolvedCurrency,
                    fundingSymbol,
                    minPeriodExclusive,
                    askCount,
                    frrAskCount,
                    fixedRateAskCount,
                    totalAskAmount,
                    frrAskAmountFromBook,
                    fixedRateAskAmountFromBook,
                    amountBeforeFirstFrrAsk,
                    frrAmountAvailableFromTicker,
                    nonFrrOrderBookAmountByTicker,
                    frrShareFromBook,
                    url,
                    tickerUrl
            );
        } catch (RestClientException exception) {
            log.error("Bitfinex lendbook request failed: currency={}, url={}", resolvedCurrency, url, exception);
            throw new IllegalStateException("Bitfinex lendbook request failed", exception);
        } catch (Exception exception) {
            log.error("Failed to parse Bitfinex lendbook: currency={}, url={}", resolvedCurrency, url, exception);
            throw new IllegalStateException("Failed to parse Bitfinex lendbook", exception);
        }
    }

    public FundingLendbookRateDistributionDto getFundingLendbookRateDistribution(
            String currency,
            Integer minPeriod,
            Integer maxPeriod,
            Integer limitAsks,
            Integer rateScale
    ) {
        validatePeriodRange(minPeriod, maxPeriod);

        String resolvedCurrency = normalizeCurrency(currency);
        int resolvedLimitAsks = normalizeLimitAsks(limitAsks);
        int resolvedRateScale = normalizeRateScale(rateScale);
        String url = buildLendbookUrl(resolvedCurrency, resolvedLimitAsks);

        try {
            JsonNode asks = fetchLendbookAsks(url);
            Map<BigDecimal, BucketAccumulator> buckets = new TreeMap<>();
            BigDecimal matchedTotalAmount = BigDecimal.ZERO;
            int matchedAskCount = 0;

            for (JsonNode ask : asks) {
                int askPeriod = ask.path("period").asInt();
                if (shouldSkipByPeriodRange(askPeriod, minPeriod, maxPeriod)) {
                    continue;
                }

                BigDecimal amount = decimalTextOrZero(ask, "amount");
                BigDecimal rate = decimalTextOrZero(ask, "rate");
                BigDecimal roundedRate = rate.setScale(resolvedRateScale, RoundingMode.HALF_UP);
                boolean isFrr = isFrrOffer(ask.path("frr").asText());

                BucketAccumulator bucket = buckets.computeIfAbsent(roundedRate, ignored -> new BucketAccumulator());
                bucket.orderCount++;
                bucket.totalAmount = bucket.totalAmount.add(amount);
                bucket.isFrr = bucket.isFrr || isFrr;
                matchedAskCount++;
                matchedTotalAmount = matchedTotalAmount.add(amount);
            }

            List<FundingRateBucketDto> bucketDtos = new ArrayList<>();
            BigDecimal cumulativeRatio = BigDecimal.ZERO;
            for (Map.Entry<BigDecimal, BucketAccumulator> entry : buckets.entrySet()) {
                // 先依四捨五入後的利率分桶，再計算各 bucket 的占比與累積占比。
                BigDecimal shareRatio = matchedTotalAmount.signum() == 0
                        ? BigDecimal.ZERO
                        : entry.getValue().totalAmount.divide(matchedTotalAmount, 8, RoundingMode.HALF_UP);
                BigDecimal sharePercent = shareRatio.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
                cumulativeRatio = cumulativeRatio.add(shareRatio).setScale(8, RoundingMode.HALF_UP);
                BigDecimal cumulativePercent = cumulativeRatio.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
                bucketDtos.add(new FundingRateBucketDto(
                        entry.getKey(),
                        entry.getValue().orderCount,
                        entry.getValue().totalAmount,
                        shareRatio,
                        sharePercent,
                        cumulativeRatio,
                        cumulativePercent,
                        entry.getValue().isFrr
                ));
            }

            log.info("Fetched funding lendbook rate distribution: currency={}, minPeriod={}, maxPeriod={}, limitAsks={}, rateScale={}, matchedAskCount={}, bucketCount={}",
                    resolvedCurrency, minPeriod, maxPeriod, resolvedLimitAsks, resolvedRateScale, matchedAskCount, bucketDtos.size());

            return new FundingLendbookRateDistributionDto(
                    resolvedCurrency,
                    minPeriod,
                    maxPeriod,
                    resolvedLimitAsks,
                    resolvedRateScale,
                    matchedAskCount,
                    matchedTotalAmount,
                    url,
                    bucketDtos
            );
        } catch (RestClientException exception) {
            log.error("Bitfinex lendbook distribution request failed: currency={}, url={}, connectTimeoutSeconds={}, readTimeoutSeconds={}",
                    resolvedCurrency, url, properties.getMarketConnectTimeoutSeconds(), properties.getMarketReadTimeoutSeconds(), exception);
            throw new IllegalStateException("Bitfinex lendbook distribution request failed", exception);
        } catch (Exception exception) {
            log.error("Failed to parse Bitfinex lendbook distribution: currency={}, url={}", resolvedCurrency, url, exception);
            throw new IllegalStateException("Failed to parse Bitfinex lendbook distribution", exception);
        }
    }

    private JsonNode fetchLendbookAsks(String url) throws Exception {
        String responseBody = restTemplate.exchange(url, HttpMethod.GET, null, String.class).getBody();
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode asks = root.path("asks");
        if (!asks.isArray()) {
            throw new IllegalStateException("Unexpected funding lendbook response: " + responseBody);
        }
        return asks;
    }

    private String buildTickerUrl(String symbol) {
        return "%s/v2/ticker/%s".formatted(properties.getPublicBaseUrl(), symbol);
    }

    private String buildLendbookUrl(String currency, int limitAsks) {
        return "%s/v1/lendbook/%s?limit_bids=0&limit_asks=%d".formatted(
                properties.getRestBaseUrl(),
                currency,
                limitAsks
        );
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return DEFAULT_FUNDING_SYMBOL;
        }
        return symbol.trim();
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return DEFAULT_FUNDING_CURRENCY;
        }
        String normalized = currency.trim().toUpperCase();
        if (normalized.startsWith("F") && normalized.length() > 1) {
            return normalized.substring(1);
        }
        return normalized;
    }

    private String toFundingSymbol(String currency) {
        return "f" + currency;
    }

    private int normalizeLimitAsks(Integer limitAsks) {
        if (limitAsks == null || limitAsks < 1) {
            return DEFAULT_LIMIT_ASKS;
        }
        return limitAsks;
    }

    private int normalizeRateScale(Integer rateScale) {
        if (rateScale == null || rateScale < 0) {
            return DEFAULT_RATE_SCALE;
        }
        return rateScale;
    }

    private boolean shouldSkipByMinPeriod(int period, Integer minPeriodExclusive) {
        return minPeriodExclusive != null && period <= minPeriodExclusive;
    }

    private boolean shouldSkipByPeriodRange(int askPeriod, Integer minPeriod, Integer maxPeriod) {
        return (minPeriod != null && askPeriod < minPeriod)
                || (maxPeriod != null && askPeriod > maxPeriod);
    }

    private void validatePeriodRange(Integer minPeriod, Integer maxPeriod) {
        if (minPeriod != null && maxPeriod != null && minPeriod > maxPeriod) {
            throw new IllegalArgumentException("minPeriod must be less than or equal to maxPeriod");
        }
    }

    private boolean isFrrOffer(String frr) {
        return "yes".equalsIgnoreCase(frr);
    }

    private BigDecimal decimalOrZero(JsonNode node, int index) {
        JsonNode child = node.path(index);
        if (child.isMissingNode() || child.isNull()) {
            return BigDecimal.ZERO;
        }
        return child.decimalValue();
    }

    private BigDecimal decimalTextOrZero(JsonNode node, String fieldName) {
        JsonNode child = node.path(fieldName);
        if (child.isMissingNode() || child.isNull()) {
            return BigDecimal.ZERO;
        }
        String value = child.asText();
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.trim());
    }

    private static final class BucketAccumulator {
        private int orderCount;
        private BigDecimal totalAmount = BigDecimal.ZERO;
        private boolean isFrr;
    }
}
