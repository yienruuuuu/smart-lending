package io.github.yienruuuuu.smartlending.model;

public record PerformanceLatestSnapshotsDto(
        PerformanceSnapshot main,
        PerformanceSnapshot sub,
        PerformanceSnapshot combined
) {
}
