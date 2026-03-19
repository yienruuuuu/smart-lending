package io.github.yienruuuuu.smartlending.controller;

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
}
