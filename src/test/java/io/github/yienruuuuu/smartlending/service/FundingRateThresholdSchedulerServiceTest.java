package io.github.yienruuuuu.smartlending.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.yienruuuuu.smartlending.config.BitfinexProperties;
import io.github.yienruuuuu.smartlending.model.FundingAccountDiagnosticsDto;
import io.github.yienruuuuu.smartlending.model.FundingAccountSummaryDto;
import io.github.yienruuuuu.smartlending.model.FundingLendbookRateDistributionDto;
import io.github.yienruuuuu.smartlending.model.FundingPositionDto;
import io.github.yienruuuuu.smartlending.model.FundingRateBucketDto;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FundingRateThresholdSchedulerServiceTest {

    private final BitfinexProperties properties = properties();
    private final BitfinexFundingMarketRestClient fundingMarketRestClient = mock(BitfinexFundingMarketRestClient.class);
    private final FundingAccountSummaryService fundingAccountSummaryService = mock(FundingAccountSummaryService.class);
    private final BitfinexFundingAccountRestClient fundingAccountRestClient = mock(BitfinexFundingAccountRestClient.class);
    private final FundingRateThresholdSchedulerService service = new FundingRateThresholdSchedulerService(
            properties,
            fundingMarketRestClient,
            fundingAccountSummaryService,
            fundingAccountRestClient,
            new ObjectMapper()
    );

    @Test
    void shouldStopWhenAnnualRateIsLessThanOrEqualToTen() {
        when(fundingMarketRestClient.getFundingLendbookRateDistribution(any(), any(), any(), any(), any()))
                .thenReturn(distribution(
                        bucket("10.00", "4.90"),
                        bucket("10.01", "5.10")
                ));

        service.pollTargetFundingRate();

        verify(fundingAccountSummaryService, never()).getSummary("fUSD");
        verify(fundingAccountRestClient, never()).createFundingOffer(argThat(item -> true));
    }

    @Test
    void shouldStopWhenAllFundsAreAlreadyLent() {
        when(fundingMarketRestClient.getFundingLendbookRateDistribution(any(), any(), any(), any(), any()))
                .thenReturn(distribution(
                        bucket("10.51", "4.90"),
                        bucket("10.52", "5.10")
                ));
        when(fundingAccountSummaryService.getSummary("fUSD")).thenReturn(summary(BigDecimal.ZERO, BigDecimal.ZERO, List.of()));

        service.pollTargetFundingRate();

        verify(fundingAccountRestClient, never()).cancelFundingOffer(argThat(item -> true));
        verify(fundingAccountRestClient, never()).createFundingOffer(argThat(item -> true));
    }

    @Test
    void shouldCreateOffersWhenExistingOfferAlreadyHasSameRateAndIdleAmountIsEnough() {
        when(fundingMarketRestClient.getFundingLendbookRateDistribution(any(), any(), any(), any(), any()))
                .thenReturn(distribution(
                        bucket("11.31", "4.90"),
                        bucket("11.32", "5.10")
                ));
        when(fundingAccountSummaryService.getSummary("fUSD")).thenReturn(summary(
                new BigDecimal("300"),
                new BigDecimal("700"),
                List.of(openOffer(12345L, "0.00030986", "150"))
        ));

        service.pollTargetFundingRate();

        verify(fundingAccountRestClient, never()).cancelFundingOffer(argThat(item -> true));
        verify(fundingAccountRestClient, times(2)).createFundingOffer(argThat(request ->
                "fUSD".equals(request.symbol())
                        && "150".equals(request.amount())
                        && "0.00030986".equals(request.rate())
                        && request.period().equals(120)
                        && "LIMIT".equals(request.type())
                        && request.flags().equals(0)
        ));
    }

    @Test
    void shouldCancelOneOfferAndCreateMergedOfferWhenSameRateExistsButIdleAmountIsInsufficient() {
        when(fundingMarketRestClient.getFundingLendbookRateDistribution(any(), any(), any(), any(), any()))
                .thenReturn(distribution(
                        bucket("11.31", "4.90"),
                        bucket("11.32", "5.10")
                ));
        when(fundingAccountSummaryService.getSummary("fUSD"))
                .thenReturn(summary(new BigDecimal("20"), new BigDecimal("330"), List.of(
                        openOffer(12345L, "0.00030986", "150"),
                        openOffer(67890L, "0.00030986", "180")
                )))
                .thenReturn(summary(new BigDecimal("170"), new BigDecimal("180"), List.of(
                        openOffer(67890L, "0.00030986", "180")
                )));

        service.pollTargetFundingRate();

        verify(fundingAccountRestClient).cancelFundingOffer(argThat(request -> request.offerId().equals(12345L)));
        verify(fundingAccountRestClient, times(1)).createFundingOffer(argThat(request ->
                "fUSD".equals(request.symbol())
                        && "170".equals(request.amount())
                        && "0.00030986".equals(request.rate())
                        && request.period().equals(120)
                        && "LIMIT".equals(request.type())
                        && request.flags().equals(0)
        ));
        verify(fundingAccountRestClient, times(1)).createFundingOffer(any());
    }

    @Test
    void shouldStopWhenOffersStillExistAfterWaitingForFullReprice() {
        when(fundingMarketRestClient.getFundingLendbookRateDistribution(any(), any(), any(), any(), any()))
                .thenReturn(distribution(
                        bucket("17.11", "4.90"),
                        bucket("17.12", "5.10")
                ));
        when(fundingAccountSummaryService.getSummary("fUSD"))
                .thenReturn(summary(new BigDecimal("300"), new BigDecimal("700"), List.of(openOffer(12345L, "0.00020000", "700"))))
                .thenReturn(summary(new BigDecimal("300"), new BigDecimal("700"), List.of(openOffer(12345L, "0.00020000", "700"))));

        service.pollTargetFundingRate();

        verify(fundingAccountRestClient).cancelFundingOffer(argThat(request -> request.offerId().equals(12345L)));
        verify(fundingAccountRestClient, never()).createFundingOffer(argThat(item -> true));
    }

    @Test
    void shouldCancelExistingOffersAndCreateChunkedOffersAfterRefresh() {
        when(fundingMarketRestClient.getFundingLendbookRateDistribution(any(), any(), any(), any(), any()))
                .thenReturn(distribution(
                        bucket("11.31", "4.90"),
                        bucket("11.32", "5.10")
                ));
        when(fundingAccountSummaryService.getSummary("fUSD"))
                .thenReturn(summary(new BigDecimal("300"), new BigDecimal("700"), List.of(openOffer(12345L, "0.00020000", "700"))))
                .thenReturn(summary(new BigDecimal("480"), BigDecimal.ZERO, List.of()));

        service.pollTargetFundingRate();

        verify(fundingAccountRestClient).cancelFundingOffer(argThat(request -> request.offerId().equals(12345L)));
        verify(fundingAccountRestClient, times(2)).createFundingOffer(argThat(request ->
                "fUSD".equals(request.symbol())
                        && "150".equals(request.amount())
                        && "0.00030986".equals(request.rate())
                        && request.period().equals(120)
                        && "LIMIT".equals(request.type())
                        && request.flags().equals(0)
        ));
        verify(fundingAccountRestClient, times(1)).createFundingOffer(argThat(request ->
                "fUSD".equals(request.symbol())
                        && "180".equals(request.amount())
                        && "0.00030986".equals(request.rate())
                        && request.period().equals(120)
                        && "LIMIT".equals(request.type())
                        && request.flags().equals(0)
        ));
        verify(fundingAccountRestClient, times(3)).createFundingOffer(any());
    }

    @Test
    void shouldCreateSingleMergedOfferWhenAmountIsBelowTwoBaseChunks() {
        when(fundingMarketRestClient.getFundingLendbookRateDistribution(any(), any(), any(), any(), any()))
                .thenReturn(distribution(
                        bucket("11.31", "4.90"),
                        bucket("11.32", "5.10")
                ));
        when(fundingAccountSummaryService.getSummary("fUSD"))
                .thenReturn(summary(new BigDecimal("299"), BigDecimal.ZERO, List.of()));

        service.pollTargetFundingRate();

        verify(fundingAccountRestClient, times(1)).createFundingOffer(argThat(request ->
                "fUSD".equals(request.symbol())
                        && "299".equals(request.amount())
                        && "0.00030986".equals(request.rate())
                        && request.period().equals(120)
                        && "LIMIT".equals(request.type())
                        && request.flags().equals(0)
        ));
        verify(fundingAccountRestClient, times(1)).createFundingOffer(any());
    }

    @Test
    void shouldStopWhenIdleAmountIsInsufficientAndNoOfferCanBeAdjusted() {
        when(fundingMarketRestClient.getFundingLendbookRateDistribution(any(), any(), any(), any(), any()))
                .thenReturn(distribution(
                        bucket("11.31", "4.90"),
                        bucket("11.32", "5.10")
                ));
        when(fundingAccountSummaryService.getSummary("fUSD"))
                .thenReturn(summary(new BigDecimal("149"), BigDecimal.ZERO, List.of()));

        service.pollTargetFundingRate();

        verify(fundingAccountRestClient, never()).cancelFundingOffer(argThat(item -> true));
        verify(fundingAccountRestClient, never()).createFundingOffer(argThat(item -> true));
    }

    private BitfinexProperties properties() {
        BitfinexProperties properties = new BitfinexProperties();
        properties.setOfferResubmitDelayMillis(0L);
        return properties;
    }

    private FundingLendbookRateDistributionDto distribution(FundingRateBucketDto... buckets) {
        return new FundingLendbookRateDistributionDto(
                "USD",
                60,
                120,
                10000,
                2,
                buckets.length,
                new BigDecimal("1000"),
                "https://api.bitfinex.com/v1/lendbook/USD?limit_bids=0&limit_asks=10000",
                List.of(buckets)
        );
    }

    private FundingRateBucketDto bucket(String rate, String cumulativeSharePercent) {
        return new FundingRateBucketDto(
                new BigDecimal(rate),
                1,
                new BigDecimal("100"),
                new BigDecimal("0.05"),
                new BigDecimal("5.00"),
                new BigDecimal("0.05"),
                new BigDecimal(cumulativeSharePercent),
                false
        );
    }

    private FundingAccountSummaryDto summary(BigDecimal idleAmount, BigDecimal offerAmount, List<FundingPositionDto> offers) {
        return new FundingAccountSummaryDto(
                "fUSD",
                idleAmount.add(offerAmount).add(new BigDecimal("500")),
                new BigDecimal("500"),
                idleAmount,
                offerAmount,
                offers.size(),
                0,
                0,
                offers,
                List.of(),
                List.of(),
                new FundingAccountDiagnosticsDto(
                        "funding",
                        "USD",
                        BigDecimal.ZERO,
                        new BigDecimal("500"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("500"),
                        BigDecimal.ZERO,
                        "test"
                )
        );
    }

    private FundingPositionDto openOffer(long offerId, String rate, String amount) {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode decoded = objectMapper.createObjectNode();
        decoded.put("id", offerId);
        decoded.put("rate", rate);
        decoded.put("amount", amount);
        ArrayNode raw = objectMapper.createArrayNode();
        return new FundingPositionDto("offers", "fUSD", raw, decoded);
    }
}
