package io.github.yienruuuuu.smartlending.model;

import java.time.Instant;

public record PerformanceCashflowSyncState(
        Instant mainLastSyncedAt,
        Instant subLastSyncedAt
) {
}
