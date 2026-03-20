package io.github.yienruuuuu.smartlending.model;

import java.math.BigDecimal;

public record FundingRateBucketDto(
        BigDecimal roundedRate,
        int orderCount,
        BigDecimal totalAmount,
        BigDecimal amountShareRatio,
        BigDecimal amountSharePercent,
        BigDecimal cumulativeShareRatio,
        BigDecimal cumulativeSharePercent,
        boolean isFrr
) {
}
