package org.knowm.xchange.polymarket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.instrument.Instrument;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PolymarketStreamingMarketDataService implements StreamingMarketDataService {

  private static final Logger LOG = LoggerFactory.getLogger(PolymarketStreamingMarketDataService.class);

  private final PolymarketStreamingService streamingService;
  private final ObjectMapper mapper = new ObjectMapper();

  private final java.util.Map<String, LocalOrderBook> orderBooks = new java.util.concurrent.ConcurrentHashMap<>();

  public PolymarketStreamingMarketDataService(ExchangeSpecification exchangeSpecification) {
    String uri = "wss://ws-subscriptions-clob.polymarket.com/ws/market";
    this.streamingService = new PolymarketStreamingService(uri);
  }

  public Completable connect() {
    return streamingService.connect();
  }

  public Completable disconnect() {
    return streamingService.disconnect();
  }

  public boolean isSocketOpen() {
    return streamingService.isSocketOpen();
  }

  @Override
  public Observable<OrderBook> getOrderBook(CurrencyPair currencyPair, Object... args) {
    return getOrderBook((Instrument) currencyPair, args);
  }

  @Override
  public Observable<OrderBook> getOrderBook(Instrument instrument, Object... args) {
      if (args == null || args.length == 0 || !(args[0] instanceof String)) {
          throw new IllegalArgumentException("Asset ID is required as the first argument.");
      }
      String assetId = (String) args[0];
      String yesTokenId;
      String noTokenId = null;
      if (assetId.contains(",")) {
          String[] tokens = assetId.split(",");
          yesTokenId = tokens[0];
          noTokenId = tokens[1];
      } else {
          yesTokenId = assetId;
      }

      Observable<JsonNode> yesObservable = streamingService.subscribeChannel(yesTokenId);
      Observable<JsonNode> mergedObservable;
      if (noTokenId != null) {
          Observable<JsonNode> noObservable = streamingService.subscribeChannel(noTokenId);
          mergedObservable = Observable.merge(yesObservable, noObservable);
      } else {
          mergedObservable = yesObservable;
      }

      final String finalYesTokenId = yesTokenId;
      final String finalNoTokenId = noTokenId;

      return mergedObservable
          .map(jsonNode -> {
               if (jsonNode.isArray() && jsonNode.size() > 0) {
                    JsonNode node = jsonNode.get(0);
                    if (node.has("bids") && node.has("asks")) {
                         String msgAssetId = node.has("asset_id") ? node.get("asset_id").asText() : null;
                         if (msgAssetId != null && (msgAssetId.equals(finalYesTokenId) || msgAssetId.equals(finalNoTokenId))) {
                             LocalOrderBook targetBook = orderBooks.computeIfAbsent(msgAssetId, k -> new LocalOrderBook());
                             targetBook.handleSnapshot(node);
                         }
                    }
               } else if (jsonNode.has("event_type") && "book".equals(jsonNode.get("event_type").asText())) {
                    String msgAssetId = jsonNode.has("asset_id") ? jsonNode.get("asset_id").asText() : null;
                    if (msgAssetId != null && (msgAssetId.equals(finalYesTokenId) || msgAssetId.equals(finalNoTokenId))) {
                        LocalOrderBook targetBook = orderBooks.computeIfAbsent(msgAssetId, k -> new LocalOrderBook());
                        targetBook.handleSnapshot(jsonNode);
                    }
               } else if (jsonNode.has("event_type") && "price_change".equals(jsonNode.get("event_type").asText())) {
                    if (jsonNode.has("price_changes") && jsonNode.get("price_changes").isArray()) {
                        for (JsonNode changeNode : jsonNode.get("price_changes")) {
                            if (changeNode.has("asset_id")) {
                                String changeAssetId = changeNode.get("asset_id").asText();
                                if (changeAssetId.equals(finalYesTokenId) || changeAssetId.equals(finalNoTokenId)) {
                                    LocalOrderBook targetBook = orderBooks.computeIfAbsent(changeAssetId, k -> new LocalOrderBook());
                                    targetBook.handleSingleDelta(changeNode, jsonNode.get("timestamp"));
                                }
                            }
                        }
                    }
               }
               
               LocalOrderBook yesBook = orderBooks.computeIfAbsent(finalYesTokenId, k -> new LocalOrderBook());
               LocalOrderBook noBook = finalNoTokenId != null ? orderBooks.computeIfAbsent(finalNoTokenId, k -> new LocalOrderBook()) : null;
               
               return yesBook.toCombinedOrderBook(instrument, noBook);
          })
          .filter(ob -> ob.getBids().size() > 0 && ob.getAsks().size() > 0)
          .doOnDispose(() -> {
              orderBooks.remove(finalYesTokenId);
              if (finalNoTokenId != null) {
                  orderBooks.remove(finalNoTokenId);
              }
              LOG.info("Removed local orderbook cache for assetId: {}{}", finalYesTokenId,
                       finalNoTokenId != null ? " and " + finalNoTokenId : "");
          });
  }

  private static class LocalOrderBook {
      private final java.util.TreeMap<BigDecimal, BigDecimal> bids = new java.util.TreeMap<>(java.util.Comparator.reverseOrder());
      private final java.util.TreeMap<BigDecimal, BigDecimal> asks = new java.util.TreeMap<>();
      private Date lastTimestamp = new Date();

      public synchronized void handleSnapshot(JsonNode node) {
          bids.clear();
          asks.clear();
          if (node.has("timestamp")) {
              lastTimestamp = new Date(Long.parseLong(node.get("timestamp").asText()));
          }
          if (node.has("bids")) {
              for (JsonNode bidNode : node.get("bids")) {
                  BigDecimal price = new BigDecimal(bidNode.get("price").asText());
                  BigDecimal size = new BigDecimal(bidNode.get("size").asText());
                  bids.put(price, size);
              }
          }
          if (node.has("asks")) {
              for (JsonNode askNode : node.get("asks")) {
                  BigDecimal price = new BigDecimal(askNode.get("price").asText());
                  BigDecimal size = new BigDecimal(askNode.get("size").asText());
                  asks.put(price, size);
              }
          }
      }

      public synchronized void handleDelta(JsonNode node, String assetId) {
          if (node.has("timestamp")) {
              lastTimestamp = new Date(Long.parseLong(node.get("timestamp").asText()));
          }
          if (node.has("price_changes")) {
              for (JsonNode changeNode : node.get("price_changes")) {
                  if (changeNode.has("asset_id") && assetId.equals(changeNode.get("asset_id").asText())) {
                      BigDecimal price = new BigDecimal(changeNode.get("price").asText());
                      BigDecimal size = new BigDecimal(changeNode.get("size").asText());
                      String side = changeNode.get("side").asText();
                      
                      java.util.TreeMap<BigDecimal, BigDecimal> sideMap = "BUY".equals(side) ? bids : asks;
                      if (size.compareTo(BigDecimal.ZERO) == 0) {
                          sideMap.remove(price);
                      } else {
                          sideMap.put(price, size);
                      }
                  }
              }
          }
      }

      public synchronized void handleSingleDelta(JsonNode changeNode, JsonNode timestampNode) {
          if (timestampNode != null) {
              lastTimestamp = new Date(Long.parseLong(timestampNode.asText()));
          }
          BigDecimal price = new BigDecimal(changeNode.get("price").asText());
          BigDecimal size = new BigDecimal(changeNode.get("size").asText());
          String side = changeNode.get("side").asText();
          
          java.util.TreeMap<BigDecimal, BigDecimal> sideMap = "BUY".equals(side) ? bids : asks;
          if (size.compareTo(BigDecimal.ZERO) == 0) {
              sideMap.remove(price);
          } else {
              sideMap.put(price, size);
          }
      }

      public synchronized OrderBook toCombinedOrderBook(Instrument instrument, LocalOrderBook noBook) {
          List<LimitOrder> bidOrders = new ArrayList<>();
          List<LimitOrder> askOrders = new ArrayList<>();

          // YES-native bids — direct liquidity (trade YES on Polymarket)
          for (java.util.Map.Entry<BigDecimal, BigDecimal> entry : this.bids.entrySet()) {
              LimitOrder o = new LimitOrder.Builder(Order.OrderType.BID, instrument)
                      .originalAmount(entry.getValue())
                      .timestamp(lastTimestamp)
                      .limitPrice(entry.getKey())
                      .userReference("direct")
                      .build();
              bidOrders.add(o);
          }

          // YES-native asks — direct liquidity (trade YES on Polymarket)
          for (java.util.Map.Entry<BigDecimal, BigDecimal> entry : this.asks.entrySet()) {
              LimitOrder o = new LimitOrder.Builder(Order.OrderType.ASK, instrument)
                      .originalAmount(entry.getValue())
                      .timestamp(lastTimestamp)
                      .limitPrice(entry.getKey())
                      .userReference("direct")
                      .build();
              askOrders.add(o);
          }

          if (noBook != null) {
              synchronized (noBook) {
                  // NO bids → implied YES asks at (1 - no_bid_price); trade NO on Polymarket
                  for (java.util.Map.Entry<BigDecimal, BigDecimal> entry : noBook.bids.entrySet()) {
                      BigDecimal yesPrice = BigDecimal.ONE.subtract(entry.getKey());
                      LimitOrder o = new LimitOrder.Builder(Order.OrderType.ASK, instrument)
                              .originalAmount(entry.getValue())
                              .timestamp(noBook.lastTimestamp)
                              .limitPrice(yesPrice)
                              .userReference("implied")
                              .build();
                      askOrders.add(o);
                  }
                  // NO asks → implied YES bids at (1 - no_ask_price); trade NO on Polymarket
                  for (java.util.Map.Entry<BigDecimal, BigDecimal> entry : noBook.asks.entrySet()) {
                      BigDecimal yesPrice = BigDecimal.ONE.subtract(entry.getKey());
                      LimitOrder o = new LimitOrder.Builder(Order.OrderType.BID, instrument)
                              .originalAmount(entry.getValue())
                              .timestamp(noBook.lastTimestamp)
                              .limitPrice(yesPrice)
                              .userReference("implied")
                              .build();
                      bidOrders.add(o);
                  }
              }
          }

          // Sort: bids descending by price, asks ascending by price
          bidOrders.sort((a, b) -> b.getLimitPrice().compareTo(a.getLimitPrice()));
          askOrders.sort((a, b) -> a.getLimitPrice().compareTo(b.getLimitPrice()));

          return new OrderBook(lastTimestamp, askOrders, bidOrders);
      }

      public synchronized OrderBook toOrderBook(Instrument instrument) {
          List<LimitOrder> bidOrders = new ArrayList<>();
          for (java.util.Map.Entry<BigDecimal, BigDecimal> entry : bids.entrySet()) {
              bidOrders.add(new LimitOrder(Order.OrderType.BID, entry.getValue(), instrument, "", lastTimestamp, entry.getKey()));
          }
          List<LimitOrder> askOrders = new ArrayList<>();
          for (java.util.Map.Entry<BigDecimal, BigDecimal> entry : asks.entrySet()) {
              askOrders.add(new LimitOrder(Order.OrderType.ASK, entry.getValue(), instrument, "", lastTimestamp, entry.getKey()));
          }
          return new OrderBook(lastTimestamp, askOrders, bidOrders);
      }
  }

  private OrderBook parseOrderBook(JsonNode node, Instrument instrument) {
        List<LimitOrder> bids = new ArrayList<>();
        List<LimitOrder> asks = new ArrayList<>();

        Date timestamp = null;
        if (node.has("timestamp")) {
            timestamp = new Date(Long.parseLong(node.get("timestamp").asText()));
        }

        if (node.has("bids")) {
            for (JsonNode bidNode : node.get("bids")) {
                BigDecimal price = new BigDecimal(bidNode.get("price").asText());
                BigDecimal size = new BigDecimal(bidNode.get("size").asText());
                bids.add(new LimitOrder(Order.OrderType.BID, size, instrument, "", timestamp, price));
            }
        }

        if (node.has("asks")) {
            for (JsonNode askNode : node.get("asks")) {
                BigDecimal price = new BigDecimal(askNode.get("price").asText());
                BigDecimal size = new BigDecimal(askNode.get("size").asText());
                asks.add(new LimitOrder(Order.OrderType.ASK, size, instrument, "", timestamp, price));
            }
        }

        return new OrderBook(timestamp, asks, bids);
  }

  private OrderBook parsePriceChange(JsonNode node, Instrument instrument, String assetId) {
      List<LimitOrder> bids = new ArrayList<>();
      List<LimitOrder> asks = new ArrayList<>();

      Date timestamp = null;
      if (node.has("timestamp")) {
          timestamp = new Date(Long.parseLong(node.get("timestamp").asText()));
      }

      if (node.has("price_changes")) {
          for (JsonNode changeNode : node.get("price_changes")) {
              if (changeNode.has("asset_id") && assetId.equals(changeNode.get("asset_id").asText())) {
                  BigDecimal price = new BigDecimal(changeNode.get("price").asText());
                  BigDecimal size = new BigDecimal(changeNode.get("size").asText());
                  String side = changeNode.get("side").asText();
                  if ("BUY".equals(side)) {
                      bids.add(new LimitOrder(Order.OrderType.BID, size, instrument, "", timestamp, price));
                  } else if ("SELL".equals(side)) {
                      asks.add(new LimitOrder(Order.OrderType.ASK, size, instrument, "", timestamp, price));
                  }
              }
          }
      }

      return new OrderBook(timestamp, asks, bids);
  }


  @Override
  public Observable<Ticker> getTicker(CurrencyPair currencyPair, Object... args) {
      throw new UnsupportedOperationException("Ticker not supported");
  }

  @Override
  public Observable<Trade> getTrades(CurrencyPair currencyPair, Object... args) {
      throw new UnsupportedOperationException("Trades not supported");
  }

}
