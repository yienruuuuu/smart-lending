package io.github.yienruuuuu.smartlending.model;

import java.math.BigDecimal;
import java.time.Instant;

public record BitfinexBalanceHistoryEntry(
        String currency,
        BigDecimal amount,
        BigDecimal balance,
        String description,
        Instant timestamp
) {
}
