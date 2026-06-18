package io.github.yienruuuuu.smartlending.controller;

import io.github.yienruuuuu.smartlending.model.PerformanceCashflowEvent;
import io.github.yienruuuuu.smartlending.model.PerformanceCashflowStatus;
import io.github.yienruuuuu.smartlending.model.PerformanceCashflowSyncAccountResultDto;
import io.github.yienruuuuu.smartlending.model.PerformanceCashflowSyncResponseDto;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    void shouldReturnMainPerformanceSummary() throws Exception {
        when(performanceMetricsService.getSummary("main", "30d")).thenReturn(new PerformanceSummaryDto(
                "main",
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
                new BigDecimal("2.18868048"),
                new BigDecimal("218.87"),
                BigDecimal.ZERO,
                new BigDecimal("250"),
                new BigDecimal("150"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("450"),
                new BigDecimal("30"),
                new BigDecimal("0.40909091"),
                PerformanceCashflowStatus.EMPTY,
                "尚未同步 cashflow ledger，績效可能把入金或轉出誤算成報酬。"
        ));

        mockMvc.perform(get("/api/v1/performance/summary")
                        .param("account", "main")
                        .param("range", "30d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account").value("main"))
                .andExpect(jsonPath("$.twrAnnualizedReturnPercent").value(218.87))
                .andExpect(jsonPath("$.cashflowStatus").value("EMPTY"))
                .andExpect(jsonPath("$.xirrPercent").value(218.87));

        verify(performanceMetricsService).getSummary("main", "30d");
    }

    @Test
    void shouldReturnMainPerformanceSeries() throws Exception {
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
                        new BigDecimal("30"),
                        new BigDecimal("100.00000000"),
                        BigDecimal.ZERO
                ))
        ));

        mockMvc.perform(get("/api/v1/performance/series")
                        .param("account", "main")
                        .param("range", "7d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account").value("main"))
                .andExpect(jsonPath("$.pointCount").value(1))
                .andExpect(jsonPath("$.points[0].totalWalletAmount").value(1100))
                .andExpect(jsonPath("$.points[0].twrIndex").value(100.00000000))
                .andExpect(jsonPath("$.points[0].periodCashflow").value(0));
    }

    @Test
    void shouldReturnMainLatestSnapshot() throws Exception {
        when(performanceMetricsService.getLatestSnapshots()).thenReturn(new PerformanceLatestSnapshotsDto(
                new PerformanceSnapshot("main", "fUSD", "USD", Instant.parse("2026-03-31T00:00:00Z"), new BigDecimal("1100"), new BigDecimal("250"), new BigDecimal("150"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("450"), new BigDecimal("30"), new BigDecimal("0.4"), "test")
        ));

        mockMvc.perform(get("/api/v1/performance/snapshots/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.main.account").value("main"))
                .andExpect(jsonPath("$.main.totalWalletAmount").value(1100))
                .andExpect(jsonPath("$.combined").doesNotExist())
                .andExpect(jsonPath("$.sub").doesNotExist());
    }

    @Test
    void shouldReturnMainCashflows() throws Exception {
        when(performanceCashflowService.getCashflows("main", "30d")).thenReturn(List.of(
                new PerformanceCashflowEvent(
                        "main",
                        "fUSD",
                        "USD",
                        Instant.parse("2026-03-15T00:00:00Z"),
                        new BigDecimal("-300"),
                        PerformanceCashflowType.INTERNAL_TRANSFER_OUT,
                        "main:ledger:1",
                        null,
                        "bitfinex-v2-ledger",
                        "ledger",
                        "Transfer of 300 USD from wallet Funding to Deposit on wallet exchange"
                )
        ));

        mockMvc.perform(get("/api/v1/performance/cashflows")
                        .param("account", "main")
                        .param("range", "30d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("INTERNAL_TRANSFER_OUT"))
                .andExpect(jsonPath("$[0].source").value("bitfinex-v2-ledger"))
                .andExpect(jsonPath("$[0].amount").value(-300));
    }

    @Test
    void shouldManuallySyncCashflows() throws Exception {
        when(performanceCashflowService.syncAll()).thenReturn(new PerformanceCashflowSyncResponseDto(
                Instant.parse("2026-06-18T00:00:00Z"),
                2,
                "cashflow sync completed",
                List.of(new PerformanceCashflowSyncAccountResultDto("main", 4, 2, 2, List.of("funding 1.23 Margin Funding Payment")))
        ));

        mockMvc.perform(post("/api/v1/performance/cashflows/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncedCount").value(2))
                .andExpect(jsonPath("$.message").value("cashflow sync completed"))
                .andExpect(jsonPath("$.accounts[0].account").value("main"))
                .andExpect(jsonPath("$.accounts[0].ledgerFetchedCount").value(4));

        verify(performanceCashflowService).syncAll();
    }

    @Test
    void shouldReturnMainPerformanceLogs() throws Exception {
        when(performanceLogsService.getLogs("main", "30d", "all", null, 0, 50)).thenReturn(
                new PerformanceLogsResponseDto(
                        "main",
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
                                        "main",
                                        Instant.parse("2026-03-31T00:00:00Z"),
                                        "資產快照",
                                        "snapshot",
                                        null,
                                        new BigDecimal("1100"),
                                        new BigDecimal("250"),
                                        new BigDecimal("450"),
                                        new BigDecimal("0.4"),
                                        "snapshot:main:1",
                                        "bitfinex-live",
                                        "snapshot",
                                        "wallet=USD"
                                )
                        )
                )
        );

        mockMvc.perform(get("/api/v1/performance/logs")
                        .param("account", "main")
                        .param("range", "30d")
                        .param("type", "all")
                        .param("page", "0")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account").value("main"))
                .andExpect(jsonPath("$.summary.eventCount").value(2))
                .andExpect(jsonPath("$.items[0].account").value("main"));
    }
}
