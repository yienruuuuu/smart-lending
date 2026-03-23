package io.github.yienruuuuu.smartlending.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.yienruuuuu.smartlending.model.CancelFundingOfferRequest;
import io.github.yienruuuuu.smartlending.model.CreateFundingOfferRequest;
import io.github.yienruuuuu.smartlending.model.FundingOfferActionDto;
import io.github.yienruuuuu.smartlending.model.FundingPositionDto;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BitfinexFundingAccountRestClient {

    private static final Logger log = LoggerFactory.getLogger(BitfinexFundingAccountRestClient.class);
    private static final String DEFAULT_OFFER_TYPE = "LIMIT";

    private final BitfinexAccountRestClient bitfinexAccountRestClient;
    private final ObjectMapper objectMapper;

    public BitfinexFundingAccountRestClient(
            BitfinexAccountRestClient bitfinexAccountRestClient,
            ObjectMapper objectMapper
    ) {
        this.bitfinexAccountRestClient = bitfinexAccountRestClient;
        this.objectMapper = objectMapper;
    }

    public List<FundingPositionDto> getFundingOffers(String symbol) {
        return fetchFundingPositions("offers", symbol);
    }

    public List<FundingPositionDto> getFundingCredits(String symbol) {
        return fetchFundingPositions("credits", symbol);
    }

    public List<FundingPositionDto> getFundingLoans(String symbol) {
        return fetchFundingPositions("loans", symbol);
    }

    public FundingOfferActionDto createFundingOffer(CreateFundingOfferRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("type", normalizeOfferType(request.type()));
        body.put("symbol", request.symbol().trim());
        body.put("amount", request.amount().trim());
        body.put("rate", request.rate().trim());
        body.put("period", request.period());
        body.put("flags", normalizeFlags(request.flags()));

        JsonNode raw = bitfinexAccountRestClient.postAuthenticated("v2/auth/w/funding/offer/submit", body.toString());
        log.info("Submitted funding offer: symbol={}, amount={}, rate={}, period={}", request.symbol(), request.amount(), request.rate(), request.period());
        return new FundingOfferActionDto("submit", raw, decodeFundingOfferAction(raw));
    }

    public FundingOfferActionDto cancelFundingOffer(CancelFundingOfferRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("id", request.offerId());

        JsonNode raw = bitfinexAccountRestClient.postAuthenticated("v2/auth/w/funding/offer/cancel", body.toString());
        log.info("Canceled funding offer: offerId={}", request.offerId());
        return new FundingOfferActionDto("cancel", raw, decodeFundingOfferAction(raw));
    }

    private List<FundingPositionDto> fetchFundingPositions(String type, String symbol) {
        String apiPath = symbol == null || symbol.isBlank()
                ? "v2/auth/r/funding/%s".formatted(type)
                : "v2/auth/r/funding/%s/%s".formatted(type, symbol);

        JsonNode root = bitfinexAccountRestClient.postAuthenticated(apiPath, "{}");
        if (!root.isArray()) {
            throw new IllegalStateException("Unexpected funding response: " + root);
        }

        List<FundingPositionDto> positions = new ArrayList<>();
        for (JsonNode item : root) {
            positions.add(new FundingPositionDto(
                    type,
                    symbol,
                    item.deepCopy(),
                    decodeFundingPosition(type, item)
            ));
        }

        log.info("Fetched funding {} rows: symbol={}, count={}", type, symbol, positions.size());
        return positions;
    }

    private JsonNode decodeFundingOfferAction(JsonNode root) {
        if (!root.isArray()) {
            return root.deepCopy();
        }

        ObjectNode decoded = objectMapper.createObjectNode();
        decoded.put("notificationMts", root.path(0).asLong());
        decoded.put("notificationType", root.path(1).asText());
        decoded.put("messageId", root.path(2).asLong());
        decoded.put("status", root.path(6).asText());
        decoded.put("text", root.path(7).asText());

        JsonNode payload = root.path(4);
        if (payload.isArray()) {
            decoded.set("offer", decodeFundingPosition("offers", payload));
        } else {
            decoded.set("payload", payload.deepCopy());
        }
        return decoded;
    }

    private JsonNode decodeFundingPosition(String type, JsonNode item) {
        ObjectNode decoded = objectMapper.createObjectNode();
        ArrayNode rawFieldOrder = objectMapper.createArrayNode();

        if ("offers".equals(type)) {
            decoded.put("id", item.path(0).asLong());
            decoded.put("symbol", item.path(1).asText());
            decoded.put("mtsCreated", item.path(2).asLong());
            decoded.put("mtsUpdated", item.path(3).asLong());
            decoded.put("amount", item.path(4).asText());
            decoded.put("amountOrig", item.path(5).asText());
            decoded.put("offerType", item.path(6).asText());
            decoded.put("flags", item.path(9).asInt());
            decoded.put("status", item.path(10).asText());
            decoded.put("rate", item.path(14).asText());
            decoded.put("period", item.path(15).asInt());
            decoded.put("notify", item.path(16).asInt());
            decoded.put("hidden", item.path(17).asInt());
            decoded.put("renew", item.path(19).asInt());
            rawFieldOrder.add("id").add("symbol").add("mtsCreated").add("mtsUpdated").add("amount")
                    .add("amountOrig").add("offerType").add("flags").add("status").add("rate")
                    .add("period").add("notify").add("hidden").add("renew");
        } else {
            decoded.put("id", item.path(0).asLong());
            decoded.put("symbol", item.path(1).asText());
            decoded.put("side", item.path(2).asText());
            decoded.put("mtsCreate", item.path(3).asLong());
            decoded.put("mtsUpdate", item.path(4).asLong());
            decoded.put("amount", item.path(5).asText());
            decoded.put("flags", item.path(6).asInt());
            decoded.put("status", item.path(7).asText());
            decoded.put("rate", item.path(11).asText());
            decoded.put("period", item.path(12).asInt());
            decoded.put("mtsOpening", item.path(13).asLong());
            decoded.put("mtsLastPayout", item.path(14).asLong());
            decoded.put("notify", item.path(15).asInt());
            decoded.put("hidden", item.path(16).asInt());
            decoded.put("renew", item.path(18).asInt());
            decoded.put("rateReal", item.path(19).asText());
            rawFieldOrder.add("id").add("symbol").add("side").add("mtsCreate").add("mtsUpdate")
                    .add("amount").add("flags").add("status").add("rate").add("period")
                    .add("mtsOpening").add("mtsLastPayout").add("notify").add("hidden")
                    .add("renew").add("rateReal");
        }

        decoded.set("rawFieldOrder", rawFieldOrder);
        return decoded;
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
