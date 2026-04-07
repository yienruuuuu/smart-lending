package io.github.yienruuuuu.smartlending.model;

import java.math.BigDecimal;
import java.time.Instant;

public record PerformanceLogRowDto(
        String kind,
        String account,
        Instant capturedAt,
        String title,
        String type,
        BigDecimal amount,
        BigDecimal totalWalletAmount,
        BigDecimal idleAmount,
        BigDecimal lentAmount,
        BigDecimal utilizationRatio,
        String referenceId,
        String source,
        String rawEventType,
        String note
) {
}
