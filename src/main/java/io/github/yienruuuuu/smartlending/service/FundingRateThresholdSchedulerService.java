package io.github.yienruuuuu.smartlending.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yienruuuuu.smartlending.model.FundingLendbookRateDistributionDto;
import io.github.yienruuuuu.smartlending.model.FundingRateBucketDto;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class FundingRateThresholdSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(FundingRateThresholdSchedulerService.class);

    private static final String TARGET_CURRENCY = "USD";
    private static final int TARGET_MIN_PERIOD = 60;
    private static final int TARGET_MAX_PERIOD = 120;
    private static final int TARGET_LIMIT_ASKS = 10000;
    private static final int TARGET_RATE_SCALE = 1;
    private static final BigDecimal TARGET_CUMULATIVE_SHARE_PERCENT = new BigDecimal("5.0");

    private final BitfinexFundingMarketRestClient fundingMarketRestClient;
    private final ObjectMapper objectMapper;

    public FundingRateThresholdSchedulerService(
            BitfinexFundingMarketRestClient fundingMarketRestClient,
            ObjectMapper objectMapper
    ) {
        this.fundingMarketRestClient = fundingMarketRestClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 以固定條件每 10 分鐘抓一次 funding rate-distribution，供提前掛單策略參考。
     */
    @Scheduled(initialDelay = 10000L, fixedDelay = 600000L)
    public void pollTargetFundingRate() {
        FundingLendbookRateDistributionDto result = fundingMarketRestClient.getFundingLendbookRateDistribution(
                TARGET_CURRENCY,
                TARGET_MIN_PERIOD,
                TARGET_MAX_PERIOD,
                TARGET_LIMIT_ASKS,
                TARGET_RATE_SCALE
        );

        logQueryResult(result);
        logRateBeforeThreshold(result.buckets());
    }

    private void logQueryResult(FundingLendbookRateDistributionDto result) {
        try {
            log.debug("Scheduled funding rate-distribution result: {}", objectMapper.writeValueAsString(result));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize scheduled funding rate-distribution result", exception);
        }
    }

    /**
     * 找第一個 cumulativeSharePercent 大於 5.0 的 bucket，
     * 並取其前一筆 bucket 的利率作為觀察值。
     */
    private void logRateBeforeThreshold(List<FundingRateBucketDto> buckets) {
        for (int index = 0; index < buckets.size(); index++) {
            FundingRateBucketDto current = buckets.get(index);
            if (current.cumulativeSharePercent().compareTo(TARGET_CUMULATIVE_SHARE_PERCENT) <= 0) {
                continue;
            }

            if (index == 0) {
                log.info("First bucket already exceeded cumulativeSharePercent threshold: thresholdPercent={}, currentRate={}, currentCumulativeSharePercent={}",
                        TARGET_CUMULATIVE_SHARE_PERCENT,
                        current.roundedRate(),
                        current.cumulativeSharePercent());
                return;
            }

            FundingRateBucketDto previous = buckets.get(index - 1);
            log.info("Rate before first cumulativeSharePercent threshold breach: thresholdPercent={}, rate={}, nextRate={}, nextCumulativeSharePercent={}",
                    TARGET_CUMULATIVE_SHARE_PERCENT,
                    previous.roundedRate(),
                    current.roundedRate(),
                    current.cumulativeSharePercent());
            return;
        }

        log.info("No bucket exceeded cumulativeSharePercent threshold: thresholdPercent={}, bucketCount={}",
                TARGET_CUMULATIVE_SHARE_PERCENT,
                buckets.size());
    }
}
