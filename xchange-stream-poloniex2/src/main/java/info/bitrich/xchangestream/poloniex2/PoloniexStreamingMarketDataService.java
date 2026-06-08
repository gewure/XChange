package info.bitrich.xchangestream.poloniex2;

import com.fasterxml.jackson.databind.JsonNode;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import io.reactivex.rxjava3.core.Observable;
import java.math.BigDecimal;
import java.util.*;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Created by Lukas Zaoralek on 10.11.17. */
public class PoloniexStreamingMarketDataService implements StreamingMarketDataService {
  private static final Logger LOG = LoggerFactory.getLogger(PoloniexStreamingMarketDataService.class);

  private final PoloniexStreamingService service;
  private volatile boolean useMockStream = false;
  private final Map<CurrencyPair, LocalOrderBook> localOrderBooks = new java.util.concurrent.ConcurrentHashMap<>();

  public void setUseMockStream(boolean useMockStream) {
    this.useMockStream = useMockStream;
  }

  private double getMockPrice(String code) {
    if (code == null) return 1.0;
    switch (code.toUpperCase()) {
      case "BTC": case "WBTC": return 95000.0;
      case "ETH": case "WETH": return 3000.0;
      case "LTC": return 80.0;
      case "BCH": return 400.0;
      case "XLM": return 0.12;
      case "XMR": return 170.0;
      case "ADA": return 0.5;
      case "ZEC": return 30.0;
      case "XRP": return 0.6;
      case "DOGE": return 0.15;
      case "LINK": return 15.0;
      case "DOT": return 7.0;
      case "SOL": return 160.0;
      case "BNB": return 580.0;
      case "TRX": return 0.12;
      case "UNI": return 8.0;
      case "USD": case "USDT": case "USDC": case "EUR": return 1.0;
      default: return 1.0;
    }
  }

  private double getBasePrice(CurrencyPair pair) {
    double base = getMockPrice(pair.getBase().getCurrencyCode());
    double counter = getMockPrice(pair.getCounter().getCurrencyCode());
    return base / counter;
  }

  private OrderBook generateMockOrderBook(CurrencyPair pair) {
    double basePrice = getBasePrice(pair);
    double randomFactor = 1.0 + (Math.random() - 0.5) * 0.002;
    double midPrice = basePrice * randomFactor;

    List<LimitOrder> asks = new java.util.ArrayList<>();
    List<LimitOrder> bids = new java.util.ArrayList<>();

    for (int i = 1; i <= 5; i++) {
      double askPrice = midPrice * (1.0 + i * 0.0005);
      double amount = 0.5 + Math.random() * 2.0;
      asks.add(new LimitOrder(
          OrderType.ASK,
          BigDecimal.valueOf(amount),
          pair,
          String.valueOf(i),
          new java.util.Date(),
          BigDecimal.valueOf(askPrice)
      ));
    }

    for (int i = 1; i <= 5; i++) {
      double bidPrice = midPrice * (1.0 - i * 0.0005);
      double amount = 0.5 + Math.random() * 2.0;
      bids.add(new LimitOrder(
          OrderType.BID,
          BigDecimal.valueOf(amount),
          pair,
          String.valueOf(i),
          new java.util.Date(),
          BigDecimal.valueOf(bidPrice)
      ));
    }

    return new OrderBook(new java.util.Date(), asks, bids);
  }

  private Ticker generateMockTicker(CurrencyPair pair) {
    double basePrice = getBasePrice(pair);
    double randomFactor = 1.0 + (Math.random() - 0.5) * 0.002;
    double midPrice = basePrice * randomFactor;
    double askPrice = midPrice * 1.0005;
    double bidPrice = midPrice * 0.9995;

    return new Ticker.Builder()
        .instrument(pair)
        .open(BigDecimal.valueOf(basePrice))
        .last(BigDecimal.valueOf(midPrice))
        .bid(BigDecimal.valueOf(bidPrice))
        .ask(BigDecimal.valueOf(askPrice))
        .high(BigDecimal.valueOf(midPrice * 1.02))
        .low(BigDecimal.valueOf(midPrice * 0.98))
        .volume(BigDecimal.valueOf(1000.0 + Math.random() * 5000.0))
        .timestamp(new java.util.Date())
        .build();
  }

  private Trade generateMockTrade(CurrencyPair pair) {
    double basePrice = getBasePrice(pair);
    double randomFactor = 1.0 + (Math.random() - 0.5) * 0.002;
    double midPrice = basePrice * randomFactor;
    OrderType type = Math.random() > 0.5 ? OrderType.BID : OrderType.ASK;

    return new Trade(
        type,
        BigDecimal.valueOf(0.1 + Math.random() * 1.5),
        pair,
        BigDecimal.valueOf(midPrice),
        new java.util.Date(),
        "mock-trade-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000),
        null,
        null
    );
  }

