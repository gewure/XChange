package org.knowm.xchange.bitpanda.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BitpandaTrade {
    @JsonProperty("type")
    private String type;

    @JsonProperty("attributes")
    private BitpandaTradeAttributes attributes;

    @JsonProperty("id")
    private String id;

    public String getType() {
        return type;
    }

    public BitpandaTradeAttributes getAttributes() {
        return attributes;
    }

    public String getId() {
        return id;
    }
}
