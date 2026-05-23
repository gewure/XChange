package org.knowm.xchange.polymarket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.service.netty.JsonNettyStreamingService;
import java.io.IOException;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PolymarketStreamingService extends JsonNettyStreamingService {

  private static final Logger LOG = LoggerFactory.getLogger(PolymarketStreamingService.class);
  private final ObjectMapper mapper = new ObjectMapper();

  public PolymarketStreamingService(String apiUrl) {
    super(apiUrl, Integer.MAX_VALUE);
  }

  @Override
  protected String getChannelNameFromMessage(JsonNode message) throws IOException {
      if (message.isArray() && message.size() > 0) {
          JsonNode node = message.get(0);
          if (node.has("asset_id")) {
              return node.get("asset_id").asText();
          }
      } else if (message.has("price_changes") && message.get("price_changes").isArray() && message.get("price_changes").size() > 0) {
           return message.get("price_changes").get(0).get("asset_id").asText();
      }

    return "unknown";
  }

  @Override
  public String getSubscribeMessage(String channelName, Object... args) throws IOException {
    PolymarketSubscriptionMessage subscribeMessage = new PolymarketSubscriptionMessage(
        Collections.singletonList(channelName), "market");
    return mapper.writeValueAsString(subscribeMessage);
  }

  @Override
  public String getUnsubscribeMessage(String channelName, Object... args) throws IOException {
    // Unsubscribe not explicitly documented for this specific WS
    return null;
  }

  private static class PolymarketSubscriptionMessage {
    public final java.util.List<String> assets_ids;
    public final String type;

    public PolymarketSubscriptionMessage(java.util.List<String> assets_ids, String type) {
      this.assets_ids = assets_ids;
      this.type = type;
    }
  }
}
