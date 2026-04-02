package io.github.yienruuuuu.smartlending.model;

import java.util.List;

public record PerformanceSeriesResponseDto(
        String account,
        String range,
        int pointCount,
        List<PerformanceSeriesPointDto> points
) {
}
