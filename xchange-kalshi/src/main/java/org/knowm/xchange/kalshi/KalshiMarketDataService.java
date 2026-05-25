package org.knowm.xchange.kalshi;

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
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.exceptions.ExchangeSecurityException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class KalshiMarketDataService implements MarketDataService {

    private final KalshiExchange exchange;
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public KalshiMarketDataService(Exchange exchange) {
        this.exchange = (KalshiExchange) exchange;
    }

    @Override
    public OrderBook getOrderBook(CurrencyPair currencyPair, Object... args) throws IOException {
        return getOrderBook((Instrument) currencyPair, args);
    }

    @Override
    public OrderBook getOrderBook(Instrument instrument, Object... args) throws IOException {
        String ticker = null;
        if (args != null && args.length > 0 && args[0] instanceof String) {
            ticker = (String) args[0];
        } else if (instrument != null && instrument.getBase() != null && instrument.getCounter() != null) {
            ticker = instrument.getBase().getCurrencyCode() + "-" + instrument.getCounter().getCurrencyCode();
        } else {
             throw new IllegalArgumentException("Market ticker is required as the first argument or derived from Instrument.");
        }

        String path = "/markets/" + ticker + "/orderbook";
        Request.Builder requestBuilder = new Request.Builder()
                .url(exchange.getExchangeSpecification().getSslUri() + path)
                .get();

        String apiKey = exchange.getExchangeSpecification().getApiKey();
        KalshiDigest signatureCreator = exchange.getSignatureCreator();

        if (apiKey != null && signatureCreator != null) {
            String timestamp = String.valueOf(System.currentTimeMillis());
            String pathForSignature = "/trade-api/v2" + path;
            String signature = signatureCreator.sign(timestamp, "GET", pathForSignature);

            requestBuilder.addHeader("KALSHI-ACCESS-KEY", apiKey);
            requestBuilder.addHeader("KALSHI-ACCESS-SIGNATURE", signature);
            requestBuilder.addHeader("KALSHI-ACCESS-TIMESTAMP", timestamp);
        } else {
            throw new ExchangeSecurityException("Kalshi API requires authentication (apiKey and secretKey)");
        }

        Request request = requestBuilder.build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            JsonNode rootNode = mapper.readTree(response.body().string());
            JsonNode orderbookNode = rootNode.get("orderbook_fp");

            List<LimitOrder> bids = new ArrayList<>();
            List<LimitOrder> asks = new ArrayList<>();

            Date timestamp = new Date();

            if (orderbookNode != null) {
                if (orderbookNode.has("yes_dollars")) {
                    for (JsonNode bidNode : orderbookNode.get("yes_dollars")) {
                        BigDecimal price = new BigDecimal(bidNode.get(0).asText());
                        BigDecimal size = new BigDecimal(bidNode.get(1).asText());
                        bids.add(new LimitOrder(Order.OrderType.BID, size, instrument, "", timestamp, price));
                    }
                }
                if (orderbookNode.has("no_dollars")) {
                     for (JsonNode bidNode : orderbookNode.get("no_dollars")) {
                        // A bid for no at price X is an ask for yes at price (1 - X)
                        BigDecimal noPrice = new BigDecimal(bidNode.get(0).asText());
                        BigDecimal price = BigDecimal.ONE.subtract(noPrice);
                        BigDecimal size = new BigDecimal(bidNode.get(1).asText());
                        asks.add(new LimitOrder(Order.OrderType.ASK, size, instrument, "", timestamp, price));
                    }
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
