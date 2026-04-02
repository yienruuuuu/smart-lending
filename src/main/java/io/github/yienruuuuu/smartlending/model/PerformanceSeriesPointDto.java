package io.github.yienruuuuu.smartlending.model;

import java.math.BigDecimal;
import java.time.Instant;

public record PerformanceSeriesPointDto(
        Instant capturedAt,
        BigDecimal totalWalletAmount,
        BigDecimal idleAmount,
        BigDecimal offerAmount,
        BigDecimal creditAmount,
        BigDecimal loanAmount,
        BigDecimal lentAmount,
        BigDecimal unsettledInterest
) {
}
