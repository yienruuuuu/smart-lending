package io.github.yienruuuuu.smartlending.model;

import java.math.BigDecimal;

public record FundingAccountDiagnosticsDto(
        String walletType,
        String walletCurrency,
        BigDecimal unsettledInterest,
        BigDecimal walletImpliedLentAmount,
        BigDecimal creditsAmount,
        BigDecimal loansAmount,
        BigDecimal positionDerivedLentAmount,
        BigDecimal reconciliationDelta,
        String lentAmountRule
) {
}
