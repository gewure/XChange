package org.knowm.xchange.bitpanda;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.knowm.xchange.bitpanda.dto.trade.BitpandaTrade;
import org.knowm.xchange.bitpanda.dto.trade.BitpandaTradeAttributes;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades.TradeSortType;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;

public class BitpandaAdapters {

    private BitpandaAdapters() {
    }

    public static Ticker adaptTicker(
            Map<String, Map<String, String>> bitpandaTicker, CurrencyPair currencyPair) {
        String base = currencyPair.getBase().getCurrencyCode();
        String counter = currencyPair.getCounter().getCurrencyCode();

        if (bitpandaTicker.containsKey(base) && bitpandaTicker.get(base).containsKey(counter)) {
            String priceString = bitpandaTicker.get(base).get(counter);
            BigDecimal price = new BigDecimal(priceString);

            return new Ticker.Builder()
                    .instrument(currencyPair)
                    .last(price)
                    .bid(price) // Assuming spread is negligible or using last as best guess
                    .ask(price)
                    .timestamp(new Date()) // API doesn't return timestamp
                    .build();
        }
        return null;
    }

    public static UserTrades adaptTradeHistory(
            List<BitpandaTrade> bitpandaTrades, Map<String, Currency> assetIdMap) {
        List<UserTrade> trades = new ArrayList<>();
        for (BitpandaTrade bitpandaTrade : bitpandaTrades) {
            BitpandaTradeAttributes attributes = bitpandaTrade.getAttributes();

            Currency base = assetIdMap.get(attributes.getCryptocoinId());
            Currency counter = assetIdMap.get(attributes.getFiatId());

            if (base == null || counter == null) {
                // Skip if currency mapping is missing
                continue;
            }

            CurrencyPair currencyPair = new CurrencyPair(base, counter);
            OrderType type = attributes.getType().equals("buy") ? OrderType.BID : OrderType.ASK;
            BigDecimal originalAmount = attributes.getAmountCryptocoin();
            BigDecimal price = attributes.getPrice();
            Date timestamp = attributes.getTime().getDateIso8601();
            String id = bitpandaTrade.getId();

            trades.add(UserTrade.builder()
                    .type(type)
                    .originalAmount(originalAmount)
                    .instrument(currencyPair)
                    .price(price)
                    .timestamp(timestamp)
                    .id(id)
                    .orderId(id) // Using trade ID as order ID for now
                    .build());
        }
        return new UserTrades(trades, TradeSortType.SortByTimestamp);
    }
}
