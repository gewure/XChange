package org.knowm.xchange.kalshi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.instrument.Instrument;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.TreeMap;

public class KalshiStreamingMarketDataService implements StreamingMarketDataService {

    private final KalshiStreamingExchange exchange;
    private final KalshiStreamingService service;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, KalshiOrderBook> orderBooks = new ConcurrentHashMap<>();

    public KalshiStreamingMarketDataService(KalshiStreamingExchange exchange) {
        this.exchange = exchange;

        String url = exchange.getExchangeSpecification().getExchangeSpecificParametersItem("streaming_uri").toString();
        String apiKey = exchange.getExchangeSpecification().getApiKey();
        KalshiDigest signatureCreator = exchange.getSignatureCreator();

        this.service = new KalshiStreamingService(url, apiKey, signatureCreator);
    }

    public Completable connect() {
        return service.connect();
    }

    public Completable disconnect() {
        return service.disconnect();
    }

    public boolean isAlive() {
        return service.isSocketOpen();
    }

    @Override
    public Observable<OrderBook> getOrderBook(CurrencyPair currencyPair, Object... args) {
        return getOrderBook((Instrument) currencyPair, args);
    }

    @Override
    public Observable<OrderBook> getOrderBook(Instrument instrument, Object... args) {
        String ticker = null;
        if (args != null && args.length > 0 && args[0] instanceof String) {
            ticker = (String) args[0];
        } else if (instrument != null && instrument.getBase() != null && instrument.getCounter() != null) {
            ticker = instrument.getBase().getCurrencyCode() + "-" + instrument.getCounter().getCurrencyCode();
        } else {
             throw new IllegalArgumentException("Market ticker is required as the first argument or derived from Instrument.");
        }

        final String finalTicker = ticker;

        String channelName = "orderbook_delta-" + finalTicker;
        return service.subscribeChannel(channelName, finalTicker)
                .map(node -> {
                    String type = node.get("type").asText();
                    JsonNode msg = node.get("msg");

                    KalshiOrderBook localBook = orderBooks.computeIfAbsent(finalTicker, k -> new KalshiOrderBook(instrument));

                    if ("orderbook_snapshot".equals(type)) {
                        localBook.applySnapshot(msg);
                    } else if ("orderbook_delta".equals(type)) {
                        localBook.applyDelta(msg);
                    }

                    return localBook.toOrderBook();
                });
    }

    @Override
    public Observable<Ticker> getTicker(CurrencyPair currencyPair, Object... args) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Observable<Trade> getTrades(CurrencyPair currencyPair, Object... args) {
        throw new UnsupportedOperationException();
    }

    private static class KalshiOrderBook {
        private final Instrument instrument;
        // Bids: Yes side. Higher price is better. (Reverse order: highest first)
        private final TreeMap<BigDecimal, BigDecimal> bids = new TreeMap<>(java.util.Collections.reverseOrder());
        // Asks: No side. Lower price is better. (Natural order: lowest first)
        private final TreeMap<BigDecimal, BigDecimal> asks = new TreeMap<>();
        private Date lastUpdate = new Date();

        public KalshiOrderBook(Instrument instrument) {
            this.instrument = instrument;
        }

        public synchronized void applySnapshot(JsonNode msg) {
            bids.clear();
            asks.clear();

            if (msg.has("yes_dollars_fp")) {
                for (JsonNode level : msg.get("yes_dollars_fp")) {
                    BigDecimal price = new BigDecimal(level.get(0).asText());
                    BigDecimal size = new BigDecimal(level.get(1).asText());
                    if (size.compareTo(BigDecimal.ZERO) > 0) {
                        bids.put(price, size);
                    }
                }
            }
            if (msg.has("no_dollars_fp")) {
                for (JsonNode level : msg.get("no_dollars_fp")) {
                    BigDecimal noPrice = new BigDecimal(level.get(0).asText());
                    BigDecimal askPrice = BigDecimal.ONE.subtract(noPrice);
                    BigDecimal size = new BigDecimal(level.get(1).asText());
                    if (size.compareTo(BigDecimal.ZERO) > 0) {
                        asks.put(askPrice, size);
                    }
                }
            }
            lastUpdate = new Date();
        }

        public synchronized void applyDelta(JsonNode msg) {
            BigDecimal price = new BigDecimal(msg.get("price_dollars").asText());
            BigDecimal deltaSize = new BigDecimal(msg.get("delta_fp").asText());
            String side = msg.get("side").asText();

            if ("yes".equals(side)) {
                BigDecimal currentSize = bids.getOrDefault(price, BigDecimal.ZERO);
                BigDecimal newSize = currentSize.add(deltaSize);
                if (newSize.compareTo(BigDecimal.ZERO) <= 0) {
                    bids.remove(price);
                } else {
                    bids.put(price, newSize);
                }
            } else if ("no".equals(side)) {
                BigDecimal askPrice = BigDecimal.ONE.subtract(price);
                BigDecimal currentSize = asks.getOrDefault(askPrice, BigDecimal.ZERO);
                BigDecimal newSize = currentSize.add(deltaSize);
                if (newSize.compareTo(BigDecimal.ZERO) <= 0) {
                    asks.remove(askPrice);
                } else {
                    asks.put(askPrice, newSize);
                }
            }

            if (msg.has("ts_ms")) {
                lastUpdate = new Date(msg.get("ts_ms").asLong());
            } else {
                lastUpdate = new Date();
            }
        }

        public synchronized OrderBook toOrderBook() {
            List<LimitOrder> bidOrders = new ArrayList<>(bids.size());
            for (Map.Entry<BigDecimal, BigDecimal> entry : bids.entrySet()) {
                bidOrders.add(new LimitOrder(Order.OrderType.BID, entry.getValue(), instrument, "", lastUpdate, entry.getKey()));
            }

            List<LimitOrder> askOrders = new ArrayList<>(asks.size());
            for (Map.Entry<BigDecimal, BigDecimal> entry : asks.entrySet()) {
                askOrders.add(new LimitOrder(Order.OrderType.ASK, entry.getValue(), instrument, "", lastUpdate, entry.getKey()));
            }

            return new OrderBook(lastUpdate, askOrders, bidOrders);
        }
    }
}
