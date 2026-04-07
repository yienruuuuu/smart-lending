package io.github.yienruuuuu.smartlending.model;

import java.math.BigDecimal;
import java.time.Instant;

public record PerformanceSummaryDto(
        String account,
        String range,
        int snapshotCount,
        int cashflowCount,
        Instant startAt,
        Instant endAt,
        BigDecimal startValue,
        BigDecimal endValue,
        BigDecimal absoluteReturn,
        BigDecimal totalReturnRatio,
        BigDecimal totalReturnPercent,
        BigDecimal annualizedReturnRatio,
        BigDecimal annualizedReturnPercent,
        BigDecimal twrReturnRatio,
        BigDecimal twrReturnPercent,
        BigDecimal twrAnnualizedReturnRatio,
        BigDecimal twrAnnualizedReturnPercent,
        BigDecimal xirrRatio,
        BigDecimal xirrPercent,
        BigDecimal netCashflow,
        BigDecimal idleAmount,
        BigDecimal offerAmount,
        BigDecimal creditAmount,
        BigDecimal loanAmount,
        BigDecimal lentAmount,
        BigDecimal unsettledInterest,
        BigDecimal utilizationRatio
) {
}
