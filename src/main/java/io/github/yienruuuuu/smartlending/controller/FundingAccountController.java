package io.github.yienruuuuu.smartlending.controller;

import io.github.yienruuuuu.smartlending.model.CancelFundingOfferRequest;
import io.github.yienruuuuu.smartlending.model.CreateFundingOfferRequest;
import io.github.yienruuuuu.smartlending.model.FundingAccountSummaryDto;
import io.github.yienruuuuu.smartlending.model.FundingOfferActionDto;
import io.github.yienruuuuu.smartlending.model.FundingPositionDto;
import io.github.yienruuuuu.smartlending.service.BitfinexFundingAccountRestClient;
import io.github.yienruuuuu.smartlending.service.FundingAccountSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Funding Account", description = "Bitfinex 融資帳戶查詢與操作 API")
@RestController
@RequestMapping("/api/v1/account/funding")
public class FundingAccountController {

    private final BitfinexFundingAccountRestClient fundingAccountRestClient;
    private final FundingAccountSummaryService fundingAccountSummaryService;

    public FundingAccountController(
            BitfinexFundingAccountRestClient fundingAccountRestClient,
            FundingAccountSummaryService fundingAccountSummaryService
    ) {
        this.fundingAccountRestClient = fundingAccountRestClient;
        this.fundingAccountSummaryService = fundingAccountSummaryService;
    }

    @Operation(summary = "查詢 funding 摘要：錢包總額、已借出、閒置與掛單")
    @GetMapping("/summary")
    public ResponseEntity<FundingAccountSummaryDto> getSummary(
            @Parameter(description = "Funding symbol，例如 fUSD") @RequestParam(required = false) String symbol
    ) {
        return ResponseEntity.ok(fundingAccountSummaryService.getSummary(symbol));
    }

    @Operation(summary = "查詢目前未成交 funding offers")
    @GetMapping("/offers")
    public ResponseEntity<List<FundingPositionDto>> getOffers(
            @Parameter(description = "Funding symbol，例如 fUSD；不帶值時查全部 funding offers") @RequestParam(required = false) String symbol
    ) {
        return ResponseEntity.ok(fundingAccountRestClient.getFundingOffers(symbol));
    }

    @Operation(
            summary = "建立 funding 掛單",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            examples = @ExampleObject(value = """
                                    {
                                      \"symbol\": \"fUSD\",
                                      \"amount\": \"1000\",
                                      \"rate\": \"0.0002\",
                                      \"period\": 30,
                                      \"type\": \"LIMIT\",
                                      \"flags\": 0
                                    }
                                    """)
                    )
            )
    )
    @PostMapping("/offers")
    public ResponseEntity<FundingOfferActionDto> createOffer(@Valid @org.springframework.web.bind.annotation.RequestBody CreateFundingOfferRequest request) {
        return ResponseEntity.ok(fundingAccountRestClient.createFundingOffer(request));
    }

    @Operation(
            summary = "取消 funding 掛單",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            examples = @ExampleObject(value = """
                                    {
                                      \"offerId\": 123456789
                                    }
                                    """)
                    )
            )
    )
    @PostMapping("/offers/cancel")
    public ResponseEntity<FundingOfferActionDto> cancelOffer(@Valid @org.springframework.web.bind.annotation.RequestBody CancelFundingOfferRequest request) {
        return ResponseEntity.ok(fundingAccountRestClient.cancelFundingOffer(request));
    }

    @Operation(summary = "查詢 funding credits")
    @GetMapping("/credits")
    public ResponseEntity<List<FundingPositionDto>> getCredits(
            @Parameter(description = "Funding symbol，例如 fUSD；不帶值時查全部 funding credits") @RequestParam(required = false) String symbol
    ) {
        return ResponseEntity.ok(fundingAccountRestClient.getFundingCredits(symbol));
    }

    @Operation(summary = "查詢 funding loans")
    @GetMapping("/loans")
    public ResponseEntity<List<FundingPositionDto>> getLoans(
            @Parameter(description = "Funding symbol，例如 fUSD；不帶值時查全部 funding loans") @RequestParam(required = false) String symbol
    ) {
        return ResponseEntity.ok(fundingAccountRestClient.getFundingLoans(symbol));
    }
}
