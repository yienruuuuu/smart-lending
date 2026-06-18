package io.github.yienruuuuu.smartlending.model;

import java.math.BigDecimal;
import java.time.Instant;

public record BitfinexLedgerEntry(
        long id,
        String currency,
        String wallet,
        Instant timestamp,
        BigDecimal amount,
        BigDecimal balance,
        String description
) {
}
