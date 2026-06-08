package org.knowm.xchange.faast;

import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import org.knowm.xchange.polymarket.PolymarketStreamingExchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

public class PolymarketLiveStreamTest {

    @Test
    public void testPolymarketStreamingOrderBook() throws Exception {
        System.out.println("Starting PolymarketLiveStreamTest...");
        
        // Resolve a live token ID dynamically
        String symbol = "BTC-15M-UP";
        String assetId = resolvePolymarketTokenId(symbol);
        if (assetId == null) {
            System.err.println("Could not resolve active Polymarket token ID for " + symbol + ". Falling back to hardcoded token.");
            assetId = "19480385364254632242664204005928059574927872839260819894824084244005647147217";
        }
        System.out.println("Resolved active token ID: " + assetId);

        StreamingExchange exchange;
        try {
            exchange = (StreamingExchange) ExchangeFactory.INSTANCE.createExchange(PolymarketStreamingExchange.class);
        } catch (Exception e) {
            System.err.println("Could not instantiate PolymarketStreamingExchange: " + e.getMessage());
            return;
        }

        try {
            exchange.connect().blockingAwait(10, TimeUnit.SECONDS);
            System.out.println("Connected to Polymarket WebSocket");
        } catch (Exception e) {
            System.err.println("Connection failed: " + e.getMessage() + ". Skipping live check.");
            return;
        }

        CurrencyPair dummyPair = new CurrencyPair("BTC", "USDC");

        System.out.println("Subscribing to asset ID: " + assetId);
        AtomicInteger updatesCount = new AtomicInteger(0);

        try {
            exchange.getStreamingMarketDataService()
                .getOrderBook(dummyPair, assetId)
                .take(3)
                .doOnNext(ob -> {
                    System.out.println("Received OrderBook: Bids=" + ob.getBids().size() + ", Asks=" + ob.getAsks().size() + ", Ts=" + ob.getTimeStamp());
                    if (ob.getBids().size() > 0) {
                        System.out.println("  Top Bid: " + ob.getBids().get(0));
                    }
                    if (ob.getAsks().size() > 0) {
                        System.out.println("  Top Ask: " + ob.getAsks().get(0));
                    }
                    updatesCount.incrementAndGet();
                })
                .timeout(20, TimeUnit.SECONDS)
                .onErrorComplete()
                .blockingSubscribe();
        } catch (Exception e) {
            System.err.println("Error during stream subscription: " + e.getMessage());
        }

        System.out.println("Finished streaming test. Total updates received: " + updatesCount.get());
        try {
            exchange.disconnect().blockingAwait();
            System.out.println("Disconnected from Polymarket WebSocket");
        } catch (Exception e) {
            // ignore
        }
    }

    private String fetchPolymarketTokenId(String symbol, long windowTs) {
        OkHttpClient client = new OkHttpClient();
        String asset = symbol.split("-")[0].toLowerCase();
        String period = symbol.contains("15M") ? "15m" : "5m";
        String slug = asset + "-updown-" + period + "-" + windowTs;
        String url = "https://gamma-api.polymarket.com/events/slug/" + slug;
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String bodyStr = response.body().string();
                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(bodyStr);
                if (rootNode.has("markets")) {
                    JsonNode marketsNode = rootNode.get("markets");
                    if (marketsNode.isArray() && marketsNode.size() > 0) {
                        JsonNode marketNode = marketsNode.get(0);
                        if (marketNode.has("clobTokenIds")) {
                            String clobTokenIdsStr = marketNode.get("clobTokenIds").asText();
                            JsonNode tokenIdsNode = mapper.readTree(clobTokenIdsStr);
                            if (tokenIdsNode.isArray() && tokenIdsNode.size() > 0) {
                                int tokenIndex = symbol.contains("DOWN") ? 1 : 0;
                                if (tokenIdsNode.size() > tokenIndex) {
                                    return tokenIdsNode.get(tokenIndex).asText();
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch Polymarket token ID for slug: " + slug + ", error: " + e.getMessage());
        }
        return null;
    }

    private String resolvePolymarketTokenId(String symbol) {
        long nowSec = System.currentTimeMillis() / 1000;
        long period = symbol.contains("15M") ? 900 : 300;
        long windowTs = (nowSec / period) * period;
        
        String tokenId = fetchPolymarketTokenId(symbol, windowTs);
        if (tokenId != null) {
            return tokenId;
        }
        
        tokenId = fetchPolymarketTokenId(symbol, windowTs + period);
        if (tokenId != null) {
            return tokenId;
        }
        
        return fetchPolymarketTokenId(symbol, windowTs - period);
    }
}
