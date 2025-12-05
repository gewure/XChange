package org.knowm.xchange.bitpanda.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Date;

public class BitpandaTime {
    @JsonProperty("date_iso8601")
    private Date dateIso8601;

    @JsonProperty("unix")
    private String unix;

    public Date getDateIso8601() {
        return dateIso8601;
    }

    public String getUnix() {
        return unix;
    }
}
