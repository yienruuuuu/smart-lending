package io.github.yienruuuuu.smartlending.service;

import io.github.yienruuuuu.smartlending.config.BitfinexProperties;
import io.github.yienruuuuu.smartlending.model.WalletBalanceDto;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubFundingOfferSchedulerServiceTest {

    private final BitfinexProperties properties = new BitfinexProperties();
    private final SubBitfinexAccountRestClient subBitfinexAccountRestClient = mock(SubBitfinexAccountRestClient.class);
    private final SubBitfinexFundingAccountRestClient subBitfinexFundingAccountRestClient = mock(SubBitfinexFundingAccountRestClient.class);
    private final SubFundingOfferSchedulerService service = new SubFundingOfferSchedulerService(
            properties,
            subBitfinexAccountRestClient,
            subBitfinexFundingAccountRestClient
    );

    @Test
    void shouldSkipWhenSubCredentialsAreMissing() {
        service.submitFixedOfferWhenIdleAmountIsEnough();

        verify(subBitfinexAccountRestClient, never()).getWallets();
        verify(subBitfinexFundingAccountRestClient, never()).createFundingOffer(argThat(item -> true));
    }

    @Test
    void shouldDoNothingWhenIdleAmountIsLessThanOrEqualToThreshold() {
        properties.setSubApiKey("sub-key");
        properties.setSubApiSecret("sub-secret");
        when(subBitfinexAccountRestClient.getWallets()).thenReturn(List.of(wallet("150")));

        service.submitFixedOfferWhenIdleAmountIsEnough();

        verify(subBitfinexFundingAccountRestClient, never()).createFundingOffer(argThat(item -> true));
    }

    @Test
    void shouldCreateFixedOfferWhenIdleAmountIsGreaterThanThreshold() {
        properties.setSubApiKey("sub-key");
        properties.setSubApiSecret("sub-secret");
        when(subBitfinexAccountRestClient.getWallets()).thenReturn(List.of(wallet("180")));

        service.submitFixedOfferWhenIdleAmountIsEnough();

        verify(subBitfinexFundingAccountRestClient).createFundingOffer(argThat(request ->
                "fUSD".equals(request.symbol())
                        && "180".equals(request.amount())
                        && "0.000435".equals(request.rate())
                        && request.period().equals(120)
                        && "LIMIT".equals(request.type())
                        && request.flags().equals(0)
        ));
    }

    private WalletBalanceDto wallet(String availableBalance) {
        return new WalletBalanceDto(
                "funding",
                "USD",
                new BigDecimal(availableBalance),
                BigDecimal.ZERO,
                new BigDecimal(availableBalance)
        );
    }
}
