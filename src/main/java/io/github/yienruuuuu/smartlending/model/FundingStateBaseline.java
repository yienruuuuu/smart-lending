package io.github.yienruuuuu.smartlending.model;

public record FundingStateBaseline(
        FundingStateSnapshot main,
        FundingStateSnapshot sub
) {
}
