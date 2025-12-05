package org.knowm.xchange.bitpanda.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BitpandaAsset {
    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("type")
    private String type;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getType() {
        return type;
    }
}
