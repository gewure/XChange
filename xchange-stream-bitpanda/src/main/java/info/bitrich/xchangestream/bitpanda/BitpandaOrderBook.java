package info.bitrich.xchangestream.bitpanda;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;

public class BitpandaOrderBook {

    private final TreeMap<BigDecimal, BigDecimal> bids = new TreeMap<>(Collections.reverseOrder());
    private final TreeMap<BigDecimal, BigDecimal> asks = new TreeMap<>();

    public void update(OrderType type, BigDecimal price, BigDecimal amount) {
        TreeMap<BigDecimal, BigDecimal> book = type == OrderType.BID ? bids : asks;
        if (amount.signum() == 0) {
            book.remove(price);
        } else {
            book.put(price, amount);
        }
    }

    public void clear() {
        bids.clear();
        asks.clear();
    }

    public OrderBook toOrderBook(CurrencyPair pair) {
        List<LimitOrder> orderBids = new ArrayList<>();
        List<LimitOrder> orderAsks = new ArrayList<>();

        for (Map.Entry<BigDecimal, BigDecimal> entry : bids.entrySet()) {
            orderBids.add(new LimitOrder(OrderType.BID, entry.getValue(), pair, null, null, entry.getKey()));
        }

        for (Map.Entry<BigDecimal, BigDecimal> entry : asks.entrySet()) {
            orderAsks.add(new LimitOrder(OrderType.ASK, entry.getValue(), pair, null, null, entry.getKey()));
        }

        return new OrderBook(new Date(), orderAsks, orderBids);
    }
}