  public PoloniexStreamingMarketDataService(
      PoloniexStreamingService service, Map<Integer, CurrencyPair> currencyIdMap) {
    this.service = service;
  }

  @Override
  public Observable<OrderBook> getOrderBook(CurrencyPair currencyPair, Object... args) {
    if (useMockStream) {
      return Observable.interval(0, 3, java.util.concurrent.TimeUnit.SECONDS)
          .map(tick -> generateMockOrderBook(currencyPair));
    }

    String symbol = currencyPair.getBase().getCurrencyCode().toUpperCase() + "_" +
                    currencyPair.getCounter().getCurrencyCode().toUpperCase();
    String channelName = "book:" + symbol;

    LocalOrderBook localBook = localOrderBooks.computeIfAbsent(currencyPair, k -> new LocalOrderBook());

    return service.subscribeChannel(channelName)
        .map(jsonNode -> {
          if (jsonNode.has("data") && jsonNode.get("data").isArray()) {
            for (JsonNode dataObj : jsonNode.get("data")) {
              JsonNode asksNode = dataObj.get("asks");
              JsonNode bidsNode = dataObj.get("bids");
              localBook.update(asksNode, bidsNode);
            }
          } else {
            JsonNode asksNode = jsonNode.get("asks");
            JsonNode bidsNode = jsonNode.get("bids");
            localBook.update(asksNode, bidsNode);
          }
          return localBook.toOrderBook(currencyPair);
        });
  }

  @Override
  public Observable<Ticker> getTicker(CurrencyPair currencyPair, Object... args) {
    if (useMockStream) {
      return Observable.interval(0, 3, java.util.concurrent.TimeUnit.SECONDS)
          .map(tick -> generateMockTicker(currencyPair));
    }
    throw new UnsupportedOperationException("Streaming Ticker not supported for Poloniex");
  }

  @Override
  public Observable<Trade> getTrades(CurrencyPair currencyPair, Object... args) {
    if (useMockStream) {
      return Observable.interval(0, 5, java.util.concurrent.TimeUnit.SECONDS)
          .map(tick -> generateMockTrade(currencyPair));
    }
    throw new UnsupportedOperationException("Streaming Trades not supported for Poloniex");
  }

  private static class LocalOrderBook {
    private final java.util.concurrent.ConcurrentSkipListMap<BigDecimal, BigDecimal> asks =
        new java.util.concurrent.ConcurrentSkipListMap<>();
    private final java.util.concurrent.ConcurrentSkipListMap<BigDecimal, BigDecimal> bids =
        new java.util.concurrent.ConcurrentSkipListMap<>(java.util.Collections.reverseOrder());

    public synchronized void update(JsonNode asksNode, JsonNode bidsNode) {
      if (asksNode != null && asksNode.isArray()) {
        for (JsonNode entry : asksNode) {
          BigDecimal price = new BigDecimal(entry.get(0).asText());
          BigDecimal qty = new BigDecimal(entry.get(1).asText());
          if (qty.compareTo(BigDecimal.ZERO) == 0) {
            asks.remove(price);
          } else {
            asks.put(price, qty);
          }
        }
      }
      if (bidsNode != null && bidsNode.isArray()) {
        for (JsonNode entry : bidsNode) {
          BigDecimal price = new BigDecimal(entry.get(0).asText());
          BigDecimal qty = new BigDecimal(entry.get(1).asText());
          if (qty.compareTo(BigDecimal.ZERO) == 0) {
            bids.remove(price);
          } else {
            bids.put(price, qty);
          }
        }
      }
    }

    public synchronized OrderBook toOrderBook(CurrencyPair pair) {
      List<LimitOrder> askOrders = new java.util.ArrayList<>();
      for (Map.Entry<BigDecimal, BigDecimal> entry : asks.entrySet()) {
        askOrders.add(new LimitOrder(OrderType.ASK, entry.getValue(), pair, "", null, entry.getKey()));
      }
      List<LimitOrder> bidOrders = new java.util.ArrayList<>();
      for (Map.Entry<BigDecimal, BigDecimal> entry : bids.entrySet()) {
        bidOrders.add(new LimitOrder(OrderType.BID, entry.getValue(), pair, "", null, entry.getKey()));
      }
      return new OrderBook(new java.util.Date(), askOrders, bidOrders);
    }
  }
}
