package info.bitrich.xchangestream.bitpanda;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;

public class BitpandaStreamingOrderBookUpdateTest {

    private BitpandaStreamingService streamingService;
    private BitpandaStreamingMarketDataService marketDataService;
    private ObjectMapper mapper = new ObjectMapper();

    @Before
    public void setUp() {
        streamingService = mock(BitpandaStreamingService.class);
        marketDataService = new BitpandaStreamingMarketDataService(streamingService);
    }

    @Test
    public void testOrderBookUpdates() throws Exception {
        // Snapshot
        String snapshotJson = "{"
                + "\"channel_name\": \"ORDER_BOOK\","
                + "\"instrument_code\": \"BTC_EUR\","
                + "\"bids\": [[\"10000.0\", \"1.0\"]],"
                + "\"asks\": [[\"10001.0\", \"0.5\"]]"
                + "}";
        JsonNode snapshotNode = mapper.readTree(snapshotJson);

        // Update: Add new bid
        String update1Json = "{"
                + "\"channel_name\": \"ORDER_BOOK\","
                + "\"instrument_code\": \"BTC_EUR\","
                + "\"bids\": [[\"10002.0\", \"0.2\"]]"
                + "}";
        JsonNode update1Node = mapper.readTree(update1Json);

        // Update: Update existing bid
        String update2Json = "{"
                + "\"channel_name\": \"ORDER_BOOK\","
                + "\"instrument_code\": \"BTC_EUR\","
                + "\"bids\": [[\"10000.0\", \"2.0\"]]"
                + "}";
        JsonNode update2Node = mapper.readTree(update2Json);

        // Update: Remove bid
        String update3Json = "{"
                + "\"channel_name\": \"ORDER_BOOK\","
                + "\"instrument_code\": \"BTC_EUR\","
                + "\"bids\": [[\"10002.0\", \"0.0\"]]"
                + "}";
        JsonNode update3Node = mapper.readTree(update3Json);

        when(streamingService.subscribeChannel(anyString())).thenReturn(Observable.just(snapshotNode, update1Node, update2Node, update3Node));

        TestObserver<OrderBook> observer = marketDataService.getOrderBook(CurrencyPair.BTC_EUR).test();

        observer.assertNoErrors();
        observer.assertValueCount(4);

        List<OrderBook> events = observer.values();

        // 1. Snapshot
        OrderBook ob1 = events.get(0);
        assert ob1.getBids().size() == 1;
        assert ob1.getBids().get(0).getLimitPrice().compareTo(new BigDecimal("10000.0")) == 0;
        assert ob1.getBids().get(0).getOriginalAmount().compareTo(new BigDecimal("1.0")) == 0;

        // 2. Add 10002.0
        OrderBook ob2 = events.get(1);
        assert ob2.getBids().size() == 2;
        assert ob2.getBids().get(0).getLimitPrice().compareTo(new BigDecimal("10002.0")) == 0; // sorted descending
        assert ob2.getBids().get(0).getOriginalAmount().compareTo(new BigDecimal("0.2")) == 0;
        assert ob2.getBids().get(1).getLimitPrice().compareTo(new BigDecimal("10000.0")) == 0;

        // 3. Update 10000.0 to 2.0
        OrderBook ob3 = events.get(2);
        assert ob3.getBids().size() == 2;
        assert ob3.getBids().get(0).getLimitPrice().compareTo(new BigDecimal("10002.0")) == 0;
        assert ob3.getBids().get(1).getLimitPrice().compareTo(new BigDecimal("10000.0")) == 0;
        assert ob3.getBids().get(1).getOriginalAmount().compareTo(new BigDecimal("2.0")) == 0;

        // 4. Remove 10002.0
        OrderBook ob4 = events.get(3);
        assert ob4.getBids().size() == 1;
        assert ob4.getBids().get(0).getLimitPrice().compareTo(new BigDecimal("10000.0")) == 0;
        assert ob4.getBids().get(0).getOriginalAmount().compareTo(new BigDecimal("2.0")) == 0;
    }
}
