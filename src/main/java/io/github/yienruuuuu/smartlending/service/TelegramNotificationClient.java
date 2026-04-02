package io.github.yienruuuuu.smartlending.service;

import io.github.yienruuuuu.smartlending.config.TelegramProperties;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * 極簡 Telegram bot 發送 client。
 */
@Component
public class TelegramNotificationClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationClient.class);

    private final TelegramProperties properties;
    private final RestTemplate restTemplate;

    public TelegramNotificationClient(
            TelegramProperties properties,
            RestTemplateBuilder restTemplateBuilder
    ) {
        this.properties = properties;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void sendMessage(String message) {
        if (!properties.isConfigured()) {
            log.debug("略過 Telegram 通知：未設定 bot token 或 chat id");
            return;
        }

        String url = "https://api.telegram.org/bot" + properties.getBotToken() + "/sendMessage";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            restTemplate.postForEntity(
                    url,
                    new HttpEntity<>(Map.of(
                            "chat_id", properties.getChatId(),
                            "text", message
                    ), headers),
                    String.class
            );
        } catch (RestClientException exception) {
            log.error("Telegram 通知發送失敗", exception);
        }
    }
}
