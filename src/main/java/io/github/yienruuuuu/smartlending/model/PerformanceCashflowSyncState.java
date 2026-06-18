package io.github.yienruuuuu.smartlending.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PerformanceCashflowSyncState(
        Instant mainLastSyncedAt
) {
}
