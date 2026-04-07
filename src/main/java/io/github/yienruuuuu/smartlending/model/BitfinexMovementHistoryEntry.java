package io.github.yienruuuuu.smartlending.model;

import java.math.BigDecimal;
import java.time.Instant;

public record BitfinexMovementHistoryEntry(
        String id,
        String currency,
        String method,
        String type,
        String status,
        BigDecimal amount,
        BigDecimal fee,
        Instant updatedAt,
        String transactionId,
        String address
) {
}
