package io.github.yienruuuuu.smartlending.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bitfinex 相關設定。
 *
 * <p>包含主帳戶與 sub 帳戶憑證、REST base URL、公開市場查詢 timeout，以及取消掛單後重新下單前的等待時間。
 */
@Validated
@ConfigurationProperties(prefix = "bitfinex")
public class BitfinexProperties {

    private String apiKey = "";
    private String apiSecret = "";
    private String subApiKey = "";
    private String subApiSecret = "";

    @NotBlank
    private String restBaseUrl = "https://api.bitfinex.com";

    @NotBlank
    private String publicBaseUrl = "https://api-pub.bitfinex.com";

    @Min(1)
    private int marketConnectTimeoutSeconds = 10;

    @Min(1)
    private int marketReadTimeoutSeconds = 30;

    @Min(0)
    private long offerResubmitDelayMillis = 3000L;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }

    public String getSubApiKey() {
        return subApiKey;
    }

    public void setSubApiKey(String subApiKey) {
        this.subApiKey = subApiKey;
    }

    public String getSubApiSecret() {
        return subApiSecret;
    }

    public void setSubApiSecret(String subApiSecret) {
        this.subApiSecret = subApiSecret;
    }

    public String getRestBaseUrl() {
        return restBaseUrl;
    }

    public void setRestBaseUrl(String restBaseUrl) {
        this.restBaseUrl = restBaseUrl;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public int getMarketConnectTimeoutSeconds() {
        return marketConnectTimeoutSeconds;
    }

    public void setMarketConnectTimeoutSeconds(int marketConnectTimeoutSeconds) {
        this.marketConnectTimeoutSeconds = marketConnectTimeoutSeconds;
    }

    public int getMarketReadTimeoutSeconds() {
        return marketReadTimeoutSeconds;
    }

    public void setMarketReadTimeoutSeconds(int marketReadTimeoutSeconds) {
        this.marketReadTimeoutSeconds = marketReadTimeoutSeconds;
    }

    public long getOfferResubmitDelayMillis() {
        return offerResubmitDelayMillis;
    }

    public void setOfferResubmitDelayMillis(long offerResubmitDelayMillis) {
        this.offerResubmitDelayMillis = offerResubmitDelayMillis;
    }

    public boolean hasSubAccountCredentials() {
        return subApiKey != null
                && !subApiKey.isBlank()
                && subApiSecret != null
                && !subApiSecret.isBlank();
    }
}
