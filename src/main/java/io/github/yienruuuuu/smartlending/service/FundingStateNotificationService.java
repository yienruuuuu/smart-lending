package io.github.yienruuuuu.smartlending.service;

import io.github.yienruuuuu.smartlending.config.TelegramProperties;
import io.github.yienruuuuu.smartlending.model.FundingStateBaseline;
import io.github.yienruuuuu.smartlending.model.FundingStateChangeNotification;
import io.github.yienruuuuu.smartlending.model.FundingStateSnapshot;
import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 比對 funding 狀態切換並發送 Telegram 通知。
 */
@Service
public class FundingStateNotificationService {

    private static final Logger log = LoggerFactory.getLogger(FundingStateNotificationService.class);

    private final TelegramProperties telegramProperties;
    private final FundingStateSnapshotService fundingStateSnapshotService;
    private final FundingStateBaselineRepository baselineRepository;
    private final TelegramNotificationClient telegramNotificationClient;

    public FundingStateNotificationService(
            TelegramProperties telegramProperties,
            FundingStateSnapshotService fundingStateSnapshotService,
            FundingStateBaselineRepository baselineRepository,
            TelegramNotificationClient telegramNotificationClient
    ) {
        this.telegramProperties = telegramProperties;
        this.fundingStateSnapshotService = fundingStateSnapshotService;
        this.baselineRepository = baselineRepository;
        this.telegramNotificationClient = telegramNotificationClient;
    }

    @Scheduled(initialDelay = 20000L, fixedDelayString = "${telegram.poll-fixed-delay-millis:600000}")
    public void pollAndNotify() {
        if (!telegramProperties.isEnabled()) {
            log.debug("略過 Telegram funding 狀態通知：telegram.enabled=false");
            return;
        }

        FundingStateBaseline baseline = baselineRepository.load();
        Optional<FundingStateSnapshot> currentMain = fundingStateSnapshotService.captureMain();
        Optional<FundingStateSnapshot> currentSub = fundingStateSnapshotService.captureSub();

        currentMain.flatMap(current -> detectChange(baseline.main(), current))
                .ifPresent(notification -> telegramNotificationClient.sendMessage(formatMessage(notification)));
        currentSub.flatMap(current -> detectChange(baseline.sub(), current))
                .ifPresent(notification -> telegramNotificationClient.sendMessage(formatMessage(notification)));

        baselineRepository.save(new FundingStateBaseline(
                currentMain.orElse(baseline.main()),
                currentSub.orElse(baseline.sub())
        ));
    }

    Optional<FundingStateChangeNotification> detectChange(FundingStateSnapshot previous, FundingStateSnapshot current) {
        if (previous == null || current == null) {
            return Optional.empty();
        }

        if (isBorrowed(previous, current)) {
            return Optional.of(notification("有人借款了", current.account(), fallbackOfferAmount(previous, current), fallbackOfferRate(previous, current)));
        }

        if (isRepaid(previous, current)) {
            return Optional.of(notification("有人還款了", current.account(), fallbackOfferAmount(previous, current), fallbackOfferRate(previous, current)));
        }

        if (isRepriced(previous, current)) {
            return Optional.of(notification("根據訂單簿重新掛單", current.account(), fallbackOfferAmount(previous, current), fallbackOfferRate(previous, current)));
        }

        return Optional.empty();
    }

    private boolean isBorrowed(FundingStateSnapshot previous, FundingStateSnapshot current) {
        return previous.offerAmount().compareTo(BigDecimal.ZERO) > 0
                && previous.lentAmount().compareTo(BigDecimal.ZERO) == 0
                && current.lentAmount().compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean isRepaid(FundingStateSnapshot previous, FundingStateSnapshot current) {
        return previous.lentAmount().compareTo(BigDecimal.ZERO) > 0
                && previous.offerAmount().compareTo(BigDecimal.ZERO) == 0
                && current.offerAmount().compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean isRepriced(FundingStateSnapshot previous, FundingStateSnapshot current) {
        return previous.lentAmount().compareTo(BigDecimal.ZERO) > 0
                && current.lentAmount().compareTo(BigDecimal.ZERO) > 0
                && previous.primaryOfferRate() != null
                && current.primaryOfferRate() != null
                && previous.primaryOfferRate().compareTo(current.primaryOfferRate()) != 0;
    }

    private FundingStateChangeNotification notification(String eventType, String account, BigDecimal offerAmount, BigDecimal offerRate) {
        return new FundingStateChangeNotification(eventType, account, offerAmount, offerRate);
    }

    private BigDecimal fallbackOfferAmount(FundingStateSnapshot previous, FundingStateSnapshot current) {
        return current.offerAmount().compareTo(BigDecimal.ZERO) > 0 ? current.offerAmount() : previous.offerAmount();
    }

    private BigDecimal fallbackOfferRate(FundingStateSnapshot previous, FundingStateSnapshot current) {
        return current.primaryOfferRate() != null ? current.primaryOfferRate() : previous.primaryOfferRate();
    }

    private String formatMessage(FundingStateChangeNotification notification) {
        return """
                Smart Lending 通知
                事件：%s
                帳號：%s
                掛單金額：%s
                掛單利率：%s
                """.formatted(
                notification.eventType(),
                accountLabel(notification.account()),
                toPlainString(notification.offerAmount()),
                toPlainString(notification.offerRate())
        );
    }

    private String accountLabel(String account) {
        return "sub".equalsIgnoreCase(account) ? "子帳號" : "主要帳號";
    }

    private String toPlainString(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }
}
