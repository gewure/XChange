package info.bitrich.xchangestream.bitpanda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import io.reactivex.rxjava3.core.Observable;
import java.math.BigDecimal;
import java.util.Date;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;

public class BitpandaStreamingMarketDataService implements StreamingMarketDataService {

    private final BitpandaStreamingService service;
    private final ObjectMapper mapper = new ObjectMapper();

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
                            // Depending on the structure, we might need to navigate to "data" field
                            JsonNode data = node;
                            if (node.has("data")) {
                                data = node.get("data");
                            }

                            BigDecimal last = new BigDecimal(data.get("last_price").asText());
                            BigDecimal high = new BigDecimal(data.get("high").asText());
                            BigDecimal low = new BigDecimal(data.get("low").asText());
                            BigDecimal volume = new BigDecimal(data.get("base_volume").asText());

                            BigDecimal bid = last; // Placeholder
                            BigDecimal ask = last; // Placeholder
                            if (data.has("highest_bid")) {
                                bid = new BigDecimal(data.get("highest_bid").asText());
                            }
                            if (data.has("lowest_ask")) {
                                ask = new BigDecimal(data.get("lowest_ask").asText());
                            }

                            return new Ticker.Builder()
                                    .instrument(currencyPair)
                                    .last(last)
                                    .high(high)
                                    .low(low)
                                    .volume(volume)
                                    .bid(bid)
                                    .ask(ask)
                                    .timestamp(new Date()) // Timestamp might be in message
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
                            // Assuming structure:
                            // {
                            // "channel_name": "ORDER_BOOK",
                            // "instrument_code": "BTC_EUR",
                            // "bids": [["10000.0", "1.0"], ...],
                            // "asks": [["10001.0", "0.5"], ...]
                            // }

                            // Or maybe it's a snapshot vs update.
                            // For simplicity, assuming snapshot or full book for now.

                            JsonNode data = node;
                            if (node.has("data")) {
                                data = node.get("data");
                            }

                            // TODO: Handle updates vs snapshots if necessary.
                            // XChange stream usually expects OrderBook objects which are snapshots.
                            // If the exchange sends diffs, we need to maintain a local order book.
                            // For this implementation, we'll assume we get enough data to build an
                            // OrderBook
                            // or that the user handles diffs if they are raw.
                            // But standard XChange behavior is to return OrderBook.

                            // Let's assume the message contains "bids" and "asks" arrays.

                            java.util.List<org.knowm.xchange.dto.trade.LimitOrder> bids = new java.util.ArrayList<>();
                            java.util.List<org.knowm.xchange.dto.trade.LimitOrder> asks = new java.util.ArrayList<>();

                            if (data.has("bids")) {
                                for (JsonNode bid : data.get("bids")) {
                                    bids.add(new org.knowm.xchange.dto.trade.LimitOrder(
                                            org.knowm.xchange.dto.Order.OrderType.BID,
                                            new BigDecimal(bid.get(1).asText()),
                                            currencyPair,
                                            null,
                                            null,
                                            new BigDecimal(bid.get(0).asText())));
                                }
                            }

                            if (data.has("asks")) {
                                for (JsonNode ask : data.get("asks")) {
                                    asks.add(new org.knowm.xchange.dto.trade.LimitOrder(
                                            org.knowm.xchange.dto.Order.OrderType.ASK,
                                            new BigDecimal(ask.get(1).asText()),
                                            currencyPair,
                                            null,
                                            null,
                                            new BigDecimal(ask.get(0).asText())));
                                }
                            }

                            return new OrderBook(new Date(), asks, bids);
                        });
    }
}
