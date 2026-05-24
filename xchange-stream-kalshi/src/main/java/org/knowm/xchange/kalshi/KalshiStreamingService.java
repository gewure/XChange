package org.knowm.xchange.kalshi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.service.netty.JsonNettyStreamingService;
import io.reactivex.rxjava3.core.Observable;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtensionHandler;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import io.netty.handler.codec.http.DefaultHttpHeaders;

public class KalshiStreamingService extends JsonNettyStreamingService {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger messageId = new AtomicInteger(1);
    private final String apiKey;
    private final KalshiDigest signatureCreator;

    public KalshiStreamingService(String apiUrl, String apiKey, KalshiDigest signatureCreator) {
        super(apiUrl);
        this.apiKey = apiKey;
        this.signatureCreator = signatureCreator;
    }

    @Override
    protected DefaultHttpHeaders getCustomHeaders() {
        DefaultHttpHeaders headers = super.getCustomHeaders();
        if (apiKey != null && signatureCreator != null) {
             String timestamp = String.valueOf(System.currentTimeMillis());
             // For websockets, Kalshi specifies signing just the timestamp + method ("GET") + path ("/trade-api/ws/v2")
             String pathForSignature = "/trade-api/ws/v2";
             String signature = signatureCreator.sign(timestamp, "GET", pathForSignature);

             headers.add("KALSHI-ACCESS-KEY", apiKey);
             headers.add("KALSHI-ACCESS-SIGNATURE", signature);
             headers.add("KALSHI-ACCESS-TIMESTAMP", timestamp);
        } else {
             throw new ExchangeSecurityException("Kalshi API requires authentication (apiKey and secretKey)");
        }
        return headers;
    }

    @Override
    protected String getChannelNameFromMessage(JsonNode message) throws IOException {
        String type = message.has("type") ? message.get("type").asText() : "";
        if ("orderbook_snapshot".equals(type) || "orderbook_delta".equals(type)) {
            JsonNode msgNode = message.get("msg");
            if (msgNode != null && msgNode.has("market_ticker")) {
                return "orderbook_delta-" + msgNode.get("market_ticker").asText();
            }
        }
        return type;
    }

    @Override
    public String getSubscribeMessage(String channelName, Object... args) throws IOException {
        if (args.length == 0 || !(args[0] instanceof String)) {
            throw new IllegalArgumentException("Market ticker is required.");
        }
        String marketTicker = (String) args[0];

        // Strip the ticker part from channelName if it's there
        String actualChannel = channelName;
        if (actualChannel.startsWith("orderbook_delta-")) {
            actualChannel = "orderbook_delta";
        }

        KalshiSubscribeMessage message = new KalshiSubscribeMessage(
                messageId.getAndIncrement(),
                "subscribe",
                new KalshiSubscribeParams(Collections.singletonList(actualChannel), marketTicker)
        );

        return mapper.writeValueAsString(message);
    }

    @Override
    public String getUnsubscribeMessage(String channelName, Object... args) throws IOException {
        // Unsubscription in Kalshi uses sid (Subscription ID)
        // For simplicity, we might just not send an unsubscribe message in this barebones implementation
        // or we need to track SIDs.
        return null;
    }

    // Internal classes for JSON serialization
    private static class KalshiSubscribeMessage {
        public int id;
        public String cmd;
        public KalshiSubscribeParams params;

        public KalshiSubscribeMessage(int id, String cmd, KalshiSubscribeParams params) {
            this.id = id;
            this.cmd = cmd;
            this.params = params;
        }
    }

    private static class KalshiSubscribeParams {
        public java.util.List<String> channels;
        public String market_ticker;

        public KalshiSubscribeParams(java.util.List<String> channels, String market_ticker) {
            this.channels = channels;
            this.market_ticker = market_ticker;
        }
    }
}
