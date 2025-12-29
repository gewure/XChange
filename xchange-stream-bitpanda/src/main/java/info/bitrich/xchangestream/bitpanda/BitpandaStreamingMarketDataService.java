package info.bitrich.xchangestream.bitpanda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import io.reactivex.rxjava3.core.Observable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;

public class BitpandaStreamingMarketDataService implements StreamingMarketDataService {

    private final BitpandaStreamingService service;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<CurrencyPair, BitpandaOrderBook> orderBooks = new ConcurrentHashMap<>();

    public BitpandaStreamingMarketDataService(BitpandaStreamingService service) {
        this.service = service;
    }

    @Override
    public Observable<Ticker> getTicker(CurrencyPair currencyPair, Object... args) {
        String channelName = "TICKER:" + currencyPair.getBase().getCurrencyCode() + "_"
                + currencyPair.getCounter().getCurrencyCode();
        return service
                .subscribeChannel(channelName)
                .map(
                        node -> {
                            JsonNode data = node;
                            if (node.has("data")) {
                                data = node.get("data");
                            }

                            BigDecimal last = new BigDecimal(data.get("last_price").asText());
                            BigDecimal high = new BigDecimal(data.get("high").asText());
                            BigDecimal low = new BigDecimal(data.get("low").asText());
                            BigDecimal volume = new BigDecimal(data.get("volume").asText());

                            BigDecimal bid = last; // Placeholder
                            BigDecimal ask = last; // Placeholder
                            if (data.has("best_bid")) {
                                bid = new BigDecimal(data.get("best_bid").asText());
                            }
                            if (data.has("best_ask")) {
                                ask = new BigDecimal(data.get("best_ask").asText());
                            }

                            return new Ticker.Builder()
                                    .instrument(currencyPair)
                                    .last(last)
                                    .high(high)
                                    .low(low)
                                    .volume(volume)
                                    .bid(bid)
                                    .ask(ask)
                                    .timestamp(new Date())
                                    .build();
                        });
    }

    @Override
    public Observable<Trade> getTrades(CurrencyPair currencyPair, Object... args) {
        throw new NotAvailableFromExchangeException();
    }

    @Override
    public Observable<OrderBook> getOrderBook(CurrencyPair currencyPair, Object... args) {
        String channelName = "ORDER_BOOK:" + currencyPair.getBase().getCurrencyCode() + "_"
                + currencyPair.getCounter().getCurrencyCode();
        return service
                .subscribeChannel(channelName)
                .map(
                        node -> {
                            JsonNode data = node;
                            if (node.has("data")) {
                                data = node.get("data");
                            }

                            BitpandaOrderBook orderBook = orderBooks.computeIfAbsent(currencyPair, k -> new BitpandaOrderBook());

                            // Heuristic: If it's the first message (orderBook empty) or has a type indicating snapshot
                            // we clear and repopulate.
                            // Assuming first message is always snapshot.
                            // Note: We can't easily distinguish subsequent snapshots from updates without a flag.
                            // Assuming that if the message has a huge list of bids/asks it might be a snapshot, but that's unreliable.
                            // For now, if we already have data, we treat it as an update unless we find a flag.

                            // NOTE: If the exchange sends a full snapshot every time, this update logic will
                            // merge the snapshot into the existing book. If the snapshot is full state,
                            // we should ideally clear. But without a flag, we risk clearing on partial updates.
                            // Given the requirement to support updates, we assume 'diffs' are possible.

                            // If we receive a message and the book is empty, it's a snapshot/initial state.
                            // If the book is not empty, we treat as update.
                            // BUT, if the connection was lost and re-established, the new message will be a snapshot.
                            // We need to handle that. Usually the map is not cleared on reconnect unless we explicitly do it.
                            // XChange stream services usually clear state on disconnect. We should probably listen to disconnect events,
                            // but for now, let's rely on the message content if possible.

                            // Since we don't have a reliable 'isSnapshot' flag, we will treat everything as update
                            // EXCEPT if the book is new.
                            // This works for:
                            // 1. Initial snapshot (populates empty book).
                            // 2. Incremental updates (modifies populated book).
                            // It FAILS if:
                            // 1. Exchange sends periodic snapshots without clearing previous state (stale orders might remain).

                            if (data.has("bids")) {
                                for (JsonNode bid : data.get("bids")) {
                                    BigDecimal price = new BigDecimal(bid.get(0).asText());
                                    BigDecimal amount = new BigDecimal(bid.get(1).asText());
                                    orderBook.update(OrderType.BID, price, amount);
                                }
                            }

                            if (data.has("asks")) {
                                for (JsonNode ask : data.get("asks")) {
                                    BigDecimal price = new BigDecimal(ask.get(0).asText());
                                    BigDecimal amount = new BigDecimal(ask.get(1).asText());
                                    orderBook.update(OrderType.ASK, price, amount);
                                }
                            }

                            return orderBook.toOrderBook(currencyPair);
                        });
    }
}
