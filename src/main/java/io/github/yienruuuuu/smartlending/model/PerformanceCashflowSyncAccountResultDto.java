package io.github.yienruuuuu.smartlending.model;

import java.util.List;

public record PerformanceCashflowSyncAccountResultDto(
        String account,
        int ledgerFetchedCount,
        int cashflowSyncedCount,
        int ignoredCount,
        List<String> ignoredSamples
) {
}
