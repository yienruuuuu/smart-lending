package io.github.yienruuuuu.smartlending.service;

import io.github.yienruuuuu.smartlending.config.BitfinexProperties;
import io.github.yienruuuuu.smartlending.model.FundingPositionDto;
import io.github.yienruuuuu.smartlending.model.FundingStateSnapshot;
import io.github.yienruuuuu.smartlending.model.WalletBalanceDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 收集主帳戶與子帳戶目前 funding 狀態，供通知比對使用。
 */
@Service
public class FundingStateSnapshotService {

    private static final String TARGET_SYMBOL = "fUSD";
    private static final String TARGET_CURRENCY = "USD";

    private final BitfinexProperties properties;
    private final BitfinexAccountRestClient bitfinexAccountRestClient;
    private final BitfinexFundingAccountRestClient fundingAccountRestClient;
    private final SubBitfinexAccountRestClient subBitfinexAccountRestClient;
    private final SubBitfinexFundingAccountRestClient subFundingAccountRestClient;

    public FundingStateSnapshotService(
            BitfinexProperties properties,
            BitfinexAccountRestClient bitfinexAccountRestClient,
            BitfinexFundingAccountRestClient fundingAccountRestClient,
            SubBitfinexAccountRestClient subBitfinexAccountRestClient,
            SubBitfinexFundingAccountRestClient subFundingAccountRestClient
    ) {
        this.properties = properties;
        this.bitfinexAccountRestClient = bitfinexAccountRestClient;
        this.fundingAccountRestClient = fundingAccountRestClient;
        this.subBitfinexAccountRestClient = subBitfinexAccountRestClient;
        this.subFundingAccountRestClient = subFundingAccountRestClient;
    }

    public Optional<FundingStateSnapshot> captureMain() {
        if (!properties.hasMainAccountCredentials()) {
            return Optional.empty();
        }
        return Optional.of(capture(
                "main",
                bitfinexAccountRestClient.getWallets(),
                fundingAccountRestClient.getFundingOffers(TARGET_SYMBOL),
                fundingAccountRestClient.getFundingCredits(TARGET_SYMBOL),
                fundingAccountRestClient.getFundingLoans(TARGET_SYMBOL)
        ));
    }

    public Optional<FundingStateSnapshot> captureSub() {
        if (!properties.hasSubAccountCredentials()) {
            return Optional.empty();
        }
        return Optional.of(capture(
                "sub",
                subBitfinexAccountRestClient.getWallets(),
                subFundingAccountRestClient.getFundingOffers(TARGET_SYMBOL),
                subFundingAccountRestClient.getFundingCredits(TARGET_SYMBOL),
                subFundingAccountRestClient.getFundingLoans(TARGET_SYMBOL)
        ));
    }

    private FundingStateSnapshot capture(
            String account,
            List<WalletBalanceDto> wallets,
            List<FundingPositionDto> offers,
            List<FundingPositionDto> credits,
            List<FundingPositionDto> loans
    ) {
        WalletBalanceDto wallet = wallets.stream()
                .filter(item -> "funding".equalsIgnoreCase(item.walletType()))
                .filter(item -> TARGET_CURRENCY.equalsIgnoreCase(item.currency()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Funding wallet not found for account: " + account));

        BigDecimal offerAmount = sumAmounts(offers);
        BigDecimal creditAmount = sumAmounts(credits);
        BigDecimal loanAmount = sumAmounts(loans);
        BigDecimal idleAmount = nullSafe(wallet.availableBalance());
        BigDecimal totalWalletAmount = nullSafe(wallet.balance());
        BigDecimal walletDerivedLentAmount = totalWalletAmount.subtract(idleAmount).subtract(offerAmount);
        BigDecimal lentAmount = walletDerivedLentAmount.max(creditAmount.max(loanAmount));

        return new FundingStateSnapshot(
                account,
                Instant.now(),
                offerAmount,
                nonNegative(lentAmount),
                offers.size(),
                credits.size(),
                loans.size(),
                primaryOfferRate(offers)
        );
    }

    private BigDecimal primaryOfferRate(List<FundingPositionDto> offers) {
        return offers.stream()
                .max(Comparator.comparing(this::offerAmount))
                .map(offer -> offer.decoded().path("rate").asText(null))
                .filter(rate -> rate != null && !rate.isBlank())
                .map(BigDecimal::new)
                .orElse(null);
    }

    private BigDecimal offerAmount(FundingPositionDto offer) {
        return new BigDecimal(offer.decoded().path("amount").asText("0")).abs();
    }

    private BigDecimal sumAmounts(List<FundingPositionDto> positions) {
        return positions.stream()
                .map(this::offerAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal nonNegative(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : value;
    }
}
