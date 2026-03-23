package io.github.yienruuuuu.smartlending.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.yienruuuuu.smartlending.model.CancelFundingOfferRequest;
import io.github.yienruuuuu.smartlending.model.CreateFundingOfferRequest;
import io.github.yienruuuuu.smartlending.model.FundingAccountDiagnosticsDto;
import io.github.yienruuuuu.smartlending.model.FundingAccountSummaryDto;
import io.github.yienruuuuu.smartlending.model.FundingOfferActionDto;
import io.github.yienruuuuu.smartlending.service.BitfinexFundingAccountRestClient;
import io.github.yienruuuuu.smartlending.service.FundingAccountSummaryService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FundingAccountController.class)
class FundingAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BitfinexFundingAccountRestClient fundingAccountRestClient;

    @MockBean
    private FundingAccountSummaryService fundingAccountSummaryService;

    @Test
    void shouldReturnFundingSummary() throws Exception {
        FundingAccountSummaryDto dto = new FundingAccountSummaryDto(
                "fUSD",
                new BigDecimal("1000"),
                new BigDecimal("700"),
                new BigDecimal("300"),
                new BigDecimal("200"),
                1,
                1,
                0,
                List.of(),
                List.of(),
                List.of(),
                new FundingAccountDiagnosticsDto(
                        "funding",
                        "USD",
                        new BigDecimal("0"),
                        new BigDecimal("500"),
                        new BigDecimal("400"),
                        new BigDecimal("300"),
                        new BigDecimal("700"),
                        BigDecimal.ZERO,
                        "max(walletImpliedLentAmount, positionDerivedLentAmount)"
                )
        );

        when(fundingAccountSummaryService.getSummary("fUSD")).thenReturn(dto);

        mockMvc.perform(get("/api/v1/account/funding/summary").param("symbol", "fUSD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("fUSD"))
                .andExpect(jsonPath("$.diagnostics.walletCurrency").value("USD"));

        verify(fundingAccountSummaryService).getSummary("fUSD");
    }

    @Test
    void shouldCreateFundingOffer() throws Exception {
        CreateFundingOfferRequest request = new CreateFundingOfferRequest("fUSD", "1000", "0.0002", 30, "LIMIT", 0);
        FundingOfferActionDto response = new FundingOfferActionDto(
                "submit",
                JsonNodeFactory.instance.arrayNode(),
                objectMapper.createObjectNode().put("status", "SUCCESS")
        );

        when(fundingAccountRestClient.createFundingOffer(request)).thenReturn(response);

        mockMvc.perform(post("/api/v1/account/funding/offers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("submit"))
                .andExpect(jsonPath("$.decoded.status").value("SUCCESS"));

        verify(fundingAccountRestClient).createFundingOffer(request);
    }

    @Test
    void shouldCancelFundingOffer() throws Exception {
        CancelFundingOfferRequest request = new CancelFundingOfferRequest(123456789L);
        FundingOfferActionDto response = new FundingOfferActionDto(
                "cancel",
                JsonNodeFactory.instance.arrayNode(),
                objectMapper.createObjectNode().put("status", "SUCCESS")
        );

        when(fundingAccountRestClient.cancelFundingOffer(request)).thenReturn(response);

        mockMvc.perform(post("/api/v1/account/funding/offers/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("cancel"))
                .andExpect(jsonPath("$.decoded.status").value("SUCCESS"));

        verify(fundingAccountRestClient).cancelFundingOffer(request);
    }

    @Test
    void shouldRejectInvalidFundingOfferRequest() throws Exception {
        mockMvc.perform(post("/api/v1/account/funding/offers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "symbol": "",
                                  "amount": "1000",
                                  "rate": "0.0002",
                                  "period": 1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }
}
