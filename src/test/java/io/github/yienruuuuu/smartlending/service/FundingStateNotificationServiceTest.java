package io.github.yienruuuuu.smartlending.service;

import io.github.yienruuuuu.smartlending.config.TelegramProperties;
import io.github.yienruuuuu.smartlending.model.FundingStateSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FundingStateNotificationServiceTest {

    private final TelegramProperties telegramProperties = new TelegramProperties();
    private final FundingStateSnapshotService snapshotService = mock(FundingStateSnapshotService.class);
    private final FundingStateBaselineRepository baselineRepository = mock(FundingStateBaselineRepository.class);
    private final TelegramNotificationClient telegramNotificationClient = mock(TelegramNotificationClient.class);
    private final FundingStateNotificationService service = new FundingStateNotificationService(
            telegramProperties,
            snapshotService,
            baselineRepository,
            telegramNotificationClient
    );

    @Test
    void shouldDetectBorrowedTransition() {
        Optional<?> result = service.detectChange(
                snapshot("main", "2026-04-02T00:00:00Z", "300", "0", "0.0004"),
                snapshot("main", "2026-04-02T00:10:00Z", "0", "300", null)
        );

        assertThat(result).isPresent();
        assertThat(result.get().toString()).contains("有人借款了");
    }

    @Test
    void shouldDetectRepaidTransition() {
        Optional<?> result = service.detectChange(
                snapshot("sub", "2026-04-02T00:00:00Z", "0", "300", null),
                snapshot("sub", "2026-04-02T00:10:00Z", "300", "0", "0.00042")
        );

        assertThat(result).isPresent();
        assertThat(result.get().toString()).contains("有人還款了");
    }

    @Test
    void shouldDetectRepricedTransition() {
        Optional<?> result = service.detectChange(
                snapshot("main", "2026-04-02T00:00:00Z", "150", "300", "0.00040"),
                snapshot("main", "2026-04-02T00:10:00Z", "150", "300", "0.00045")
        );

        assertThat(result).isPresent();
        assertThat(result.get().toString()).contains("根據訂單簿重新掛單");
    }

    @Test
    void shouldNotNotifyOnBaselineOnly() {
        Optional<?> result = service.detectChange(
                null,
                snapshot("main", "2026-04-02T00:10:00Z", "150", "300", "0.00045")
        );

        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotNotifyWhenStateIsUnchanged() {
        FundingStateSnapshot snapshot = snapshot("main", "2026-04-02T00:00:00Z", "150", "300", "0.00040");

        Optional<?> result = service.detectChange(snapshot, snapshot);

        assertThat(result).isEmpty();
    }

    private FundingStateSnapshot snapshot(String account, String capturedAt, String offerAmount, String lentAmount, String rate) {
        return new FundingStateSnapshot(
                account,
                Instant.parse(capturedAt),
                new BigDecimal(offerAmount),
                new BigDecimal(lentAmount),
                new BigDecimal(offerAmount).compareTo(BigDecimal.ZERO) > 0 ? 1 : 0,
                0,
                0,
                rate == null ? null : new BigDecimal(rate)
        );
    }
}
