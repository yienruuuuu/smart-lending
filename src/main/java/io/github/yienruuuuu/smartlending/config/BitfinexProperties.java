package io.github.yienruuuuu.smartlending.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
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

    private List<String> fundingSymbols = List.of("fUSD");

    @NotBlank
    private String bookPrecision = "R0";

    @Min(1)
    private int bookLength = 25;

    @Min(1)
    private int pollingIntervalSeconds = 10;

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

    public List<String> getFundingSymbols() {
        return fundingSymbols;
    }

    public void setFundingSymbols(List<String> fundingSymbols) {
        this.fundingSymbols = fundingSymbols;
    }

    public String getBookPrecision() {
        return bookPrecision;
    }

    public void setBookPrecision(String bookPrecision) {
        this.bookPrecision = bookPrecision;
    }

    public int getBookLength() {
        return bookLength;
    }

    public void setBookLength(int bookLength) {
        this.bookLength = bookLength;
    }

    public int getPollingIntervalSeconds() {
        return pollingIntervalSeconds;
    }

    public void setPollingIntervalSeconds(int pollingIntervalSeconds) {
        this.pollingIntervalSeconds = pollingIntervalSeconds;
    }
}
