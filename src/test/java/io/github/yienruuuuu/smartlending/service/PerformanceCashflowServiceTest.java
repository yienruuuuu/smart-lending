package io.github.yienruuuuu.smartlending.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yienruuuuu.smartlending.config.BitfinexProperties;
import io.github.yienruuuuu.smartlending.config.PerformanceProperties;
import io.github.yienruuuuu.smartlending.model.BitfinexLedgerEntry;
import io.github.yienruuuuu.smartlending.model.PerformanceCashflowType;
import io.github.yienruuuuu.smartlending.model.PerformanceSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PerformanceCashflowServiceTest {

    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void shouldSyncMainFundingTransfersFromBitfinexV2LedgerAndDeduplicate() {
        BitfinexProperties bitfinexProperties = mainCredentials();
        BitfinexAccountRestClient mainClient = mock(BitfinexAccountRestClient.class);
        PerformanceSnapshotFileRepository snapshotRepository = snapshotRepository();
        snapshotRepository.append(snapshot("main", "2026-03-01T00:00:00Z", "1000"));

        when(mainClient.getLedgerHistory(eq("USD"), any(Instant.class), any(Instant.class), eq(250))).thenReturn(List.of(
                ledger(1001, "funding", "2026-03-02T00:00:00Z", "507.57178087", "Transfer of 507.57178087 USD from wallet Exchange to Deposit on wallet funding"),
                ledger(1002, "funding", "2026-03-03T00:00:00Z", "-100", "Transfer of 100 USD from wallet Funding to Deposit on wallet exchange"),
                ledger(1003, "funding", "2026-03-04T00:00:00Z", "1.23", "Margin Funding Payment on wallet funding"),
                ledger(1004, "exchange", "2026-03-05T00:00:00Z", "-507.57178087", "Transfer of 507.57178087 USD from wallet Exchange to Deposit on wallet funding")
        ));

        PerformanceCashflowService service = new PerformanceCashflowService(
                bitfinexProperties,
                mainClient,
                snapshotRepository,
                cashflowRepository(),
                syncStateRepository()
        );

        var firstResponse = service.syncAll();
        var secondResponse = service.syncAll();

        var cashflows = service.getCashflows("main", "all");
        assertThat(firstResponse.syncedCount()).isEqualTo(2);
        assertThat(firstResponse.accounts()).singleElement()
                .satisfies(account -> {
                    assertThat(account.account()).isEqualTo("main");
                    assertThat(account.ledgerFetchedCount()).isEqualTo(4);
                    assertThat(account.cashflowSyncedCount()).isEqualTo(2);
                    assertThat(account.ignoredCount()).isEqualTo(2);
                    assertThat(account.ignoredSamples()).hasSize(2);
                });
        assertThat(secondResponse.syncedCount()).isEqualTo(2);
        assertThat(cashflows).hasSize(2);
        assertThat(cashflows.get(0).type()).isEqualTo(PerformanceCashflowType.INTERNAL_TRANSFER_IN);
        assertThat(cashflows.get(0).amount()).isEqualByComparingTo("507.57178087");
        assertThat(cashflows.get(0).referenceId()).isEqualTo("main:ledger:1001");
        assertThat(cashflows.get(1).type()).isEqualTo(PerformanceCashflowType.INTERNAL_TRANSFER_OUT);
        assertThat(cashflows.get(1).amount()).isEqualByComparingTo("-100");
        assertThat(cashflows.get(1).source()).isEqualTo("bitfinex-v2-ledger");
        verify(mainClient, times(2)).getLedgerHistory(eq("USD"), any(Instant.class), any(Instant.class), eq(250));
    }

    @Test
    void shouldBackfillFromEarliestSnapshotWhenLedgerFileIsEmptyEvenIfSyncStateExists() {
        BitfinexAccountRestClient mainClient = mock(BitfinexAccountRestClient.class);
        PerformanceSnapshotFileRepository snapshotRepository = snapshotRepository();
        snapshotRepository.append(snapshot("main", "2026-03-01T00:00:00Z", "1000"));
        PerformanceCashflowSyncStateRepository syncStateRepository = syncStateRepository();
        syncStateRepository.save(new io.github.yienruuuuu.smartlending.model.PerformanceCashflowSyncState(
                Instant.parse("2026-03-10T00:00:00Z")
        ));
        when(mainClient.getLedgerHistory(eq("USD"), any(Instant.class), any(Instant.class), eq(250))).thenReturn(List.of());

        PerformanceCashflowService service = new PerformanceCashflowService(
                mainCredentials(),
                mainClient,
                snapshotRepository,
                cashflowRepository(),
                syncStateRepository
        );

        service.syncAll();

        verify(mainClient).getLedgerHistory(
                eq("USD"),
                eq(Instant.parse("2026-02-28T00:00:00Z")),
                any(Instant.class),
                eq(250)
        );
    }

    @Test
    void shouldRejectNonMainCashflowQueries() {
        PerformanceCashflowService service = new PerformanceCashflowService(
                mainCredentials(),
                mock(BitfinexAccountRestClient.class),
                snapshotRepository(),
                cashflowRepository(),
                syncStateRepository()
        );

        assertThatThrownBy(() -> service.getCashflows("sub", "all"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("account must be main");
        assertThatThrownBy(() -> service.getCashflows("combined", "all"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("account must be main");
    }

    private BitfinexProperties mainCredentials() {
        BitfinexProperties bitfinexProperties = new BitfinexProperties();
        bitfinexProperties.setApiKey("main-key");
        bitfinexProperties.setApiSecret("main-secret");
        return bitfinexProperties;
    }

    private PerformanceSnapshotFileRepository snapshotRepository() {
        PerformanceProperties properties = new PerformanceProperties();
        properties.setStoragePath(tempDir.toString());
        return new PerformanceSnapshotFileRepository(properties, new ObjectMapper());
    }

    private PerformanceCashflowFileRepository cashflowRepository() {
        PerformanceProperties properties = new PerformanceProperties();
        properties.setStoragePath(tempDir.toString());
        return new PerformanceCashflowFileRepository(properties, new ObjectMapper());
    }

    private PerformanceCashflowSyncStateRepository syncStateRepository() {
        PerformanceProperties properties = new PerformanceProperties();
        properties.setStoragePath(tempDir.toString());
        return new PerformanceCashflowSyncStateRepository(properties, new ObjectMapper());
    }

    private BitfinexLedgerEntry ledger(long id, String wallet, String timestamp, String amount, String description) {
        return new BitfinexLedgerEntry(
                id,
                "USD",
                wallet,
                Instant.parse(timestamp),
                new BigDecimal(amount),
                BigDecimal.ZERO,
                description
        );
    }

    private PerformanceSnapshot snapshot(String account, String capturedAt, String totalWalletAmount) {
        return new PerformanceSnapshot(
                account,
                "fUSD",
                "USD",
                Instant.parse(capturedAt),
                new BigDecimal(totalWalletAmount),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "test"
        );
    }
}
