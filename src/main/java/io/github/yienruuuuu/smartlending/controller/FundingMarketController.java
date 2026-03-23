package io.github.yienruuuuu.smartlending.controller;

import io.github.yienruuuuu.smartlending.model.FundingLendbookRateDistributionDto;
import io.github.yienruuuuu.smartlending.model.FundingLendbookSummaryDto;
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

/**
 * 提供 Bitfinex 公開 funding 市場查詢 API。
 */
@Tag(name = "Funding Market", description = "Bitfinex 公開 funding 市場查詢 API")
@RestController
@RequestMapping("/api/v1/funding/market")
public class FundingMarketController {

    private final BitfinexFundingMarketRestClient fundingMarketRestClient;

    public FundingMarketController(BitfinexFundingMarketRestClient fundingMarketRestClient) {
        this.fundingMarketRestClient = fundingMarketRestClient;
    }

    /**
     * 查詢目前 funding ticker。
     */
    @Operation(summary = "查詢目前 Bitfinex funding ticker，包含 FRR")
    @GetMapping("/ticker")
    public ResponseEntity<FundingTickerDto> getTicker(
            @Parameter(description = "Funding symbol，例如 fUSD") @RequestParam(required = false) String symbol
    ) {
        return ResponseEntity.ok(fundingMarketRestClient.getFundingTicker(symbol));
    }

    /**
     * 查詢 lendbook ask 摘要。
     */
    @Operation(summary = "查詢目前 lendbook ask 摘要：總量、FRR 掛單與固定利率掛單")
    @GetMapping("/lendbook/summary")
    public ResponseEntity<FundingLendbookSummaryDto> getLendbookSummary(
            @Parameter(description = "Funding 幣別，例如 USD 或 fUSD")
            @RequestParam(required = false) String currency,
            @Parameter(description = "要納入統計的 Bitfinex v1 lendbook ask 筆數上限，預設 10000")
            @RequestParam(required = false) Integer limitAsks,
            @Parameter(description = "只保留 period 嚴格大於此值的 asks，例如 30 代表只保留 period > 30")
            @RequestParam(required = false) Integer minPeriodExclusive
    ) {
        return ResponseEntity.ok(fundingMarketRestClient.getFundingLendbookSummary(currency, limitAsks, minPeriodExclusive));
    }

    /**
     * 依利率分桶查詢 lendbook rate-distribution。
     */
    @Operation(summary = "依利率分桶統計 lendbook asks，並可依 period 區間過濾")
    @GetMapping("/lendbook/rate-distribution")
    public ResponseEntity<FundingLendbookRateDistributionDto> getLendbookRateDistribution(
            @Parameter(example = "USD", description = "Funding 幣別，例如 USD")
            @RequestParam(required = false) String currency,
            @Parameter(example = "30", description = "只保留 period 大於等於此值的 asks")
            @RequestParam(required = false) Integer minPeriod,
            @Parameter(example = "120", description = "只保留 period 小於等於此值的 asks")
            @RequestParam(required = false) Integer maxPeriod,
            @Parameter(example = "10000", description = "要納入統計的 Bitfinex v1 lendbook ask 筆數上限，預設 10000")
            @RequestParam(required = false) Integer limitAsks,
            @Parameter(example = "1", description = "利率分桶時保留的小數位數，例如 1 代表 14.6、14.7")
            @RequestParam(required = false) Integer rateScale
    ) {
        return ResponseEntity.ok(fundingMarketRestClient.getFundingLendbookRateDistribution(currency, minPeriod, maxPeriod, limitAsks, rateScale));
    }
}
