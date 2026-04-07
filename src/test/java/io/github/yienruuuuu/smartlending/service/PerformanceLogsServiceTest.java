package io.github.yienruuuuu.smartlending.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yienruuuuu.smartlending.config.PerformanceProperties;
import io.github.yienruuuuu.smartlending.model.PerformanceCashflowEvent;
import io.github.yienruuuuu.smartlending.model.PerformanceCashflowType;
import io.github.yienruuuuu.smartlending.model.PerformanceSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PerformanceLogsServiceTest {

    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void shouldReturnUnifiedLogsForSingleAccount() {
        PerformanceSnapshotFileRepository snapshotRepository = snapshotRepository();
        snapshotRepository.append(snapshot("main", "2026-03-01T00:00:00Z", "1000"));
        snapshotRepository.append(snapshot("main", "2026-03-02T00:00:00Z", "1100"));

        PerformanceCashflowService cashflowService = mock(PerformanceCashflowService.class);
        when(cashflowService.getCashflows("main", "30d")).thenReturn(List.of(
                cashflow("main", "2026-03-01T12:00:00Z", "100", PerformanceCashflowType.DEPOSIT, "ref-1", "wire deposit")
        ));

        PerformanceLogsService service = new PerformanceLogsService(snapshotRepository, cashflowService);

        var response = service.getLogs("main", "30d", "all", null, 0, 50);

        assertThat(response.totalCount()).isEqualTo(3);
        assertThat(response.summary().snapshotCount()).isEqualTo(2);
        assertThat(response.summary().cashflowCount()).isEqualTo(1);
        assertThat(response.summary().netCashflow()).isEqualByComparingTo("100");
        assertThat(response.items().get(0).kind()).isEqualTo("snapshot");
    }

    @Test
    void shouldFilterLogsByTypeAndQuery() {
        PerformanceSnapshotFileRepository snapshotRepository = snapshotRepository();
        snapshotRepository.append(snapshot("main", "2026-03-01T00:00:00Z", "1000"));

        PerformanceCashflowService cashflowService = mock(PerformanceCashflowService.class);
        when(cashflowService.getCashflows("main", "30d")).thenReturn(List.of(
                cashflow("main", "2026-03-01T12:00:00Z", "-50", PerformanceCashflowType.WITHDRAWAL, "ref-2", "wire withdrawal")
        ));

        PerformanceLogsService service = new PerformanceLogsService(snapshotRepository, cashflowService);

        var response = service.getLogs("main", "30d", "withdrawal", "wire", 0, 50);

        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.items().get(0).type()).isEqualTo("withdrawal");
    }

    @Test
    void shouldBuildCombinedSnapshotsAndKeepRawCashflowAccounts() {
        PerformanceSnapshotFileRepository snapshotRepository = snapshotRepository();
        snapshotRepository.append(snapshot("main", "2026-03-01T00:00:00Z", "1000"));
        snapshotRepository.append(snapshot("sub", "2026-03-01T12:00:00Z", "500"));

        PerformanceCashflowService cashflowService = mock(PerformanceCashflowService.class);
        when(cashflowService.getCashflows("combined", "30d")).thenReturn(List.of(
                cashflow("sub", "2026-03-01T06:00:00Z", "25", PerformanceCashflowType.DEPOSIT, "ref-3", "sub deposit")
        ));

        PerformanceLogsService service = new PerformanceLogsService(snapshotRepository, cashflowService);

        var response = service.getLogs("combined", "30d", "all", null, 0, 50);

        assertThat(response.summary().snapshotCount()).isEqualTo(2);
        assertThat(response.items()).anyMatch(item -> "sub".equals(item.account()) && "cashflow".equals(item.kind()));
        assertThat(response.items()).anyMatch(item -> "combined".equals(item.account()) && "snapshot".equals(item.kind()));
    }

    @Test
    void shouldRejectUnsupportedType() {
        PerformanceLogsService service = new PerformanceLogsService(snapshotRepository(), mock(PerformanceCashflowService.class));

        assertThatThrownBy(() -> service.getLogs("main", "30d", "foo", null, 0, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type must be one of");
    }

    private PerformanceSnapshotFileRepository snapshotRepository() {
        PerformanceProperties properties = new PerformanceProperties();
        properties.setStoragePath(tempDir.toString());
        return new PerformanceSnapshotFileRepository(properties, new ObjectMapper());
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

    private PerformanceCashflowEvent cashflow(
            String account,
            String capturedAt,
            String amount,
            PerformanceCashflowType type,
            String referenceId,
            String note
    ) {
        return new PerformanceCashflowEvent(
                account,
                "fUSD",
                "USD",
                Instant.parse(capturedAt),
                new BigDecimal(amount),
                type,
                referenceId,
                null,
                "test",
                type.name(),
                note
        );
    }
}
