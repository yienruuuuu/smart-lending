package io.github.yienruuuuu.smartlending.model;

import java.math.BigDecimal;
import java.time.Instant;

public record PerformanceSummaryDto(
        String account,
        String range,
        int snapshotCount,
        Instant startAt,
        Instant endAt,
        BigDecimal startValue,
        BigDecimal endValue,
        BigDecimal absoluteReturn,
        BigDecimal totalReturnRatio,
        BigDecimal totalReturnPercent,
        BigDecimal annualizedReturnRatio,
        BigDecimal annualizedReturnPercent,
        BigDecimal idleAmount,
        BigDecimal offerAmount,
        BigDecimal creditAmount,
        BigDecimal loanAmount,
        BigDecimal lentAmount,
        BigDecimal unsettledInterest,
        BigDecimal utilizationRatio
) {
}
