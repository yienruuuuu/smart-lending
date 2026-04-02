package io.github.yienruuuuu.smartlending.model;

import java.math.BigDecimal;
import java.time.Instant;

public record FundingStateSnapshot(
        String account,
        Instant capturedAt,
        BigDecimal offerAmount,
        BigDecimal lentAmount,
        int offerCount,
        int creditCount,
        int loanCount,
        BigDecimal primaryOfferRate
) {
}
