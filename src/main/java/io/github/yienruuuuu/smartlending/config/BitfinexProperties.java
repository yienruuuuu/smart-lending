package io.github.yienruuuuu.smartlending.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "bitfinex")
public class BitfinexProperties {

    private String apiKey = "";
    private String apiSecret = "";

    @NotBlank
    private String restBaseUrl = "https://api.bitfinex.com";

    @NotBlank
    private String publicBaseUrl = "https://api-pub.bitfinex.com";

    @Min(1)
    private int marketConnectTimeoutSeconds = 10;

    @Min(1)
    private int marketReadTimeoutSeconds = 30;

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
}
