package io.github.yienruuuuu.smartlending.model;

import java.time.Instant;
import java.util.List;

public record PerformanceCashflowSyncResponseDto(
        Instant syncedAt,
        int syncedCount,
        String message,
        List<PerformanceCashflowSyncAccountResultDto> accounts
) {
}
