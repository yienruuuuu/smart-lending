package io.github.yienruuuuu.smartlending.controller;

import io.github.yienruuuuu.smartlending.model.FundingLendbookRateDistributionDto;
import io.github.yienruuuuu.smartlending.model.FundingLendbookSummaryDto;
import io.github.yienruuuuu.smartlending.model.FundingRateBucketDto;
import io.github.yienruuuuu.smartlending.model.FundingTickerDto;
import io.github.yienruuuuu.smartlending.service.BitfinexFundingMarketRestClient;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FundingMarketController.class)
class FundingMarketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BitfinexFundingMarketRestClient fundingMarketRestClient;

    @Test
    void shouldReturnFundingTicker() throws Exception {
        FundingTickerDto dto = new FundingTickerDto(
                "fUSD",
                new BigDecimal("0.0002"),
                new BigDecimal("0.00019"),
                new BigDecimal("2"),
                new BigDecimal("1000"),
                new BigDecimal("0.00021"),
                new BigDecimal("30"),
                new BigDecimal("800"),
                new BigDecimal("0.00001"),
                new BigDecimal("0.05"),
                new BigDecimal("0.0002"),
                new BigDecimal("15000"),
                new BigDecimal("0.00025"),
                new BigDecimal("0.00018"),
                new BigDecimal("500000")
        );

        when(fundingMarketRestClient.getFundingTicker("fUSD")).thenReturn(dto);

        mockMvc.perform(get("/api/v1/funding/market/ticker").param("symbol", "fUSD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("fUSD"))
                .andExpect(jsonPath("$.frr").value(0.0002))
                .andExpect(jsonPath("$.frrAmountAvailable").value(500000));

        verify(fundingMarketRestClient).getFundingTicker("fUSD");
    }

    @Test
    void shouldReturnFundingLendbookSummary() throws Exception {
        FundingLendbookSummaryDto dto = new FundingLendbookSummaryDto(
                "USD",
                "fUSD",
                30,
                120,
                95,
                25,
                new BigDecimal("20000000.00"),
                new BigDecimal("15000000.00"),
                new BigDecimal("5000000.00"),
                new BigDecimal("4500000.00"),
                new BigDecimal("13633888.46"),
                new BigDecimal("6366111.54"),
                new BigDecimal("0.75"),
                "https://api.bitfinex.com/v1/lendbook/USD?limit_bids=0&limit_asks=10000",
                "https://api-pub.bitfinex.com/v2/ticker/fUSD"
        );

        when(fundingMarketRestClient.getFundingLendbookSummary("USD", 10000, 30)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/funding/market/lendbook/summary")
                        .param("currency", "USD")
                        .param("limitAsks", "10000")
                        .param("minPeriodExclusive", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.fundingSymbol").value("fUSD"))
                .andExpect(jsonPath("$.minPeriodExclusive").value(30))
                .andExpect(jsonPath("$.askCount").value(120))
                .andExpect(jsonPath("$.frrAskAmountFromBook").value(15000000.00))
                .andExpect(jsonPath("$.amountBeforeFirstFrrAsk").value(4500000.00))
                .andExpect(jsonPath("$.frrAmountAvailableFromTicker").value(13633888.46))
                .andExpect(jsonPath("$.nonFrrOrderBookAmountByTicker").value(6366111.54));

        verify(fundingMarketRestClient).getFundingLendbookSummary("USD", 10000, 30);
    }

    @Test
    void shouldReturnFundingLendbookRateDistribution() throws Exception {
        FundingLendbookRateDistributionDto dto = new FundingLendbookRateDistributionDto(
                "USD",
                2,
                30,
                10000,
                1,
                5,
                new BigDecimal("11000000"),
                "https://api.bitfinex.com/v1/lendbook/USD?limit_bids=0&limit_asks=10000",
                List.of(
                        new FundingRateBucketDto(new BigDecimal("14.6"), 2, new BigDecimal("3000000"), new BigDecimal("0.27272727"), new BigDecimal("27.27"), new BigDecimal("0.27272727"), new BigDecimal("27.27"), false),
                        new FundingRateBucketDto(new BigDecimal("14.7"), 3, new BigDecimal("8000000"), new BigDecimal("0.72727273"), new BigDecimal("72.73"), new BigDecimal("1.00000000"), new BigDecimal("100.00"), true)
                )
        );

        when(fundingMarketRestClient.getFundingLendbookRateDistribution("USD", 2, 30, 10000, 1)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/funding/market/lendbook/rate-distribution")
                        .param("currency", "USD")
                        .param("minPeriod", "2")
                        .param("maxPeriod", "30")
                        .param("limitAsks", "10000")
                        .param("rateScale", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.minPeriod").value(2))
                .andExpect(jsonPath("$.maxPeriod").value(30))
                .andExpect(jsonPath("$.rateScale").value(1))
                .andExpect(jsonPath("$.matchedAskCount").value(5))
                .andExpect(jsonPath("$.matchedTotalAmount").value(11000000))
                .andExpect(jsonPath("$.buckets[0].roundedRate").value(14.6))
                .andExpect(jsonPath("$.buckets[0].totalAmount").value(3000000))
                .andExpect(jsonPath("$.buckets[0].amountSharePercent").value(27.27))
                .andExpect(jsonPath("$.buckets[0].cumulativeSharePercent").value(27.27))
                .andExpect(jsonPath("$.buckets[0].isFrr").value(false))
                .andExpect(jsonPath("$.buckets[1].roundedRate").value(14.7))
                .andExpect(jsonPath("$.buckets[1].totalAmount").value(8000000))
                .andExpect(jsonPath("$.buckets[1].amountSharePercent").value(72.73))
                .andExpect(jsonPath("$.buckets[1].cumulativeSharePercent").value(100.00))
                .andExpect(jsonPath("$.buckets[1].isFrr").value(true));

        verify(fundingMarketRestClient).getFundingLendbookRateDistribution("USD", 2, 30, 10000, 1);
    }
}
