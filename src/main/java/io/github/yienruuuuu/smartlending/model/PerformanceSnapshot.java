package io.github.yienruuuuu.smartlending.model;

import java.math.BigDecimal;
import java.time.Instant;

public record PerformanceSnapshot(
        String account,
        String symbol,
        String currency,
        Instant capturedAt,
        BigDecimal totalWalletAmount,
        BigDecimal idleAmount,
        BigDecimal offerAmount,
        BigDecimal creditAmount,
        BigDecimal loanAmount,
        BigDecimal lentAmount,
        BigDecimal unsettledInterest,
        BigDecimal utilizationRatio,
        String source
) {
}
