package io.github.yienruuuuu.smartlending.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.yienruuuuu.smartlending.model.CreateFundingOfferRequest;
import io.github.yienruuuuu.smartlending.model.FundingOfferActionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 負責替 sub account 建立 Bitfinex funding offer。
 */
@Component
public class SubBitfinexFundingAccountRestClient {

    private static final Logger log = LoggerFactory.getLogger(SubBitfinexFundingAccountRestClient.class);
    private static final String DEFAULT_OFFER_TYPE = "LIMIT";

    private final SubBitfinexAccountRestClient subBitfinexAccountRestClient;
    private final ObjectMapper objectMapper;

    public SubBitfinexFundingAccountRestClient(
            SubBitfinexAccountRestClient subBitfinexAccountRestClient,
            ObjectMapper objectMapper
    ) {
        this.subBitfinexAccountRestClient = subBitfinexAccountRestClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 建立一筆 sub account funding offer。
     */
    public FundingOfferActionDto createFundingOffer(CreateFundingOfferRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("type", normalizeOfferType(request.type()));
        body.put("symbol", request.symbol().trim());
        body.put("amount", request.amount().trim());
        body.put("rate", request.rate().trim());
        body.put("period", request.period());
        body.put("flags", normalizeFlags(request.flags()));

        JsonNode raw = subBitfinexAccountRestClient.postAuthenticated("v2/auth/w/funding/offer/submit", body.toString());
        log.info("已替 sub account 送出 funding 掛單。symbol={}, amount={}, rate={}, period={}", request.symbol(), request.amount(), request.rate(), request.period());
        return new FundingOfferActionDto("submit", raw, raw.deepCopy());
    }

    private String normalizeOfferType(String type) {
        if (type == null || type.isBlank()) {
            return DEFAULT_OFFER_TYPE;
        }
        return type.trim().toUpperCase();
    }

    private int normalizeFlags(Integer flags) {
        return flags == null ? 0 : flags;
    }
}
