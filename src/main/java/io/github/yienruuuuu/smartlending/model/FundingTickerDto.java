package io.github.yienruuuuu.smartlending.model;

import java.math.BigDecimal;

public record FundingTickerDto(
        String symbol,
        BigDecimal frr,
        BigDecimal bid,
        BigDecimal bidPeriod,
        BigDecimal bidSize,
        BigDecimal ask,
        BigDecimal askPeriod,
        BigDecimal askSize,
        BigDecimal dailyChange,
        BigDecimal dailyChangeRelative,
        BigDecimal lastPrice,
        BigDecimal volume,
        BigDecimal high,
        BigDecimal low,
        BigDecimal frrAmountAvailable
) {
}
