package io.github.yienruuuuu.smartlending.model;

import com.fasterxml.jackson.databind.JsonNode;

public record FundingPositionDto(
        String type,
        String symbol,
        JsonNode raw,
        JsonNode decoded
) {
}
