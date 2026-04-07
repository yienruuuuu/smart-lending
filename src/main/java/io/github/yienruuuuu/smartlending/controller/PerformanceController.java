package io.github.yienruuuuu.smartlending.controller;

import io.github.yienruuuuu.smartlending.model.PerformanceLatestSnapshotsDto;
import io.github.yienruuuuu.smartlending.model.PerformanceCashflowEvent;
import io.github.yienruuuuu.smartlending.model.PerformanceSeriesResponseDto;
import io.github.yienruuuuu.smartlending.model.PerformanceSummaryDto;
import io.github.yienruuuuu.smartlending.service.PerformanceCashflowService;
import io.github.yienruuuuu.smartlending.service.PerformanceMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供 performance 報表與 dashboard 使用的 API。
 */
@Tag(name = "Performance", description = "Funding performance snapshots 與報表 API")
@RestController
@RequestMapping("/api/v1/performance")
public class PerformanceController {

    private final PerformanceMetricsService performanceMetricsService;
    private final PerformanceCashflowService performanceCashflowService;

    public PerformanceController(
            PerformanceMetricsService performanceMetricsService,
            PerformanceCashflowService performanceCashflowService
    ) {
        this.performanceMetricsService = performanceMetricsService;
        this.performanceCashflowService = performanceCashflowService;
    }

    @Operation(summary = "查詢報酬率摘要")
    @GetMapping("/summary")
    public ResponseEntity<PerformanceSummaryDto> getSummary(
            @Parameter(description = "帳戶範圍：main、sub、combined") @RequestParam(required = false) String account,
            @Parameter(description = "時間範圍：7d、30d、90d、all") @RequestParam(required = false) String range
    ) {
        return ResponseEntity.ok(performanceMetricsService.getSummary(account, range));
    }

    @Operation(summary = "查詢績效時間序列")
    @GetMapping("/series")
    public ResponseEntity<PerformanceSeriesResponseDto> getSeries(
            @Parameter(description = "帳戶範圍：main、sub、combined") @RequestParam(required = false) String account,
            @Parameter(description = "時間範圍：7d、30d、90d、all") @RequestParam(required = false) String range
    ) {
        return ResponseEntity.ok(performanceMetricsService.getSeries(account, range));
    }

    @Operation(summary = "查詢最新快照")
    @GetMapping("/snapshots/latest")
    public ResponseEntity<PerformanceLatestSnapshotsDto> getLatestSnapshots() {
        return ResponseEntity.ok(performanceMetricsService.getLatestSnapshots());
    }

    @Operation(summary = "查詢績效現金流事件")
    @GetMapping("/cashflows")
    public ResponseEntity<List<PerformanceCashflowEvent>> getCashflows(
            @Parameter(description = "帳戶範圍：main、sub、combined") @RequestParam(required = false) String account,
            @Parameter(description = "時間範圍：7d、30d、90d、all") @RequestParam(required = false) String range
    ) {
        return ResponseEntity.ok(performanceCashflowService.getCashflows(account, range));
    }
}
