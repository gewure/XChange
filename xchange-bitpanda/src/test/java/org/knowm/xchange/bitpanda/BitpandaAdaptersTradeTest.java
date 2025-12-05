package org.knowm.xchange.bitpanda;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.knowm.xchange.bitpanda.dto.trade.BitpandaTrade;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;

public class BitpandaAdaptersTradeTest {

    @Test
    public void testAdaptTradeHistory() throws IOException {
        // Mock asset map
        Map<String, Currency> assetIdMap = new HashMap<>();
        assetIdMap.put("1", Currency.BTC);
        assetIdMap.put("2", Currency.EUR);

        // Mock BitpandaTrade using JSON deserialization for simplicity
        String json = "{"
                + "\"type\": \"trade\","
                + "\"attributes\": {"
                + "  \"status\": \"finished\","
                + "  \"type\": \"buy\","
                + "  \"cryptocoin_id\": \"1\","
                + "  \"fiat_id\": \"2\","
                + "  \"amount_fiat\": \"100.00\","
                + "  \"amount_cryptocoin\": \"0.01\","
                + "  \"fiat_to_eur_rate\": \"1.0\","
                + "  \"wallet_id\": \"w1\","
                + "  \"fiat_wallet_id\": \"fw1\","
                + "  \"payment_option_id\": \"p1\","
                + "  \"time\": { \"date_iso8601\": \"2023-01-01T12:00:00+00:00\", \"unix\": \"1672574400\" },"
                + "  \"price\": \"10000.00\","
                + "  \"is_swap\": false"
                + "},"
                + "\"id\": \"t1\""
                + "}";

        ObjectMapper mapper = new ObjectMapper();
        BitpandaTrade trade = mapper.readValue(json, BitpandaTrade.class);
        List<BitpandaTrade> trades = Collections.singletonList(trade);

        UserTrades userTrades = BitpandaAdapters.adaptTradeHistory(trades, assetIdMap);

        assertThat(userTrades.getUserTrades()).hasSize(1);
        UserTrade userTrade = userTrades.getUserTrades().get(0);
        assertThat(userTrade.getType()).isEqualTo(OrderType.BID);
        assertThat(userTrade.getOriginalAmount()).isEqualByComparingTo("0.01");
        assertThat(userTrade.getInstrument()).isEqualTo(CurrencyPair.BTC_EUR);
        assertThat(userTrade.getPrice()).isEqualByComparingTo("10000.00");
        assertThat(userTrade.getId()).isEqualTo("t1");
        assertThat(userTrade.getOrderId()).isEqualTo("t1");
    }
}
