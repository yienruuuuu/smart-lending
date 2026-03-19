package io.github.yienruuuuu.smartlending.controller;

import io.github.yienruuuuu.smartlending.model.FundingAccountSummaryDto;
import io.github.yienruuuuu.smartlending.model.FundingPositionDto;
import io.github.yienruuuuu.smartlending.service.BitfinexFundingAccountRestClient;
import io.github.yienruuuuu.smartlending.service.FundingAccountSummaryService;
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
    private final FundingAccountSummaryService fundingAccountSummaryService;

    public FundingAccountController(
            BitfinexFundingAccountRestClient fundingAccountRestClient,
            FundingAccountSummaryService fundingAccountSummaryService
    ) {
        this.fundingAccountRestClient = fundingAccountRestClient;
        this.fundingAccountSummaryService = fundingAccountSummaryService;
    }

    @Operation(summary = "Get funding summary: wallet total, lent, idle, and open offers")
    @GetMapping("/summary")
    public ResponseEntity<FundingAccountSummaryDto> getSummary(
            @Parameter(description = "Funding symbol, for example fUSD") @RequestParam(required = false) String symbol
    ) {
        return ResponseEntity.ok(fundingAccountSummaryService.getSummary(symbol));
    }

    @Operation(summary = "Get raw funding offers")
    @GetMapping("/offers")
    public ResponseEntity<List<FundingPositionDto>> getOffers(
            @Parameter(description = "Funding symbol, for example fUSD") @RequestParam(required = false) String symbol
    ) {
        return ResponseEntity.ok(fundingAccountRestClient.getFundingOffers(symbol));
    }

    @Operation(summary = "Get raw funding credits")
    @GetMapping("/credits")
    public ResponseEntity<List<FundingPositionDto>> getCredits(
            @Parameter(description = "Funding symbol, for example fUSD") @RequestParam(required = false) String symbol
    ) {
        return ResponseEntity.ok(fundingAccountRestClient.getFundingCredits(symbol));
    }

    @Operation(summary = "Get raw funding loans")
    @GetMapping("/loans")
    public ResponseEntity<List<FundingPositionDto>> getLoans(
            @Parameter(description = "Funding symbol, for example fUSD") @RequestParam(required = false) String symbol
    ) {
        return ResponseEntity.ok(fundingAccountRestClient.getFundingLoans(symbol));
    }
}
