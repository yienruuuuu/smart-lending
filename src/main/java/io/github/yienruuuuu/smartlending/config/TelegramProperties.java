package io.github.yienruuuuu.smartlending.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Telegram 通知設定。
 */
@ConfigurationProperties(prefix = "telegram")
public class TelegramProperties {

    private boolean enabled = true;
    private String botToken = "";
    private String chatId = "";
    private long pollFixedDelayMillis = 600000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBotToken() {
        return botToken;
    }

    public void setBotToken(String botToken) {
        this.botToken = botToken;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public long getPollFixedDelayMillis() {
        return pollFixedDelayMillis;
    }

    public void setPollFixedDelayMillis(long pollFixedDelayMillis) {
        this.pollFixedDelayMillis = pollFixedDelayMillis;
    }

    public boolean isConfigured() {
        return enabled
                && botToken != null
                && !botToken.isBlank()
                && chatId != null
                && !chatId.isBlank();
    }
}
