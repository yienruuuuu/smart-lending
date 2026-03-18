package io.github.yienruuuuu.smartlending.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.yienruuuuu.smartlending.model.PollingStatusDto;
import io.github.yienruuuuu.smartlending.service.BitfinexFundingPollingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Funding Polling", description = "Bitfinex funding polling control endpoints")
@RestController
@RequestMapping("/api/v1/funding/polling")
public class FundingPollingController {

    private final BitfinexFundingPollingService fundingPollingService;

    public FundingPollingController(BitfinexFundingPollingService fundingPollingService) {
        this.fundingPollingService = fundingPollingService;
    }

    @Operation(summary = "啟動 funding 輪詢")
    @PostMapping("/start")
    public ResponseEntity<PollingStatusDto> start() {
        return ResponseEntity.ok(fundingPollingService.startPolling());
    }

    @Operation(summary = "停止 funding 輪詢")
    @PostMapping("/stop")
    public ResponseEntity<PollingStatusDto> stop() {
        return ResponseEntity.ok(fundingPollingService.stopPolling());
    }

    @Operation(summary = "手動單次查詢 funding book")
    @PostMapping("/run-once")
    public ResponseEntity<JsonNode> runOnce(
            @Parameter(description = "Funding symbol，例如 fUSD；不帶值時使用預設 fUSD")
            @RequestParam(defaultValue = "fUSD") String symbol
    ) {
        return ResponseEntity.ok(fundingPollingService.pollFundingBookOnce(symbol));
    }

    @Operation(summary = "查詢目前輪詢狀態")
    @GetMapping("/status")
    public ResponseEntity<PollingStatusDto> status() {
        return ResponseEntity.ok(fundingPollingService.getPollingStatus());
    }
}
