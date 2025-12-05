package info.bitrich.xchangestream.bitpanda;

import info.bitrich.xchangestream.service.netty.JsonNettyStreamingService;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;

public class BitpandaStreamingService extends JsonNettyStreamingService {

  private final String apiKey;

  public BitpandaStreamingService(String apiUrl, String apiKey) {
    super(apiUrl, Integer.MAX_VALUE, java.time.Duration.ofSeconds(10), java.time.Duration.ofSeconds(15), 10);
    this.apiKey = apiKey;
  }

  @Override
  protected io.netty.handler.codec.http.DefaultHttpHeaders getCustomHeaders() {
    io.netty.handler.codec.http.DefaultHttpHeaders headers = new io.netty.handler.codec.http.DefaultHttpHeaders();
    if (apiKey != null && !apiKey.isEmpty()) {
      headers.add("Authorization", "Bearer " + apiKey);
    }
    return headers;
  }

  @Override
  protected String getChannelNameFromMessage(JsonNode message) throws IOException {
    if (message.has("channel_name")) {
      return message.get("channel_name").asText();
    }
    return "";
  }

  @Override
  public String getSubscribeMessage(String channelName, Object... args) throws IOException {
    // Format: {"type": "SUBSCRIBE", "channels": [{"name": "CHANNEL_NAME",
    // "instrument_codes": ["BTC_EUR"]}]}

    // channelName from XChange is usually the currency pair symbol (e.g. BTC_EUR)
    // We need to determine if it's a TICKER or ORDER_BOOK subscription.
    // However, getSubscribeMessage signature doesn't pass the channel type directly
    // if we use the default implementation.
    // But XChange's StreamingMarketDataService calls subscribeChannel(channelName).

    // We can embed the channel type in the channelName passed to subscribeChannel.
    // e.g. "TICKER:BTC_EUR" or "ORDER_BOOK:BTC_EUR"

    String type = "TICKER";
    String symbol = channelName;

    if (channelName.startsWith("ORDER_BOOK:")) {
      type = "ORDER_BOOK";
      symbol = channelName.substring("ORDER_BOOK:".length());
    } else if (channelName.startsWith("TICKER:")) {
      type = "TICKER";
      symbol = channelName.substring("TICKER:".length());
    }

    return String.format(
        "{\"type\": \"SUBSCRIBE\", \"channels\": [{\"name\": \"%s\", \"instrument_codes\": [\"%s\"]}]}",
        type, symbol);
  }

  @Override
  public String getUnsubscribeMessage(String channelName, Object... args) throws IOException {
    String type = "TICKER";
    String symbol = channelName;

    if (channelName.startsWith("ORDER_BOOK:")) {
      type = "ORDER_BOOK";
      symbol = channelName.substring("ORDER_BOOK:".length());
    } else if (channelName.startsWith("TICKER:")) {
      type = "TICKER";
      symbol = channelName.substring("TICKER:".length());
    }

    return String.format(
        "{\"type\": \"UNSUBSCRIBE\", \"channels\": [{\"name\": \"%s\", \"instrument_codes\": [\"%s\"]}]}",
        type, symbol);
  }
}
