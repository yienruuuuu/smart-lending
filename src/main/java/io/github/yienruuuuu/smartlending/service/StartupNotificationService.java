package io.github.yienruuuuu.smartlending.service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 服務啟動完成後發送 Telegram 啟動通知。
 */
@Service
public class StartupNotificationService {

    private static final DateTimeFormatter STARTUP_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final TelegramNotificationClient telegramNotificationClient;

    public StartupNotificationService(TelegramNotificationClient telegramNotificationClient) {
        this.telegramNotificationClient = telegramNotificationClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void notifyStartup() {
        telegramNotificationClient.sendMessage("""
                Smart Lending 已啟動
                時間：%s
                """.formatted(OffsetDateTime.now().format(STARTUP_TIME_FORMATTER)));
    }
}
