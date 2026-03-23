package io.github.yienruuuuu.smartlending.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yienruuuuu.smartlending.config.BitfinexProperties;
import io.github.yienruuuuu.smartlending.model.CancelFundingOfferRequest;
import io.github.yienruuuuu.smartlending.model.CreateFundingOfferRequest;
import io.github.yienruuuuu.smartlending.model.FundingAccountSummaryDto;
import io.github.yienruuuuu.smartlending.model.FundingLendbookRateDistributionDto;
import io.github.yienruuuuu.smartlending.model.FundingPositionDto;
import io.github.yienruuuuu.smartlending.model.FundingRateBucketDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 每 10 分鐘執行一次融資掛單策略。
 *
 * <p>流程如下：
 * <ol>
 *   <li>查詢指定條件的 funding rate-distribution</li>
 *   <li>找到第一筆 cumulativeSharePercent 大於門檻值的利率 bucket</li>
 *   <li>將該筆利率減 0.01，作為本次目標年化利率</li>
 *   <li>若利率小於等於 10，則結束本次操作</li>
 *   <li>若已有相同利率掛單，優先處理 funding wallet 的閒置資金</li>
 *   <li>若閒置資金達 150 USD，則直接拆單掛出</li>
 *   <li>若閒置資金不足 150 USD，則取消一筆既有掛單，等待資金回補後再掛出</li>
 *   <li>若沒有相同利率掛單，則沿用整批取消重掛流程，將既有掛單換到新利率</li>
 * </ol>
 */
