package io.github.yienruuuuu.smartlending.model;

import java.math.BigDecimal;
import java.time.Instant;

public record PerformanceCashflowEvent(
        String account,
        String symbol,
        String currency,
        Instant capturedAt,
        BigDecimal amount,
        PerformanceCashflowType type,
        String referenceId,
        String counterparty,
        String source,
        String rawEventType,
        String note
) {
}
