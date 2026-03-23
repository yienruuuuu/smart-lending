package io.github.yienruuuuu.smartlending.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateFundingOfferRequest(
        @Schema(description = "Funding symbol，例如 fUSD", example = "fUSD")
        @NotBlank String symbol,

        @Schema(description = "掛單金額，使用字串避免精度問題", example = "1000")
        @NotBlank String amount,

        @Schema(description = "日利率，使用字串避免精度問題，例如 0.0002 代表 0.02%", example = "0.0002")
        @NotBlank String rate,

        @Schema(description = "掛單天期，Bitfinex funding 常見範圍為 2 到 120 天", example = "30")
        @NotNull @Min(2) @Max(120) Integer period,

        @Schema(description = "訂單型別，未帶值時預設為 LIMIT", example = "LIMIT")
        String type,

        @Schema(description = "Bitfinex flags，未帶值時預設為 0", example = "0")
        @Min(0) Integer flags
) {
}
