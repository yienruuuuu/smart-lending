package io.github.yienruuuuu.smartlending.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yienruuuuu.smartlending.config.PerformanceProperties;
import io.github.yienruuuuu.smartlending.model.PerformanceCashflowEvent;
import io.github.yienruuuuu.smartlending.model.PerformanceCashflowStatus;
import io.github.yienruuuuu.smartlending.model.PerformanceCashflowType;
import io.github.yienruuuuu.smartlending.model.PerformanceSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class PerformanceMetricsServiceTest {

    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void shouldCalculateTwrAndXirrForSingleAccountWithoutCashflows() {
        PerformanceMetricsService service = service();
        snapshotRepository().append(snapshot("main", "2026-03-01T00:00:00Z", "1000", "300", "100", "300", "280"));
        snapshotRepository().append(snapshot("main", "2026-03-31T00:00:00Z", "1100", "250", "150", "450", "300"));

        var summary = service.getSummary("main", "30d");

        assertThat(summary.snapshotCount()).isEqualTo(2);
        assertThat(summary.cashflowCount()).isZero();
        assertThat(summary.totalReturnPercent()).isEqualByComparingTo("10.00");
        assertThat(summary.twrReturnPercent()).isEqualByComparingTo("10.00");
        assertThat(summary.xirrPercent()).isGreaterThan(new BigDecimal("200.00"));
        assertThat(summary.cashflowStatus()).isEqualTo(PerformanceCashflowStatus.EMPTY);
    }

    @Test
    void shouldAdjustMainTwrForTransferAndStillProvideXirr() {
        PerformanceMetricsService service = service();
        snapshotRepository().append(snapshot("main", "2026-03-01T00:00:00Z", "1000", "300", "100", "300", "280"));
        snapshotRepository().append(snapshot("main", "2026-03-31T00:00:00Z", "800", "250", "150", "450", "300"));
        cashflowRepository().merge("main", java.util.List.of(cashflow("main", "2026-03-15T00:00:00Z", "-300", PerformanceCashflowType.INTERNAL_TRANSFER_OUT)));

        var summary = service.getSummary("main", "30d");

        assertThat(summary.totalReturnPercent()).isEqualByComparingTo("-20.00");
        assertThat(summary.twrReturnPercent()).isEqualByComparingTo("10.00");
        assertThat(summary.xirrPercent()).isNotNull();
        assertThat(summary.netCashflow()).isEqualByComparingTo("-300");
        assertThat(summary.cashflowStatus()).isEqualTo(PerformanceCashflowStatus.OK);
    }

    @Test
    void shouldBuildTwrIndexSeriesWithoutJumpingOnCapitalInflow() {
        PerformanceMetricsService service = service();
        snapshotRepository().append(snapshot("main", "2026-03-01T00:00:00Z", "1000", "300", "100", "300", "280"));
        snapshotRepository().append(snapshot("main", "2026-03-15T00:00:00Z", "1517.57178087", "400", "100", "900", "285"));
        snapshotRepository().append(snapshot("main", "2026-03-31T00:00:00Z", "1532.57178087", "250", "150", "1000", "300"));
        cashflowRepository().merge("main", java.util.List.of(cashflow("main", "2026-03-10T00:00:00Z", "507.57178087", PerformanceCashflowType.INTERNAL_TRANSFER_IN)));

        var series = service.getSeries("main", "all");

        assertThat(series.points()).hasSize(3);
        assertThat(series.points().get(0).twrIndex()).isEqualByComparingTo("100");
        assertThat(series.points().get(1).periodCashflow()).isEqualByComparingTo("507.57178087");
        assertThat(series.points().get(1).twrIndex()).isEqualByComparingTo("101.00000000");
        assertThat(series.points().get(2).periodCashflow()).isEqualByComparingTo("0");
        assertThat(series.points().get(2).twrIndex()).isEqualByComparingTo("101.99830521");
    }

    @Test
    void shouldBuildTwrIndexSeriesWithoutDroppingOnCapitalOutflow() {
        PerformanceMetricsService service = service();
        snapshotRepository().append(snapshot("main", "2026-03-01T00:00:00Z", "1000", "300", "100", "300", "280"));
        snapshotRepository().append(snapshot("main", "2026-03-31T00:00:00Z", "800", "250", "150", "450", "300"));
        cashflowRepository().merge("main", java.util.List.of(cashflow("main", "2026-03-15T00:00:00Z", "-300", PerformanceCashflowType.INTERNAL_TRANSFER_OUT)));

        var series = service.getSeries("main", "all");

        assertThat(series.points()).hasSize(2);
        assertThat(series.points().get(1).periodCashflow()).isEqualByComparingTo("-300");
        assertThat(series.points().get(1).twrIndex()).isEqualByComparingTo("110.00000000");
    }

    @Test
    void shouldRejectNonMainAccounts() {
        PerformanceMetricsService service = service();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getSummary("combined", "30d"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("account must be main");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getSeries("sub", "all"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("account must be main");
    }

    private PerformanceMetricsService service() {
        return new PerformanceMetricsService(snapshotRepository(), cashflowRepository(), syncStateRepository());
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

    private PerformanceSnapshot snapshot(
            String account,
            String capturedAt,
            String totalWalletAmount,
            String idleAmount,
            String offerAmount,
            String lentAmount,
            String unsettledInterest
    ) {
        return new PerformanceSnapshot(
                account,
                "fUSD",
                "USD",
                Instant.parse(capturedAt),
                new BigDecimal(totalWalletAmount),
                new BigDecimal(idleAmount),
                new BigDecimal(offerAmount),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal(lentAmount),
                new BigDecimal(unsettledInterest),
                BigDecimal.ZERO,
                "test"
        );
    }

    private PerformanceCashflowEvent cashflow(
            String account,
            String capturedAt,
            String amount,
            PerformanceCashflowType type
    ) {
        return new PerformanceCashflowEvent(
                account,
                "fUSD",
                "USD",
                Instant.parse(capturedAt),
                new BigDecimal(amount),
                type,
                account + ":" + capturedAt + ":" + type,
                null,
                "test",
                type.name(),
                null
        );
    }
}
