package io.github.yienruuuuu.smartlending.model;

import java.math.BigDecimal;

public record WalletBalanceDto(
        String walletType,
        String currency,
        BigDecimal balance,
        BigDecimal unsettledInterest,
        BigDecimal availableBalance
) {
}
