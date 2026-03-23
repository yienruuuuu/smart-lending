package io.github.yienruuuuu.smartlending.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CancelFundingOfferRequest(
        @Schema(description = "要取消的 funding offer ID，可由建立訂單回應或 open offers 查詢結果取得", example = "123456789")
        @NotNull @Min(1) Long offerId
) {
}
