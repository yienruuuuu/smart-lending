package io.github.yienruuuuu.smartlending.service;

import io.github.yienruuuuu.smartlending.config.BitfinexProperties;
import io.github.yienruuuuu.smartlending.model.FundingPositionDto;
import io.github.yienruuuuu.smartlending.model.PerformanceSnapshot;
import io.github.yienruuuuu.smartlending.model.WalletBalanceDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 收集主帳戶與 sub account 的 funding performance 快照。
 */
@Service
public class PerformanceSnapshotCollectorService {

    private static final Logger log = LoggerFactory.getLogger(PerformanceSnapshotCollectorService.class);
    private static final String TARGET_CURRENCY = "USD";
    private static final String TARGET_SYMBOL = "fUSD";
    private static final String SOURCE = "bitfinex-live";

    private final BitfinexProperties bitfinexProperties;
    private final BitfinexAccountRestClient bitfinexAccountRestClient;
    private final BitfinexFundingAccountRestClient bitfinexFundingAccountRestClient;
    private final SubBitfinexAccountRestClient subBitfinexAccountRestClient;
    private final SubBitfinexFundingAccountRestClient subBitfinexFundingAccountRestClient;
    private final PerformanceSnapshotFileRepository repository;

    public PerformanceSnapshotCollectorService(
            BitfinexProperties bitfinexProperties,
            BitfinexAccountRestClient bitfinexAccountRestClient,
            BitfinexFundingAccountRestClient bitfinexFundingAccountRestClient,
            SubBitfinexAccountRestClient subBitfinexAccountRestClient,
            SubBitfinexFundingAccountRestClient subBitfinexFundingAccountRestClient,
            PerformanceSnapshotFileRepository repository
    ) {
        this.bitfinexProperties = bitfinexProperties;
        this.bitfinexAccountRestClient = bitfinexAccountRestClient;
        this.bitfinexFundingAccountRestClient = bitfinexFundingAccountRestClient;
        this.subBitfinexAccountRestClient = subBitfinexAccountRestClient;
        this.subBitfinexFundingAccountRestClient = subBitfinexFundingAccountRestClient;
        this.repository = repository;
    }

    public List<PerformanceSnapshot> captureAll() {
        Instant capturedAt = Instant.now();
        List<PerformanceSnapshot> captured = new ArrayList<>();

        captureMain(capturedAt).ifPresent(snapshot -> {
            repository.append(snapshot);
            captured.add(snapshot);
        });

        captureSub(capturedAt).ifPresent(snapshot -> {
            repository.append(snapshot);
            captured.add(snapshot);
        });

        return captured;
    }

    private Optional<PerformanceSnapshot> captureMain(Instant capturedAt) {
        if (!bitfinexProperties.hasMainAccountCredentials()) {
            log.debug("略過 main performance snapshot：未設定主帳戶 API 憑證");
            return Optional.empty();
        }

        return Optional.of(buildSnapshot(
                "main",
                capturedAt,
                bitfinexAccountRestClient.getWallets(),
                bitfinexFundingAccountRestClient.getFundingOffers(TARGET_SYMBOL),
                bitfinexFundingAccountRestClient.getFundingCredits(TARGET_SYMBOL),
                bitfinexFundingAccountRestClient.getFundingLoans(TARGET_SYMBOL)
        ));
    }

    private Optional<PerformanceSnapshot> captureSub(Instant capturedAt) {
        if (!bitfinexProperties.hasSubAccountCredentials()) {
            log.debug("略過 sub performance snapshot：未設定 sub account API 憑證");
            return Optional.empty();
        }

        return Optional.of(buildSnapshot(
                "sub",
                capturedAt,
                subBitfinexAccountRestClient.getWallets(),
                subBitfinexFundingAccountRestClient.getFundingOffers(TARGET_SYMBOL),
                subBitfinexFundingAccountRestClient.getFundingCredits(TARGET_SYMBOL),
                subBitfinexFundingAccountRestClient.getFundingLoans(TARGET_SYMBOL)
        ));
    }

    private PerformanceSnapshot buildSnapshot(
            String account,
            Instant capturedAt,
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

        BigDecimal totalWalletAmount = nullSafe(wallet.balance());
        BigDecimal idleAmount = nullSafe(wallet.availableBalance());
        BigDecimal offerAmount = sumAmounts(offers);
        BigDecimal creditAmount = sumAmounts(credits);
        BigDecimal loanAmount = sumAmounts(loans);
        BigDecimal walletDerivedLentAmount = nonNegative(totalWalletAmount.subtract(idleAmount).subtract(offerAmount));
        BigDecimal lentAmount = walletDerivedLentAmount.max(creditAmount.max(loanAmount));
        BigDecimal utilizationRatio = ratio(lentAmount, totalWalletAmount);

        return new PerformanceSnapshot(
                account,
                TARGET_SYMBOL,
                TARGET_CURRENCY,
                capturedAt,
                totalWalletAmount,
                idleAmount,
                offerAmount,
                creditAmount,
                loanAmount,
                lentAmount,
                nullSafe(wallet.unsettledInterest()),
                utilizationRatio,
                SOURCE
        );
    }

    private BigDecimal sumAmounts(List<FundingPositionDto> positions) {
        return positions.stream()
                .map(item -> item.decoded().path("amount").asText("0"))
                .map(BigDecimal::new)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal nonNegative(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : value;
    }
}
