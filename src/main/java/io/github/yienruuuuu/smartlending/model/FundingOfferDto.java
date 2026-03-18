package io.github.yienruuuuu.smartlending.model;

import java.math.BigDecimal;

public record FundingOfferDto(
        long offerId,
        String symbol,
        int period,
        BigDecimal rate,
        BigDecimal amount,
        String side
) {
}
