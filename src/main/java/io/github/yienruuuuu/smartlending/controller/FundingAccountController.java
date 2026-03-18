package io.github.yienruuuuu.smartlending.controller;

import io.github.yienruuuuu.smartlending.model.FundingPositionDto;
import io.github.yienruuuuu.smartlending.service.BitfinexFundingAccountRestClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Funding Account", description = "Bitfinex funding account endpoints")
@RestController
@RequestMapping("/api/v1/account/funding")
public class FundingAccountController {

    private final BitfinexFundingAccountRestClient fundingAccountRestClient;

    public FundingAccountController(BitfinexFundingAccountRestClient fundingAccountRestClient) {
        this.fundingAccountRestClient = fundingAccountRestClient;
    }

    @Operation(summary = "查詢目前掛單中的 funding offers")
    @GetMapping("/offers")
    public ResponseEntity<List<FundingPositionDto>> getOffers(
            @Parameter(description = "Funding symbol，例如 fUSD") @RequestParam(required = false) String symbol
    ) {
        return ResponseEntity.ok(fundingAccountRestClient.getFundingOffers(symbol));
    }

    @Operation(summary = "查詢放貸中的 funding credits")
    @GetMapping("/credits")
    public ResponseEntity<List<FundingPositionDto>> getCredits(
            @Parameter(description = "Funding symbol，例如 fUSD") @RequestParam(required = false) String symbol
    ) {
        return ResponseEntity.ok(fundingAccountRestClient.getFundingCredits(symbol));
    }

    @Operation(summary = "查詢未使用中的 funding loans")
    @GetMapping("/loans")
    public ResponseEntity<List<FundingPositionDto>> getLoans(
            @Parameter(description = "Funding symbol，例如 fUSD") @RequestParam(required = false) String symbol
    ) {
        return ResponseEntity.ok(fundingAccountRestClient.getFundingLoans(symbol));
    }
}
