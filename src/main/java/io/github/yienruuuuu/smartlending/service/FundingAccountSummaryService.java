package io.github.yienruuuuu.smartlending.service;

import io.github.yienruuuuu.smartlending.model.FundingAccountDiagnosticsDto;
import io.github.yienruuuuu.smartlending.model.FundingAccountSummaryDto;
import io.github.yienruuuuu.smartlending.model.FundingPositionDto;
import io.github.yienruuuuu.smartlending.model.WalletBalanceDto;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FundingAccountSummaryService {

    private final BitfinexAccountRestClient bitfinexAccountRestClient;
    private final BitfinexFundingAccountRestClient fundingAccountRestClient;

    public FundingAccountSummaryService(
            BitfinexAccountRestClient bitfinexAccountRestClient,
            BitfinexFundingAccountRestClient fundingAccountRestClient
    ) {
        this.bitfinexAccountRestClient = bitfinexAccountRestClient;
        this.fundingAccountRestClient = fundingAccountRestClient;
    }

    public FundingAccountSummaryDto getSummary(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);
        String currency = fundingCurrency(normalizedSymbol);

        WalletBalanceDto wallet = bitfinexAccountRestClient.getWallets().stream()
                .filter(item -> "funding".equalsIgnoreCase(item.walletType()))
                .filter(item -> currency.equalsIgnoreCase(item.currency()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Funding wallet not found for currency: " + currency
                ));

        List<FundingPositionDto> offers = fundingAccountRestClient.getFundingOffers(normalizedSymbol);
        List<FundingPositionDto> credits = fundingAccountRestClient.getFundingCredits(normalizedSymbol);
        List<FundingPositionDto> loans = fundingAccountRestClient.getFundingLoans(normalizedSymbol);

        BigDecimal offerAmount = sumAmounts(offers);
        BigDecimal creditsAmount = sumAmounts(credits);
        BigDecimal loansAmount = sumAmounts(loans);
        BigDecimal idleAmount = nullSafe(wallet.availableBalance());
        BigDecimal totalWalletAmount = nullSafe(wallet.balance());
        BigDecimal walletImpliedLentAmount = nonNegative(totalWalletAmount.subtract(idleAmount).subtract(offerAmount));
        BigDecimal positionDerivedLentAmount = creditsAmount.max(loansAmount);
        BigDecimal reconciliationDelta = walletImpliedLentAmount.subtract(positionDerivedLentAmount).abs();

        FundingAccountDiagnosticsDto diagnostics = new FundingAccountDiagnosticsDto(
                wallet.walletType(),
                wallet.currency(),
                nullSafe(wallet.unsettledInterest()),
                walletImpliedLentAmount,
                creditsAmount,
                loansAmount,
                positionDerivedLentAmount,
                reconciliationDelta,
                "lentAmount = wallet.balance - wallet.availableBalance - openOffersAmount"
        );

        return new FundingAccountSummaryDto(
                normalizedSymbol,
                totalWalletAmount,
                walletImpliedLentAmount,
                idleAmount,
                offerAmount,
                offers.size(),
                credits.size(),
                loans.size(),
                offers,
                credits,
                loans,
                diagnostics
        );
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null || symbol.isBlank() ? "fUSD" : symbol.trim();
    }

    private String fundingCurrency(String symbol) {
        return symbol.startsWith("f") && symbol.length() > 1 ? symbol.substring(1) : symbol;
    }

    private BigDecimal sumAmounts(List<FundingPositionDto> positions) {
        return positions.stream()
                .map(item -> item.decoded().path("amount").asText("0"))
                .map(BigDecimal::new)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal nonNegative(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : value;
    }
}
