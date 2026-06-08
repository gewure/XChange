package info.bitrich.xchangestream.poloniex2;

import com.fasterxml.jackson.databind.JsonNode;
import info.bitrich.xchangestream.service.netty.JsonNettyStreamingService;
import io.reactivex.rxjava3.core.Observable;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Created by Lukas Zaoralek on 10.11.17. */
public class PoloniexStreamingService extends JsonNettyStreamingService {
  private static final Logger LOG = LoggerFactory.getLogger(PoloniexStreamingService.class);

  private final Map<String, Observable<JsonNode>> subscriptions = new ConcurrentHashMap<>();

  public PoloniexStreamingService(String apiUrl) {
    // Set idle timeout to 20 seconds
    super(apiUrl, Integer.MAX_VALUE, DEFAULT_CONNECTION_TIMEOUT, DEFAULT_RETRY_DURATION, 20);
  }

  @Override
  protected void handleMessage(JsonNode message) {
    if (message.has("event") && "subscribe".equals(message.get("event").asText())) {
      LOG.info("Poloniex WS Subscribed: {}", message);
      return;
    }
    if (message.has("event") && "unsubscribe".equals(message.get("event").asText())) {
      LOG.info("Poloniex WS Unsubscribed: {}", message);
      return;
    }
    if (message.has("event") && "pong".equals(message.get("event").asText())) {
      LOG.debug("Poloniex WS Pong received");
      return;
    }
    super.handleMessage(message);
  }

  @Override
  protected void handleIdle(io.netty.channel.ChannelHandlerContext ctx) {
    LOG.debug("Sending Poloniex WS Ping...");
    ctx.writeAndFlush(new io.netty.handler.codec.http.websocketx.TextWebSocketFrame("{\"event\":\"ping\"}"));
  }

  @Override
  public boolean processArrayMessageSeparately() {
    return false;
  }

  @Override
  public synchronized Observable<JsonNode> subscribeChannel(String channelName, Object... args) {
    if (!channels.containsKey(channelName)) {
      subscriptions.put(channelName, super.subscribeChannel(channelName, args));
    }
    return subscriptions.get(channelName);
  }

  @Override
  protected String getChannelNameFromMessage(JsonNode message) {
    if (message.has("channel") && message.has("symbol")) {
      return message.get("channel").asText() + ":" + message.get("symbol").asText().toUpperCase();
    }
    if (message.has("channel") && message.has("data") && message.get("data").isArray() && message.get("data").size() > 0) {
      JsonNode data0 = message.get("data").get(0);
      if (data0.has("symbol")) {
        return message.get("channel").asText() + ":" + data0.get("symbol").asText().toUpperCase();
      }
    }
    return "";
  }

  @Override
  public String getSubscribeMessage(String channelName, Object... args) throws IOException {
    String channel = "book";
    String symbol = "BTC_USDT";
    if (channelName.contains(":")) {
      String[] parts = channelName.split(":");
      channel = parts[0];
      symbol = parts[1];
    }
    return String.format("{\"event\":\"subscribe\",\"channel\":[\"%s\"],\"symbols\":[\"%s\"]}", channel, symbol.toLowerCase());
  }

  @Override
  public String getUnsubscribeMessage(String channelName, Object... args) throws IOException {
    String channel = "book";
    String symbol = "BTC_USDT";
    if (channelName.contains(":")) {
      String[] parts = channelName.split(":");
      channel = parts[0];
      symbol = parts[1];
    }
    return String.format("{\"event\":\"unsubscribe\",\"channel\":[\"%s\"],\"symbols\":[\"%s\"]}", channel, symbol.toLowerCase());
  }
}
