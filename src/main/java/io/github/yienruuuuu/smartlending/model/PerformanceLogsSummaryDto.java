package io.github.yienruuuuu.smartlending.model;

import java.math.BigDecimal;
import java.time.Instant;

public record PerformanceLogsSummaryDto(
        int eventCount,
        int snapshotCount,
        int cashflowCount,
        BigDecimal netCashflow,
        BigDecimal startValue,
        BigDecimal endValue,
        Instant startAt,
        Instant endAt
) {
}
