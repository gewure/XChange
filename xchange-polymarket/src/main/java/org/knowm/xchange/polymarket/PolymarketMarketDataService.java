package org.knowm.xchange.polymarket;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.marketdata.MarketDataService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PolymarketMarketDataService implements MarketDataService {

    private final Exchange exchange;
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public PolymarketMarketDataService(Exchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public OrderBook getOrderBook(CurrencyPair currencyPair, Object... args) throws IOException {
        return getOrderBook((Instrument) currencyPair, args);
    }

    @Override
    public OrderBook getOrderBook(Instrument instrument, Object... args) throws IOException {
        if (args == null || args.length == 0 || !(args[0] instanceof String)) {
            throw new IllegalArgumentException("Asset ID is required as the first argument.");
        }
        String assetId = (String) args[0];

        Request request = new Request.Builder()
                .url("https://clob.polymarket.com/book?token_id=" + assetId)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            JsonNode rootNode = mapper.readTree(response.body().string());
            List<LimitOrder> bids = new ArrayList<>();
            List<LimitOrder> asks = new ArrayList<>();

            Date timestamp = null;
            if (rootNode.has("timestamp")) {
                timestamp = new Date(Long.parseLong(rootNode.get("timestamp").asText()));
            }

            if (rootNode.has("bids")) {
                for (JsonNode bidNode : rootNode.get("bids")) {
                    BigDecimal price = new BigDecimal(bidNode.get("price").asText());
                    BigDecimal size = new BigDecimal(bidNode.get("size").asText());
                    bids.add(new LimitOrder(Order.OrderType.BID, size, instrument, "", timestamp, price));
                }
            }

            if (rootNode.has("asks")) {
                for (JsonNode askNode : rootNode.get("asks")) {
                    BigDecimal price = new BigDecimal(askNode.get("price").asText());
                    BigDecimal size = new BigDecimal(askNode.get("size").asText());
                    asks.add(new LimitOrder(Order.OrderType.ASK, size, instrument, "", timestamp, price));
                }
            }

            return new OrderBook(timestamp, asks, bids);
        }
    }

    @Override
    public Ticker getTicker(CurrencyPair currencyPair, Object... args) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Trades getTrades(CurrencyPair currencyPair, Object... args) throws IOException {
        throw new UnsupportedOperationException();
    }
}
