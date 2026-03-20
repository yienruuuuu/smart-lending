package io.github.yienruuuuu.smartlending.controller;

import io.github.yienruuuuu.smartlending.model.FundingLendbookRateDistributionDto;
import io.github.yienruuuuu.smartlending.model.FundingLendbookSummaryDto;
import io.github.yienruuuuu.smartlending.model.FundingRateBucketDto;
import io.github.yienruuuuu.smartlending.model.FundingTickerDto;
import io.github.yienruuuuu.smartlending.service.BitfinexFundingMarketRestClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Funding Market", description = "Bitfinex public funding market endpoints")
@RestController
@RequestMapping("/api/v1/funding/market")
public class FundingMarketController {

    private final BitfinexFundingMarketRestClient fundingMarketRestClient;

    public FundingMarketController(BitfinexFundingMarketRestClient fundingMarketRestClient) {
        this.fundingMarketRestClient = fundingMarketRestClient;
    }

    @Operation(summary = "Get current Bitfinex funding ticker, including FRR")
    @GetMapping("/ticker")
    public ResponseEntity<FundingTickerDto> getTicker(
            @Parameter(description = "Funding symbol, for example fUSD") @RequestParam(required = false) String symbol
    ) {
        return ResponseEntity.ok(fundingMarketRestClient.getFundingTicker(symbol));
    }

    @Operation(summary = "Get current lendbook ask summary: total asks, FRR asks, fixed-rate asks")
    @GetMapping("/lendbook/summary")
    public ResponseEntity<FundingLendbookSummaryDto> getLendbookSummary(
            @Parameter(description = "Funding currency, for example USD or fUSD")
            @RequestParam(required = false) String currency,
            @Parameter(description = "Number of ask rows to include from Bitfinex v1 lendbook, default 10000")
            @RequestParam(required = false) Integer limitAsks,
            @Parameter(description = "Only include asks with period strictly greater than this value, for example 30 means period > 30")
            @RequestParam(required = false) Integer minPeriodExclusive
    ) {
        return ResponseEntity.ok(fundingMarketRestClient.getFundingLendbookSummary(currency, limitAsks, minPeriodExclusive));
    }

    @Operation(summary = "Group lendbook asks by rounded rate after filtering by period strictly greater than the given value")
    @GetMapping("/lendbook/rate-distribution")
    public ResponseEntity<FundingLendbookRateDistributionDto> getLendbookRateDistribution(
            @Parameter(description = "Funding currency, for example USD or fUSD")
            @RequestParam(required = false) String currency,
            @Parameter(description = "Only include asks with period strictly greater than this value, for example 30 means period > 30")
            @RequestParam(required = false) Integer period,
            @Parameter(description = "Number of ask rows to include from Bitfinex v1 lendbook, default 10000")
            @RequestParam(required = false) Integer limitAsks,
            @Parameter(description = "Decimal places used to round rate buckets, for example 1 means 14.6, 14.7")
            @RequestParam(required = false) Integer rateScale
    ) {
        return ResponseEntity.ok(fundingMarketRestClient.getFundingLendbookRateDistribution(currency, period, limitAsks, rateScale));
    }
}
