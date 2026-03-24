package io.github.yienruuuuu.smartlending.service;

import io.github.yienruuuuu.smartlending.config.BitfinexProperties;
import io.github.yienruuuuu.smartlending.model.CreateFundingOfferRequest;
import io.github.yienruuuuu.smartlending.model.WalletBalanceDto;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 每 10 分鐘檢查 sub account 的 funding 閒置資金，符合門檻時以固定條件掛單。
 */
@Service
public class SubFundingOfferSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SubFundingOfferSchedulerService.class);

    private static final String TARGET_SYMBOL = "fUSD";
    private static final String TARGET_CURRENCY = "USD";
    private static final BigDecimal MIN_IDLE_AMOUNT = new BigDecimal("150");
    private static final String FIXED_DAILY_RATE = "0.000435";
    private static final int FIXED_PERIOD = 120;
    private static final String FIXED_TYPE = "LIMIT";
    private static final int FIXED_FLAGS = 0;

    private final BitfinexProperties properties;
    private final SubBitfinexAccountRestClient subBitfinexAccountRestClient;
    private final SubBitfinexFundingAccountRestClient subBitfinexFundingAccountRestClient;

    public SubFundingOfferSchedulerService(
            BitfinexProperties properties,
            SubBitfinexAccountRestClient subBitfinexAccountRestClient,
            SubBitfinexFundingAccountRestClient subBitfinexFundingAccountRestClient
    ) {
        this.properties = properties;
        this.subBitfinexAccountRestClient = subBitfinexAccountRestClient;
        this.subBitfinexFundingAccountRestClient = subBitfinexFundingAccountRestClient;
    }

    @Scheduled(initialDelay = 10000L, fixedDelay = 600000L)
    public void submitFixedOfferWhenIdleAmountIsEnough() {
        if (!properties.hasSubAccountCredentials()) {
            log.debug("略過 sub account funding 掛單：未設定 sub account API 憑證");
            return;
        }

        WalletBalanceDto wallet = findFundingWallet(subBitfinexAccountRestClient.getWallets());
        BigDecimal idleAmount = nullSafe(wallet.availableBalance());
        if (idleAmount.compareTo(MIN_IDLE_AMOUNT) <= 0) {
            log.info("sub account 本次不掛單：閒置資金未大於門檻。currency={}, idleAmount={}, threshold={}",
                    TARGET_CURRENCY,
                    idleAmount.stripTrailingZeros().toPlainString(),
                    MIN_IDLE_AMOUNT.stripTrailingZeros().toPlainString());
            return;
        }

        CreateFundingOfferRequest request = new CreateFundingOfferRequest(
                TARGET_SYMBOL,
                idleAmount.stripTrailingZeros().toPlainString(),
                FIXED_DAILY_RATE,
                FIXED_PERIOD,
                FIXED_TYPE,
                FIXED_FLAGS
        );

        subBitfinexFundingAccountRestClient.createFundingOffer(request);
        log.info("sub account 已依固定策略建立 funding 掛單。symbol={}, amount={}, rate={}, period={}",
                request.symbol(),
                request.amount(),
                request.rate(),
                request.period());
    }

    private WalletBalanceDto findFundingWallet(List<WalletBalanceDto> wallets) {
        return wallets.stream()
                .filter(item -> "funding".equalsIgnoreCase(item.walletType()))
                .filter(item -> TARGET_CURRENCY.equalsIgnoreCase(item.currency()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Sub account funding wallet not found for currency: " + TARGET_CURRENCY));
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
