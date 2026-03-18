package io.github.yienruuuuu.smartlending.controller;

import io.github.yienruuuuu.smartlending.model.WalletBalanceDto;
import io.github.yienruuuuu.smartlending.service.BitfinexAccountRestClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account", description = "Bitfinex account endpoints")
@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

    private final BitfinexAccountRestClient bitfinexAccountRestClient;

    public AccountController(BitfinexAccountRestClient bitfinexAccountRestClient) {
        this.bitfinexAccountRestClient = bitfinexAccountRestClient;
    }

    @Operation(summary = "查詢 Bitfinex 帳戶 wallet 資料")
    @GetMapping("/wallets")
    public ResponseEntity<List<WalletBalanceDto>> getWallets() {
        return ResponseEntity.ok(bitfinexAccountRestClient.getWallets());
    }
}
