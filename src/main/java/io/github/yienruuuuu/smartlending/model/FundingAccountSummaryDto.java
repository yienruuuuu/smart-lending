package io.github.yienruuuuu.smartlending.model;

import java.math.BigDecimal;
import java.util.List;

public record FundingAccountSummaryDto(
        String symbol,
        BigDecimal totalWalletAmount,
        BigDecimal lentAmount,
        BigDecimal idleAmount,
        BigDecimal offerAmount,
        int offerCount,
        int creditCount,
        int loanCount,
        List<FundingPositionDto> offers,
        List<FundingPositionDto> credits,
        List<FundingPositionDto> loans,
        FundingAccountDiagnosticsDto diagnostics
) {
}
