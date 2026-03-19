package io.github.yienruuuuu.smartlending.controller;

import io.github.yienruuuuu.smartlending.model.FundingTickerDto;
import io.github.yienruuuuu.smartlending.service.BitfinexFundingMarketRestClient;
import java.math.BigDecimal;
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
}
