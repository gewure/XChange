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

      return streamingService.subscribeChannel("market", assetId)
          .map(jsonNode -> {
               if (jsonNode.isArray() && jsonNode.size() > 0) {
                    JsonNode node = jsonNode.get(0);
                    if (node.has("bids") && node.has("asks")) {
                         return parseOrderBook(node, instrument);
                    }
               }

               if (jsonNode.has("event_type") && "book".equals(jsonNode.get("event_type").asText())) {
                   return parseOrderBook(jsonNode, instrument);
               } else if (jsonNode.has("event_type") && "price_change".equals(jsonNode.get("event_type").asText())) {
                   // return a minimal orderbook for updates
                   return parsePriceChange(jsonNode, instrument, assetId);
               }
               // Skip if not a book update
               return new OrderBook(null, new ArrayList<>(), new ArrayList<>());
          })
          .filter(ob -> ob.getBids().size() > 0 || ob.getAsks().size() > 0);
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
