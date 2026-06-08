package org.knowm.xchange.polymarket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.service.netty.JsonNettyStreamingService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.Disposable;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PolymarketStreamingService extends JsonNettyStreamingService {

  private static final Logger LOG = LoggerFactory.getLogger(PolymarketStreamingService.class);

  /**
   * How often to send an application-level "PING" to the Polymarket WS.
   * Polymarket's server will echo a "PONG" back, confirming the connection is alive.
   * This is distinct from the Netty-level WS PingWebSocketFrame (controlled by idleTimeoutSeconds).
   */
  private static final int HEARTBEAT_INTERVAL_SECONDS = 10;

  private final ObjectMapper mapper = new ObjectMapper();
  private volatile Disposable heartbeatDisposable;

  private final java.util.Set<String> subscribedAssetIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
  private final java.util.Set<String> activeSubscribedAssetIds = new java.util.HashSet<>();
  private final Object subscriptionLock = new Object();
  private io.reactivex.rxjava3.disposables.Disposable batchDisposable = null;
  private boolean isReconnecting = false;

  public PolymarketStreamingService(String apiUrl) {
    // idleTimeoutSeconds=20: Netty sends a WS-level PingWebSocketFrame after 20s of TCP silence.
    // retryDuration=8s: reconnect delay after a disconnect (default is 15s — too slow for trading).
    // connectionTimeout=10s: standard handshake timeout.
    super(apiUrl, Integer.MAX_VALUE, Duration.ofSeconds(10), Duration.ofSeconds(8), 20);
    // Hook into connection-success events (fires on initial connect AND every auto-reconnect).
    // This is more reliable than overriding connect()/doOnComplete because it works through the
    // NettyStreamingService reconnect path (scheduleReconnect) as well.
    subscribeConnectionSuccess().subscribe(ignored -> {
      synchronized (subscriptionLock) {
        activeSubscribedAssetIds.clear();
        isReconnecting = false;
      }
      startHeartbeat();
    });
  }

  @Override
  public io.reactivex.rxjava3.core.Observable<JsonNode> subscribeChannel(String channelName, Object... args) {
    final String subscriptionUniqueId = getSubscriptionUniqueId(channelName, args);
    LOG.info("Subscribing to Polymarket channel, uniqueId={}, name={}", subscriptionUniqueId, channelName);

    return io.reactivex.rxjava3.core.Observable.<JsonNode>create(
            e -> {
              if (!isSocketOpen()) {
                e.onError(new info.bitrich.xchangestream.service.exception.NotConnectedException());
                return;
              }
              channels.computeIfAbsent(
                  subscriptionUniqueId,
                  cid -> {
                    Subscription newSubscription = new Subscription(e, channelName, args);
                    synchronized (subscriptionLock) {
                      subscribedAssetIds.add(channelName);
                      triggerBatchSubscription();
                    }
                    return newSubscription;
                  });
            })
        .doOnDispose(
            () -> {
              if (channels.remove(subscriptionUniqueId) != null) {
                synchronized (subscriptionLock) {
                  subscribedAssetIds.remove(channelName);
                  triggerBatchSubscription();
                }
              }
            })
        .share();
  }

  @Override
  public void resubscribeChannels() {
    synchronized (subscriptionLock) {
      subscribedAssetIds.clear();
      for (String key : channels.keySet()) {
        Subscription sub = channels.get(key);
        if (sub != null) {
          subscribedAssetIds.add(sub.getChannelName());
        }
      }
      triggerBatchSubscription();
    }
  }

  private void triggerBatchSubscription() {
    synchronized (subscriptionLock) {
      if (batchDisposable != null && !batchDisposable.isDisposed()) {
        batchDisposable.dispose();
      }
      batchDisposable = io.reactivex.rxjava3.core.Observable.timer(4000, TimeUnit.MILLISECONDS)
          .subscribe(
              ignored -> sendActualBatchSubscription(),
              throwable -> LOG.error("Error in batch subscription timer", throwable)
          );
    }
  }

  private void sendActualBatchSubscription() {
    synchronized (subscriptionLock) {
      if (!isSocketOpen()) {
        LOG.warn("Polymarket WebSocket not open, deferring batch subscription");
        return;
      }
      java.util.List<String> assets = new java.util.ArrayList<>(subscribedAssetIds);
      if (assets.isEmpty()) {
        return;
      }

      if (isReconnecting) {
        LOG.info("Polymarket reconnect already in progress, deferring batch subscription");
        return;
      }

      boolean needsReconnect = !activeSubscribedAssetIds.isEmpty() && !subscribedAssetIds.equals(activeSubscribedAssetIds);
      if (needsReconnect) {
        isReconnecting = true;
        triggerReconnect();
      } else {
        try {
          PolymarketSubscriptionMessage subscribeMessage = new PolymarketSubscriptionMessage(assets, "market");
          String payload = mapper.writeValueAsString(subscribeMessage);
          LOG.warn("Sending batch Polymarket subscription for {} assets: {}", assets.size(), payload);
          sendMessage(payload);
          activeSubscribedAssetIds.clear();
          activeSubscribedAssetIds.addAll(subscribedAssetIds);
        } catch (Exception e) {
          LOG.error("Failed to send batch subscription message", e);
        }
      }
    }
  }

  private void triggerReconnect() {
    LOG.warn("Triggering Polymarket WebSocket reconnect to update subscriptions to: {}", subscribedAssetIds);
    disconnect().subscribe(
        () -> {
          LOG.info("Disconnected Polymarket WS for subscription update. Reconnecting...");
          connect().subscribe(
              () -> {
                LOG.info("Reconnected Polymarket WS successfully");
                synchronized (subscriptionLock) {
                  isReconnecting = false;
                }
              },
              err -> {
                LOG.error("Failed to reconnect Polymarket WS", err);
                synchronized (subscriptionLock) {
                  isReconnecting = false;
                  triggerBatchSubscription();
                }
              }
          );
        },
        err -> {
          LOG.error("Failed to disconnect Polymarket WS", err);
          synchronized (subscriptionLock) {
            isReconnecting = false;
            triggerBatchSubscription();
          }
        }
    );
  }

  /**
   * Stop the heartbeat timer when disconnecting to avoid sending pings into a closed socket.
   * Also save and restore the channels map so that manual disconnect/connect cycles do not
   * wipe out active stream subscriptions.
   */
  @Override
  public Completable disconnect() {
    stopHeartbeat();
    final java.util.Map<String, Subscription> savedChannels = new java.util.HashMap<>(channels);
    return super.disconnect().doOnComplete(() -> {
      channels.putAll(savedChannels);
    });
  }

  private synchronized void startHeartbeat() {
    stopHeartbeat();
    LOG.warn("Starting Polymarket application-level heartbeat ({}s interval)", HEARTBEAT_INTERVAL_SECONDS);
    heartbeatDisposable = io.reactivex.rxjava3.core.Observable
        .interval(HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS)
        .subscribe(
            tick -> {
              try {
                sendMessage("PING");
                LOG.debug("Sent application-level ping to Polymarket");
              } catch (Exception e) {
                LOG.warn("Failed to send heartbeat ping to Polymarket: {}", e.getMessage());
              }
            },
            e -> LOG.warn("Polymarket heartbeat timer error: {}", e.getMessage()));
  }

  private synchronized void stopHeartbeat() {
    if (heartbeatDisposable != null && !heartbeatDisposable.isDisposed()) {
      heartbeatDisposable.dispose();
      heartbeatDisposable = null;
      LOG.debug("Stopped Polymarket heartbeat");
    }
  }

  @Override
  public void messageHandler(String message) {
    if ("PONG".equals(message)) {
      LOG.debug("Received pong from Polymarket — connection alive");
      return;
    }
    if ("PING".equals(message)) {
      try {
        sendMessage("PONG");
        LOG.debug("Responded to server-initiated ping with PONG");
      } catch (Exception e) {
        LOG.warn("Failed to respond to server ping: {}", e.getMessage());
      }
      return;
    }
    super.messageHandler(message);
  }

  @Override
  protected void handleMessage(JsonNode message) {
    if (message.has("type") && "pong".equals(message.get("type").asText())) {
      LOG.debug("Received pong from Polymarket — connection alive");
      return;
    }
    if (message.has("event_type") && "price_change".equals(message.get("event_type").asText())) {
      if (message.has("price_changes") && message.get("price_changes").isArray()) {
        java.util.Set<String> assetIds = new java.util.HashSet<>();
        for (JsonNode changeNode : message.get("price_changes")) {
          if (changeNode.has("asset_id")) {
            assetIds.add(changeNode.get("asset_id").asText());
          }
        }
        for (String assetId : assetIds) {
          handleChannelMessage(assetId, message);
        }
        return;
      }
    }
    super.handleMessage(message);
  }

  @Override
  protected String getChannelNameFromMessage(JsonNode message) throws IOException {
      if (message.isArray() && message.size() > 0) {
          JsonNode node = message.get(0);
          if (node.has("asset_id")) {
              return node.get("asset_id").asText();
          }
      } else if (message.has("asset_id")) {
          return message.get("asset_id").asText();
      } else if (message.has("price_changes") && message.get("price_changes").isArray() && message.get("price_changes").size() > 0) {
           return message.get("price_changes").get(0).get("asset_id").asText();
      }

    return "unknown";
  }

  @Override
  public String getSubscribeMessage(String channelName, Object... args) throws IOException {
    PolymarketSubscriptionMessage subscribeMessage = new PolymarketSubscriptionMessage(
        Collections.singletonList(channelName), "market");
    String msg = mapper.writeValueAsString(subscribeMessage);
    LOG.warn("POLY_SUB_MSG: {}", msg);
    return msg;
  }

  @Override
  public String getUnsubscribeMessage(String channelName, Object... args) throws IOException {
    // Unsubscribe not explicitly documented for this specific WS
    return null;
  }

  private static class PolymarketSubscriptionMessage {
    public final java.util.List<String> assets_ids;
    public final String type;
    public final boolean custom_feature_enabled = true;

    public PolymarketSubscriptionMessage(java.util.List<String> assets_ids, String type) {
      this.assets_ids = assets_ids;
      this.type = type;
    }
  }
}
