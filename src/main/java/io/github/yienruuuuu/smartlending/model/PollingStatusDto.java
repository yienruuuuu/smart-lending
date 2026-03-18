package io.github.yienruuuuu.smartlending.model;

import com.fasterxml.jackson.databind.JsonNode;

public record PollingStatusDto(
        boolean pollingEnabled,
        int pollingIntervalSeconds,
        JsonNode lastPollSummary
) {
}
