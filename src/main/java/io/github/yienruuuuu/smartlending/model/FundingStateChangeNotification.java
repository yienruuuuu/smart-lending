package io.github.yienruuuuu.smartlending.model;

import java.math.BigDecimal;

public record FundingStateChangeNotification(
        String eventType,
        String account,
        BigDecimal offerAmount,
        BigDecimal offerRate
) {
}