@Service
public class FundingRateThresholdSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(FundingRateThresholdSchedulerService.class);

    private static final String TARGET_SYMBOL = "fUSD";
    private static final String TARGET_CURRENCY = "USD";
    private static final int TARGET_MIN_PERIOD = 60;
    private static final int TARGET_MAX_PERIOD = 120;
    private static final int TARGET_LIMIT_ASKS = 10000;
    private static final int TARGET_RATE_SCALE = 2;
    private static final BigDecimal TARGET_CUMULATIVE_SHARE_PERCENT = new BigDecimal("5.0");
    private static final BigDecimal MIN_ANNUAL_RATE_TO_ACT = new BigDecimal("10");
    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");
    private static final BigDecimal PERCENT_DIVISOR = new BigDecimal("100");
    private static final BigDecimal RATE_OFFSET = new BigDecimal("0.01");
    private static final BigDecimal BASE_OFFER_CHUNK_AMOUNT = new BigDecimal("150");
    private static final int TARGET_OFFER_PERIOD = 120;
    private static final String TARGET_OFFER_TYPE = "LIMIT";
    private static final int TARGET_OFFER_FLAGS = 0;

    private final BitfinexProperties properties;
    private final BitfinexFundingMarketRestClient fundingMarketRestClient;
    private final FundingAccountSummaryService fundingAccountSummaryService;
    private final BitfinexFundingAccountRestClient fundingAccountRestClient;
    private final ObjectMapper objectMapper;

    public FundingRateThresholdSchedulerService(
            BitfinexProperties properties,
            BitfinexFundingMarketRestClient fundingMarketRestClient,
            FundingAccountSummaryService fundingAccountSummaryService,
            BitfinexFundingAccountRestClient fundingAccountRestClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.fundingMarketRestClient = fundingMarketRestClient;
        this.fundingAccountSummaryService = fundingAccountSummaryService;
        this.fundingAccountRestClient = fundingAccountRestClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 以固定條件每 10 分鐘執行一次掛單策略。
     */
    @Scheduled(initialDelay = 10000L, fixedDelay = 600000L)
    public void pollTargetFundingRate() {
        FundingLendbookRateDistributionDto distribution = fundingMarketRestClient.getFundingLendbookRateDistribution(
                TARGET_CURRENCY,
                TARGET_MIN_PERIOD,
                TARGET_MAX_PERIOD,
                TARGET_LIMIT_ASKS,
                TARGET_RATE_SCALE
        );

        logQueryResult(distribution);

        BigDecimal annualRate = findTargetAnnualRate(distribution.buckets());
        if (annualRate == null) {
            log.info("本次策略結束：沒有找到 cumulativeSharePercent 大於 {} 的目標利率", TARGET_CUMULATIVE_SHARE_PERCENT);
            return;
        }

        if (annualRate.compareTo(MIN_ANNUAL_RATE_TO_ACT) <= 0) {
            log.info("本次策略結束：取得利率 {} 小於等於 {}，不進行掛單操作", annualRate, MIN_ANNUAL_RATE_TO_ACT);
            return;
        }

        BigDecimal targetDailyRate = toDailyRate(annualRate);
        FundingAccountSummaryDto summary = fundingAccountSummaryService.getSummary(TARGET_SYMBOL);
        BigDecimal reofferAmount = calculateInitialReofferAmount(summary);
        if (reofferAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("本次策略結束：目前資金都在放貸中，沒有可重新掛單金額。symbol={}, lentAmount={}, idleAmount={}, offerAmount={}",
                    TARGET_SYMBOL,
                    summary.lentAmount(),
                    summary.idleAmount(),
                    summary.offerAmount());
            return;
        }

        if (hasOfferWithSameRate(summary.offers(), targetDailyRate)) {
            handleSameRateOffers(summary, annualRate, targetDailyRate);
            return;
        }

        if (!summary.offers().isEmpty()) {
            cancelOpenOffers(summary.offers());
            waitForOfferSettlement();

            FundingAccountSummaryDto refreshedSummary = fundingAccountSummaryService.getSummary(TARGET_SYMBOL);
            if (!refreshedSummary.offers().isEmpty()) {
                log.info("本次策略結束：取消掛單後仍有未成交 funding offers，暫不重新掛單。symbol={}, offerCount={}",
                        TARGET_SYMBOL,
                        refreshedSummary.offerCount());
                return;
            }

            BigDecimal refreshedIdleAmount = nullSafe(refreshedSummary.idleAmount());
            if (!isOrderableAmount(refreshedIdleAmount)) {
                log.info("本次策略結束：取消掛單後可直接下單的閒置資金不足。symbol={}, idleAmount={}, minimumAmount={}",
                        TARGET_SYMBOL,
                        refreshedIdleAmount.stripTrailingZeros().toPlainString(),
                        BASE_OFFER_CHUNK_AMOUNT.stripTrailingZeros().toPlainString());
                return;
            }

            createOffers(refreshedIdleAmount, annualRate, targetDailyRate);
            return;
        }

        BigDecimal idleAmount = nullSafe(summary.idleAmount());
        if (!isOrderableAmount(idleAmount)) {
            log.info("本次策略結束：目前閒置資金不足以直接下單，且沒有既有掛單可配合。symbol={}, idleAmount={}, minimumAmount={}",
                    TARGET_SYMBOL,
                    idleAmount.stripTrailingZeros().toPlainString(),
                    BASE_OFFER_CHUNK_AMOUNT.stripTrailingZeros().toPlainString());
            return;
        }

        createOffers(idleAmount, annualRate, targetDailyRate);
    }

    /**
     * 已有相同利率掛單時，優先利用閒置資金補掛；若閒置資金不足，則取消一筆掛單後再重新掛出。
     */
    private void handleSameRateOffers(FundingAccountSummaryDto summary, BigDecimal annualRate, BigDecimal targetDailyRate) {
        BigDecimal idleAmount = nullSafe(summary.idleAmount());
        if (isOrderableAmount(idleAmount)) {
            log.info("目前已有相同利率掛單，但 funding wallet 尚有可直接下單的閒置資金，將直接補掛。symbol={}, idleAmount={}, dailyRate={}, annualRate={}",
                    TARGET_SYMBOL,
                    idleAmount.stripTrailingZeros().toPlainString(),
                    targetDailyRate.stripTrailingZeros().toPlainString(),
                    annualRate);
            createOffers(idleAmount, annualRate, targetDailyRate);
            return;
        }

        FundingPositionDto offerToRebundle = selectOfferToRebundle(summary.offers());
        if (offerToRebundle == null) {
            log.info("本次策略結束：目前已存在相同利率掛單，但閒置資金不足以直接下單，且沒有可調整的掛單。symbol={}, idleAmount={}, minimumAmount={}",
                    TARGET_SYMBOL,
                    idleAmount.stripTrailingZeros().toPlainString(),
                    BASE_OFFER_CHUNK_AMOUNT.stripTrailingZeros().toPlainString());
            return;
        }

        long offerId = offerToRebundle.decoded().path("id").asLong();
        BigDecimal offerAmount = extractOfferAmount(offerToRebundle);
        log.info("目前已有相同利率掛單，但閒置資金不足以直接下單，將取消一筆掛單後重新整併。symbol={}, idleAmount={}, selectedOfferId={}, selectedOfferAmount={}, dailyRate={}, annualRate={}",
                TARGET_SYMBOL,
                idleAmount.stripTrailingZeros().toPlainString(),
                offerId,
                offerAmount.stripTrailingZeros().toPlainString(),
                targetDailyRate.stripTrailingZeros().toPlainString(),
                annualRate);

        cancelOpenOffers(List.of(offerToRebundle));
        waitForOfferSettlement();

        FundingAccountSummaryDto refreshedSummary = fundingAccountSummaryService.getSummary(TARGET_SYMBOL);
        BigDecimal refreshedIdleAmount = nullSafe(refreshedSummary.idleAmount());
        if (!isOrderableAmount(refreshedIdleAmount)) {
            log.info("本次策略結束：取消單筆掛單後，閒置資金仍不足以直接下單。symbol={}, idleAmount={}, minimumAmount={}",
                    TARGET_SYMBOL,
                    refreshedIdleAmount.stripTrailingZeros().toPlainString(),
                    BASE_OFFER_CHUNK_AMOUNT.stripTrailingZeros().toPlainString());
            return;
        }

        createOffers(refreshedIdleAmount, annualRate, targetDailyRate);
    }

    /**
     * 以 debug 等級紀錄完整查詢結果，避免一般執行時輸出過長內容。
     */
    private void logQueryResult(FundingLendbookRateDistributionDto result) {
        try {
            log.debug("排程 funding rate-distribution 查詢結果：{}", objectMapper.writeValueAsString(result));
        } catch (Exception exception) {
            throw new IllegalStateException("無法序列化排程 funding rate-distribution 查詢結果", exception);
        }
    }

    /**
     * 找出第一筆 cumulativeSharePercent 大於門檻值的 bucket，
     * 並以該筆利率減 0.01 作為本次目標年化利率。
     */
    private BigDecimal findTargetAnnualRate(List<FundingRateBucketDto> buckets) {
        for (FundingRateBucketDto current : buckets) {
            if (current.cumulativeSharePercent().compareTo(TARGET_CUMULATIVE_SHARE_PERCENT) <= 0) {
                continue;
            }

            BigDecimal targetAnnualRate = current.roundedRate().subtract(RATE_OFFSET).setScale(2, RoundingMode.HALF_UP);
            log.info("找到策略目標利率：thresholdPercent={}, nextRate={}, targetAnnualRate={}, nextCumulativeSharePercent={}",
                    TARGET_CUMULATIVE_SHARE_PERCENT,
                    current.roundedRate(),
                    targetAnnualRate,
                    current.cumulativeSharePercent());
            return targetAnnualRate;
        }

        return null;
    }

    /**
     * 第一次計算可重掛金額時，會把目前 open offers 一併納入，因為取消後這筆資金理論上可回收再利用。
     */
    private BigDecimal calculateInitialReofferAmount(FundingAccountSummaryDto summary) {
        return nullSafe(summary.idleAmount()).add(nullSafe(summary.offerAmount()));
    }

    /**
     * 檢查目前 open offers 是否已存在與目標日利率相同的掛單。
     */
    private boolean hasOfferWithSameRate(List<FundingPositionDto> offers, BigDecimal targetDailyRate) {
        return offers.stream()
                .map(item -> item.decoded().path("rate").asText(null))
                .filter(rate -> rate != null && !rate.isBlank())
                .map(BigDecimal::new)
                .anyMatch(rate -> rate.compareTo(targetDailyRate) == 0);
    }

    /**
     * 從既有掛單中挑選一筆適合拿來整併的訂單，預設選擇金額最小的一筆以減少調整範圍。
     */
    private FundingPositionDto selectOfferToRebundle(List<FundingPositionDto> offers) {
        return offers.stream()
                .filter(item -> item.decoded().path("id").asLong() > 0)
                .min(Comparator.comparing(this::extractOfferAmount))
                .orElse(null);
    }

    /**
     * 讀取掛單金額並轉成正數，供金額比較與排序使用。
     */
    private BigDecimal extractOfferAmount(FundingPositionDto offer) {
        String amountText = offer.decoded().path("amount").asText("0");
        return new BigDecimal(amountText).abs();
    }

    /**
     * 判斷目前閒置資金是否已達可直接掛單門檻。
     */
    private boolean isOrderableAmount(BigDecimal amount) {
        return nullSafe(amount).compareTo(BASE_OFFER_CHUNK_AMOUNT) >= 0;
    }

    /**
     * 取消目前指定的 open funding offers。
     */
    private void cancelOpenOffers(List<FundingPositionDto> offers) {
        for (FundingPositionDto offer : offers) {
            long offerId = offer.decoded().path("id").asLong();
            if (offerId <= 0) {
                log.info("略過取消 funding 掛單：無法從 decoded 取得有效 offerId。decoded={}", offer.decoded());
                continue;
            }

            fundingAccountRestClient.cancelFundingOffer(new CancelFundingOfferRequest(offerId));
            log.info("已取消既有 funding 掛單。offerId={}", offerId);
        }
    }

    /**
     * 取消掛單後等待交易所同步狀態與可用餘額回補，避免過早重掛造成餘額不足。
     */
    private void waitForOfferSettlement() {
        long delayMillis = properties.getOfferResubmitDelayMillis();
        if (delayMillis <= 0) {
            return;
        }

        try {
            log.info("取消掛單後等待交易所同步。delayMillis={}", delayMillis);
            Thread.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待取消掛單同步時被中斷", exception);
        }
    }

    /**
     * 依年化利率換算 Bitfinex 下單所需的日利率小數，並依 150 USD 基本單位拆成多筆掛單。
     */
    private void createOffers(BigDecimal amount, BigDecimal annualRate, BigDecimal dailyRate) {
        if (!isOrderableAmount(amount)) {
            log.info("本次策略結束：可用掛單金額不足以直接下單。amount={}, minimumAmount={}",
                    nullSafe(amount).stripTrailingZeros().toPlainString(),
                    BASE_OFFER_CHUNK_AMOUNT.stripTrailingZeros().toPlainString());
            return;
        }

        List<BigDecimal> chunkAmounts = splitOfferAmounts(amount);
        log.info("準備建立 funding 掛單。symbol={}, totalAmount={}, chunkCount={}, chunks={}, annualRate={}, dailyRate={}, period={}, type={}, flags={}",
                TARGET_SYMBOL,
                amount.stripTrailingZeros().toPlainString(),
                chunkAmounts.size(),
                formatAmounts(chunkAmounts),
                annualRate,
                dailyRate.stripTrailingZeros().toPlainString(),
                TARGET_OFFER_PERIOD,
                TARGET_OFFER_TYPE,
                TARGET_OFFER_FLAGS);

        for (BigDecimal chunkAmount : chunkAmounts) {
            CreateFundingOfferRequest request = new CreateFundingOfferRequest(
                    TARGET_SYMBOL,
                    chunkAmount.stripTrailingZeros().toPlainString(),
                    dailyRate.stripTrailingZeros().toPlainString(),
                    TARGET_OFFER_PERIOD,
                    TARGET_OFFER_TYPE,
                    TARGET_OFFER_FLAGS
            );

            fundingAccountRestClient.createFundingOffer(request);
            log.info("已建立新的 funding 掛單。symbol={}, amount={}, annualRate={}, dailyRate={}, period={}, type={}, flags={}",
                    request.symbol(),
                    request.amount(),
                    annualRate,
                    request.rate(),
                    request.period(),
                    request.type(),
                    request.flags());
        }
    }

    /**
     * 依 150 USD 為基本單位拆單；若有餘數，會併入最後一筆，避免產生過小尾單。
     */
    private List<BigDecimal> splitOfferAmounts(BigDecimal totalAmount) {
        BigDecimal normalizedAmount = totalAmount.stripTrailingZeros();
        if (normalizedAmount.compareTo(BASE_OFFER_CHUNK_AMOUNT) <= 0) {
            return List.of(normalizedAmount);
        }

        BigDecimal[] division = normalizedAmount.divideAndRemainder(BASE_OFFER_CHUNK_AMOUNT);
        int baseChunkCount = division[0].intValueExact();
        BigDecimal remainder = division[1];
        List<BigDecimal> amounts = new ArrayList<>(baseChunkCount);

        for (int index = 0; index < baseChunkCount; index++) {
            BigDecimal chunkAmount = BASE_OFFER_CHUNK_AMOUNT;
            if (index == baseChunkCount - 1 && remainder.compareTo(BigDecimal.ZERO) > 0) {
                chunkAmount = chunkAmount.add(remainder);
            }
            amounts.add(chunkAmount.stripTrailingZeros());
        }

        return amounts;
    }

    /**
     * 將拆單結果轉成適合 log 顯示的字串。
     */
    private String formatAmounts(List<BigDecimal> amounts) {
        return amounts.stream()
                .map(value -> value.stripTrailingZeros().toPlainString())
                .toList()
                .toString();
    }

    /**
     * 將年化百分比利率轉成 Bitfinex 下單使用的日利率小數。
     */
    private BigDecimal toDailyRate(BigDecimal annualRate) {
        return annualRate
                .divide(DAYS_PER_YEAR, 8, RoundingMode.HALF_UP)
                .divide(PERCENT_DIVISOR, 8, RoundingMode.HALF_UP);
    }

    /**
     * 將可能為 null 的金額轉成 0，避免後續金額計算發生空值問題。
     */
    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
