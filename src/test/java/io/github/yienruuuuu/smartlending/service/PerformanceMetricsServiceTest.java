package io.github.yienruuuuu.smartlending.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yienruuuuu.smartlending.config.PerformanceProperties;
import io.github.yienruuuuu.smartlending.model.PerformanceCashflowEvent;
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
    }

    @Test
    void shouldExcludeInternalTransfersFromCombinedTwr() {
        PerformanceMetricsService service = service();
        snapshotRepository().append(snapshot("main", "2026-03-01T00:00:00Z", "1000", "300", "100", "300", "280"));
        snapshotRepository().append(snapshot("main", "2026-03-31T00:00:00Z", "700", "300", "100", "300", "280"));
        snapshotRepository().append(snapshot("sub", "2026-03-01T00:00:00Z", "500", "140", "60", "140", "120"));
        snapshotRepository().append(snapshot("sub", "2026-03-31T00:00:00Z", "800", "140", "60", "140", "120"));
        cashflowRepository().merge("main", java.util.List.of(cashflow("main", "2026-03-15T00:00:00Z", "-300", PerformanceCashflowType.INTERNAL_TRANSFER_OUT)));
        cashflowRepository().merge("sub", java.util.List.of(cashflow("sub", "2026-03-15T00:00:00Z", "300", PerformanceCashflowType.INTERNAL_TRANSFER_IN)));

        var summary = service.getSummary("combined", "30d");

        assertThat(summary.startValue()).isEqualByComparingTo("1500");
        assertThat(summary.endValue()).isEqualByComparingTo("1500");
        assertThat(summary.twrReturnPercent()).isEqualByComparingTo("0.00");
        assertThat(summary.xirrPercent()).isNull();
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
    }

    @Test
    void shouldCombineMainAndSubSeriesUsingLatestAvailableSnapshots() {
        PerformanceMetricsService service = service();
        snapshotRepository().append(snapshot("main", "2026-03-01T00:00:00Z", "1000", "300", "100", "300", "280"));
        snapshotRepository().append(snapshot("main", "2026-03-02T00:00:00Z", "1050", "260", "120", "350", "290"));
        snapshotRepository().append(snapshot("sub", "2026-03-01T12:00:00Z", "500", "140", "60", "140", "120"));
        snapshotRepository().append(snapshot("sub", "2026-03-02T12:00:00Z", "540", "120", "70", "180", "150"));

        var series = service.getSeries("combined", "all");

        assertThat(series.pointCount()).isEqualTo(4);
        assertThat(series.points().get(1).totalWalletAmount()).isEqualByComparingTo("1500");
        assertThat(series.points().get(2).totalWalletAmount()).isEqualByComparingTo("1550");
        assertThat(series.points().get(3).totalWalletAmount()).isEqualByComparingTo("1590");
    }

    private PerformanceMetricsService service() {
        return new PerformanceMetricsService(snapshotRepository(), cashflowRepository());
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
