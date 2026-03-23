package io.github.yienruuuuu.smartlending.model;

import java.math.BigDecimal;
import java.util.List;

public record FundingLendbookRateDistributionDto(
        String currency,
        Integer minPeriod,
        Integer maxPeriod,
        int limitAsks,
        int rateScale,
        int matchedAskCount,
        BigDecimal matchedTotalAmount,
        String sourceUrl,
        List<FundingRateBucketDto> buckets
) {
}
