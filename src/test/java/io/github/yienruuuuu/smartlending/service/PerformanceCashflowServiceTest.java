package io.github.yienruuuuu.smartlending.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yienruuuuu.smartlending.config.BitfinexProperties;
import io.github.yienruuuuu.smartlending.config.PerformanceProperties;
import io.github.yienruuuuu.smartlending.model.BitfinexBalanceHistoryEntry;
import io.github.yienruuuuu.smartlending.model.BitfinexMovementHistoryEntry;
import io.github.yienruuuuu.smartlending.model.PerformanceCashflowType;
import io.github.yienruuuuu.smartlending.model.PerformanceSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
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
    void shouldSyncMainCashflowsFromBitfinexApisAndDeduplicate() {
        BitfinexProperties bitfinexProperties = new BitfinexProperties();
        bitfinexProperties.setApiKey("main-key");
        bitfinexProperties.setApiSecret("main-secret");

        BitfinexAccountRestClient mainClient = mock(BitfinexAccountRestClient.class);
        SubBitfinexAccountRestClient subClient = mock(SubBitfinexAccountRestClient.class);
        PerformanceSnapshotFileRepository snapshotRepository = snapshotRepository();
        snapshotRepository.append(snapshot("main", "2026-03-01T00:00:00Z", "1000"));

        when(mainClient.getMovementHistory(eq("USD"), any(Instant.class), any(Instant.class))).thenReturn(List.of(
                new BitfinexMovementHistoryEntry("m1", "USD", "wire", "DEPOSIT", "COMPLETED", new BigDecimal("100"), BigDecimal.ZERO, Instant.parse("2026-03-02T00:00:00Z"), "tx1", null)
        ));
        when(mainClient.getBalanceHistory(eq("USD"), any(Instant.class), any(Instant.class))).thenReturn(List.of(
                new BitfinexBalanceHistoryEntry("USD", new BigDecimal("-50"), new BigDecimal("950"), "Transfer from funding wallet", Instant.parse("2026-03-03T00:00:00Z"))
        ));

        PerformanceCashflowService service = new PerformanceCashflowService(
                bitfinexProperties,
                mainClient,
                subClient,
                snapshotRepository,
                cashflowRepository(),
                syncStateRepository()
        );

        int firstSyncCount = service.syncAll();
        int secondSyncCount = service.syncAll();

        List<?> cashflows = service.getCashflows("main", "all");
        assertThat(firstSyncCount).isEqualTo(2);
        assertThat(secondSyncCount).isEqualTo(2);
        assertThat(cashflows).hasSize(2);
        assertThat(service.getCashflows("main", "all").get(0).type()).isEqualTo(PerformanceCashflowType.DEPOSIT);
        assertThat(service.getCashflows("main", "all").get(1).type()).isEqualTo(PerformanceCashflowType.INTERNAL_TRANSFER_OUT);
        verify(mainClient, times(2)).getMovementHistory(eq("USD"), any(Instant.class), any(Instant.class));
    }

    @Test
    void shouldIgnoreUnsettledMovementsAndNonTransferHistoryEntries() {
        BitfinexProperties bitfinexProperties = new BitfinexProperties();
        bitfinexProperties.setApiKey("main-key");
        bitfinexProperties.setApiSecret("main-secret");

        BitfinexAccountRestClient mainClient = mock(BitfinexAccountRestClient.class);
        SubBitfinexAccountRestClient subClient = mock(SubBitfinexAccountRestClient.class);
        PerformanceSnapshotFileRepository snapshotRepository = snapshotRepository();
        snapshotRepository.append(snapshot("main", "2026-03-01T00:00:00Z", "1000"));

        when(mainClient.getMovementHistory(eq("USD"), any(Instant.class), any(Instant.class))).thenReturn(List.of(
                new BitfinexMovementHistoryEntry("m1", "USD", "wire", "WITHDRAWAL", "PENDING", new BigDecimal("100"), BigDecimal.ZERO, Instant.parse("2026-03-02T00:00:00Z"), "tx1", null)
        ));
        when(mainClient.getBalanceHistory(eq("USD"), any(Instant.class), any(Instant.class))).thenReturn(List.of(
                new BitfinexBalanceHistoryEntry("USD", new BigDecimal("-50"), new BigDecimal("950"), "Interest payment", Instant.parse("2026-03-03T00:00:00Z"))
        ));

        PerformanceCashflowService service = new PerformanceCashflowService(
                bitfinexProperties,
                mainClient,
                subClient,
                snapshotRepository,
                cashflowRepository(),
                syncStateRepository()
        );

        int syncCount = service.syncAll();

        assertThat(syncCount).isZero();
        assertThat(service.getCashflows("main", "all")).isEmpty();
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
