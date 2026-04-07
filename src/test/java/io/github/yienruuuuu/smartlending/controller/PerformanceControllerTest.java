package io.github.yienruuuuu.smartlending.controller;

import io.github.yienruuuuu.smartlending.model.PerformanceCashflowEvent;
import io.github.yienruuuuu.smartlending.model.PerformanceCashflowType;
import io.github.yienruuuuu.smartlending.model.PerformanceLatestSnapshotsDto;
import io.github.yienruuuuu.smartlending.model.PerformanceLogRowDto;
import io.github.yienruuuuu.smartlending.model.PerformanceLogsResponseDto;
import io.github.yienruuuuu.smartlending.model.PerformanceLogsSummaryDto;
import io.github.yienruuuuu.smartlending.model.PerformanceSeriesPointDto;
import io.github.yienruuuuu.smartlending.model.PerformanceSeriesResponseDto;
import io.github.yienruuuuu.smartlending.model.PerformanceSnapshot;
import io.github.yienruuuuu.smartlending.model.PerformanceSummaryDto;
import io.github.yienruuuuu.smartlending.service.PerformanceCashflowService;
import io.github.yienruuuuu.smartlending.service.PerformanceLogsService;
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

    @MockBean
    private PerformanceCashflowService performanceCashflowService;

    @MockBean
    private PerformanceLogsService performanceLogsService;

    @Test
    void shouldReturnPerformanceSummary() throws Exception {
        when(performanceMetricsService.getSummary("combined", "30d")).thenReturn(new PerformanceSummaryDto(
                "combined",
                "30d",
                2,
                0,
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-03-31T00:00:00Z"),
                new BigDecimal("1000"),
                new BigDecimal("1100"),
                new BigDecimal("100"),
                new BigDecimal("0.1"),
                new BigDecimal("10.00"),
                new BigDecimal("2.18868048"),
                new BigDecimal("218.87"),
                new BigDecimal("0.1"),
                new BigDecimal("10.00"),
                new BigDecimal("2.18868048"),
                new BigDecimal("218.87"),
                null,
                null,
                BigDecimal.ZERO,
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
                .andExpect(jsonPath("$.twrAnnualizedReturnPercent").value(218.87))
                .andExpect(jsonPath("$.xirrPercent").doesNotExist());

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

    @Test
    void shouldReturnCashflows() throws Exception {
        when(performanceCashflowService.getCashflows("main", "30d")).thenReturn(List.of(
                new PerformanceCashflowEvent(
                        "main",
                        "fUSD",
                        "USD",
                        Instant.parse("2026-03-15T00:00:00Z"),
                        new BigDecimal("-300"),
                        PerformanceCashflowType.INTERNAL_TRANSFER_OUT,
                        "main:history:1",
                        "sub",
                        "bitfinex-v1-history",
                        "transfer",
                        "rebalance"
                )
        ));

        mockMvc.perform(get("/api/v1/performance/cashflows")
                        .param("account", "main")
                        .param("range", "30d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("INTERNAL_TRANSFER_OUT"))
                .andExpect(jsonPath("$[0].amount").value(-300));
    }

    @Test
    void shouldReturnPerformanceLogs() throws Exception {
        when(performanceLogsService.getLogs("combined", "30d", "all", null, 0, 50)).thenReturn(
                new PerformanceLogsResponseDto(
                        "combined",
                        "30d",
                        "all",
                        null,
                        0,
                        50,
                        2,
                        new PerformanceLogsSummaryDto(
                                2,
                                1,
                                1,
                                new BigDecimal("100"),
                                new BigDecimal("1000"),
                                new BigDecimal("1100"),
                                Instant.parse("2026-03-01T00:00:00Z"),
                                Instant.parse("2026-03-31T00:00:00Z")
                        ),
                        List.of(
                                new PerformanceLogRowDto(
                                        "snapshot",
                                        "combined",
                                        Instant.parse("2026-03-31T00:00:00Z"),
                                        "資產快照",
                                        "snapshot",
                                        null,
                                        new BigDecimal("1100"),
                                        new BigDecimal("250"),
                                        new BigDecimal("450"),
                                        new BigDecimal("0.4"),
                                        "snapshot:combined:1",
                                        "aggregated",
                                        "snapshot",
                                        "wallet=USD"
                                )
                        )
                )
        );

        mockMvc.perform(get("/api/v1/performance/logs")
                        .param("account", "combined")
                        .param("range", "30d")
                        .param("type", "all")
                        .param("page", "0")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.eventCount").value(2))
                .andExpect(jsonPath("$.items[0].kind").value("snapshot"));
    }
}
