package org.knowm.xchange.bitpanda;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;

public class BitpandaAdaptersTest {

    @Test
    public void testAdaptTicker() {
        Map<String, Map<String, String>> tickerMap = new HashMap<>();
        Map<String, String> btcPrices = new HashMap<>();
        btcPrices.put("EUR", "12345.67");
        tickerMap.put("BTC", btcPrices);

        Ticker ticker = BitpandaAdapters.adaptTicker(tickerMap, CurrencyPair.BTC_EUR);

        assertThat(ticker).isNotNull();
        assertThat(ticker.getInstrument()).isEqualTo(CurrencyPair.BTC_EUR);
        assertThat(ticker.getLast()).isEqualTo(new BigDecimal("12345.67"));
        assertThat(ticker.getBid()).isEqualTo(new BigDecimal("12345.67"));
        assertThat(ticker.getAsk()).isEqualTo(new BigDecimal("12345.67"));
    }
}
