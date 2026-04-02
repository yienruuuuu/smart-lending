package io.github.yienruuuuu.smartlending.controller;

import io.github.yienruuuuu.smartlending.model.PerformanceLatestSnapshotsDto;
import io.github.yienruuuuu.smartlending.model.PerformanceSeriesPointDto;
import io.github.yienruuuuu.smartlending.model.PerformanceSeriesResponseDto;
import io.github.yienruuuuu.smartlending.model.PerformanceSnapshot;
import io.github.yienruuuuu.smartlending.model.PerformanceSummaryDto;
import io.github.yienruuuuu.smartlending.service.PerformanceMetricsService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PerformanceController.class)
class PerformanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PerformanceMetricsService performanceMetricsService;

    @Test
    void shouldReturnPerformanceSummary() throws Exception {
        when(performanceMetricsService.getSummary("combined", "30d")).thenReturn(new PerformanceSummaryDto(
                "combined",
                "30d",
                2,
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-03-31T00:00:00Z"),
                new BigDecimal("1000"),
                new BigDecimal("1100"),
                new BigDecimal("100"),
                new BigDecimal("0.1"),
                new BigDecimal("10.00"),
                new BigDecimal("2.18868048"),
                new BigDecimal("218.87"),
                new BigDecimal("250"),
                new BigDecimal("150"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("450"),
                new BigDecimal("30"),
                new BigDecimal("0.40909091")
        ));

        mockMvc.perform(get("/api/v1/performance/summary")
                        .param("account", "combined")
                        .param("range", "30d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account").value("combined"))
                .andExpect(jsonPath("$.annualizedReturnPercent").value(218.87));

        verify(performanceMetricsService).getSummary("combined", "30d");
    }

    @Test
    void shouldReturnPerformanceSeries() throws Exception {
        when(performanceMetricsService.getSeries("main", "7d")).thenReturn(new PerformanceSeriesResponseDto(
                "main",
                "7d",
                1,
                List.of(new PerformanceSeriesPointDto(
                        Instant.parse("2026-03-31T00:00:00Z"),
                        new BigDecimal("1100"),
                        new BigDecimal("250"),
                        new BigDecimal("150"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("450"),
                        new BigDecimal("30")
                ))
        ));

        mockMvc.perform(get("/api/v1/performance/series")
                        .param("account", "main")
                        .param("range", "7d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointCount").value(1))
                .andExpect(jsonPath("$.points[0].totalWalletAmount").value(1100));
    }

    @Test
    void shouldReturnLatestSnapshots() throws Exception {
        when(performanceMetricsService.getLatestSnapshots()).thenReturn(new PerformanceLatestSnapshotsDto(
                new PerformanceSnapshot("main", "fUSD", "USD", Instant.parse("2026-03-31T00:00:00Z"), new BigDecimal("1100"), new BigDecimal("250"), new BigDecimal("150"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("450"), new BigDecimal("30"), new BigDecimal("0.4"), "test"),
                new PerformanceSnapshot("sub", "fUSD", "USD", Instant.parse("2026-03-31T00:00:00Z"), new BigDecimal("500"), new BigDecimal("100"), new BigDecimal("80"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("220"), new BigDecimal("15"), new BigDecimal("0.44"), "test"),
                new PerformanceSnapshot("combined", "fUSD", "USD", Instant.parse("2026-03-31T00:00:00Z"), new BigDecimal("1600"), new BigDecimal("350"), new BigDecimal("230"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("670"), new BigDecimal("45"), new BigDecimal("0.41875"), "aggregated")
        ));

        mockMvc.perform(get("/api/v1/performance/snapshots/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.combined.totalWalletAmount").value(1600))
                .andExpect(jsonPath("$.main.account").value("main"));
    }
}
