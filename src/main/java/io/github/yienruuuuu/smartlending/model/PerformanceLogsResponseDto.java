package io.github.yienruuuuu.smartlending.model;

import java.util.List;

public record PerformanceLogsResponseDto(
        String account,
        String range,
        String type,
        String q,
        int page,
        int size,
        int totalCount,
        PerformanceLogsSummaryDto summary,
        List<PerformanceLogRowDto> items
) {
}
