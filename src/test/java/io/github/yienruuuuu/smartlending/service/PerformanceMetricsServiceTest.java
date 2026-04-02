package io.github.yienruuuuu.smartlending.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yienruuuuu.smartlending.config.PerformanceProperties;
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
    void shouldCalculateAnnualizedSummaryForSingleAccount() {
        PerformanceMetricsService service = service();
        repository().append(snapshot("main", "2026-03-01T00:00:00Z", "1000", "300", "100", "300", "280"));
        repository().append(snapshot("main", "2026-03-31T00:00:00Z", "1100", "250", "150", "450", "300"));

        var summary = service.getSummary("main", "30d");

        assertThat(summary.snapshotCount()).isEqualTo(2);
        assertThat(summary.startValue()).isEqualByComparingTo("1000");
        assertThat(summary.endValue()).isEqualByComparingTo("1100");
        assertThat(summary.totalReturnPercent()).isEqualByComparingTo("10.00");
        assertThat(summary.annualizedReturnPercent()).isGreaterThan(new BigDecimal("200.00"));
    }

    @Test
    void shouldCombineMainAndSubSeriesUsingLatestAvailableSnapshots() {
        PerformanceMetricsService service = service();
        repository().append(snapshot("main", "2026-03-01T00:00:00Z", "1000", "300", "100", "300", "280"));
        repository().append(snapshot("main", "2026-03-02T00:00:00Z", "1050", "260", "120", "350", "290"));
        repository().append(snapshot("sub", "2026-03-01T12:00:00Z", "500", "140", "60", "140", "120"));
        repository().append(snapshot("sub", "2026-03-02T12:00:00Z", "540", "120", "70", "180", "150"));

        var series = service.getSeries("combined", "all");

        assertThat(series.pointCount()).isEqualTo(4);
        assertThat(series.points().get(1).totalWalletAmount()).isEqualByComparingTo("1500");
        assertThat(series.points().get(2).totalWalletAmount()).isEqualByComparingTo("1550");
        assertThat(series.points().get(3).totalWalletAmount()).isEqualByComparingTo("1590");
    }

    private PerformanceMetricsService service() {
        return new PerformanceMetricsService(repository());
    }

    private PerformanceSnapshotFileRepository repository() {
        PerformanceProperties properties = new PerformanceProperties();
        properties.setStoragePath(tempDir.toString());
        return new PerformanceSnapshotFileRepository(properties, new ObjectMapper());
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
}
