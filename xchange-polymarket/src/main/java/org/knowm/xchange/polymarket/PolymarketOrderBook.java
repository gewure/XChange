package org.knowm.xchange.polymarket;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public class PolymarketOrderBook {

    public final String market;
    public final String asset_id;
    public final String timestamp;
    public final String hash;
    public final List<Level> bids;
    public final List<Level> asks;

    public PolymarketOrderBook(
        @JsonProperty("market") String market,
        @JsonProperty("asset_id") String asset_id,
        @JsonProperty("timestamp") String timestamp,
        @JsonProperty("hash") String hash,
        @JsonProperty("bids") List<Level> bids,
        @JsonProperty("asks") List<Level> asks
    ) {
        this.market = market;
        this.asset_id = asset_id;
        this.timestamp = timestamp;
        this.hash = hash;
        this.bids = bids;
        this.asks = asks;
    }

    public static class Level {
        public final BigDecimal price;
        public final BigDecimal size;

        public Level(
            @JsonProperty("price") BigDecimal price,
            @JsonProperty("size") BigDecimal size
        ) {
            this.price = price;
            this.size = size;
        }
    }
}
