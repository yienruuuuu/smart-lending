package io.github.yienruuuuu.smartlending.model;

import com.fasterxml.jackson.databind.JsonNode;

public record FundingOfferActionDto(
        String action,
        JsonNode raw,
        JsonNode decoded
) {
}
