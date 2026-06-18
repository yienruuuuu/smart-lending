package io.github.yienruuuuu.smartlending.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FundingStateBaseline(
        FundingStateSnapshot main
) {
}
