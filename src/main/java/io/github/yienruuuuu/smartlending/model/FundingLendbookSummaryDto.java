package io.github.yienruuuuu.smartlending.model;

import java.math.BigDecimal;

public record FundingLendbookSummaryDto(
        String currency,
        String fundingSymbol,
        Integer minPeriodExclusive,
        int askCount,
        int frrAskCount,
        int fixedRateAskCount,
        BigDecimal totalAskAmount,
        BigDecimal frrAskAmountFromBook,
        BigDecimal fixedRateAskAmountFromBook,
        BigDecimal amountBeforeFirstFrrAsk,
        BigDecimal frrAmountAvailableFromTicker,
        BigDecimal nonFrrOrderBookAmountByTicker,
        BigDecimal frrShareFromBook,
        String sourceUrl,
        String tickerSourceUrl
) {
}
