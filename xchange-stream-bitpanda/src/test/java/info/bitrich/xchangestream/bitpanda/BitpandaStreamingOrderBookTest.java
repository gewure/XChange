package info.bitrich.xchangestream.bitpanda;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import java.math.BigDecimal;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;

public class BitpandaStreamingOrderBookTest {

    private BitpandaStreamingService streamingService;
    private BitpandaStreamingMarketDataService marketDataService;
    private ObjectMapper mapper = new ObjectMapper();

    @Before
    public void setUp() {
        streamingService = mock(BitpandaStreamingService.class);
        marketDataService = new BitpandaStreamingMarketDataService(streamingService);
    }

    @Test
    public void testGetOrderBook() throws Exception {
        String json = "{"
                + "\"channel_name\": \"ORDER_BOOK\","
                + "\"instrument_code\": \"BTC_EUR\","
                + "\"bids\": [[\"10000.0\", \"1.0\"]],"
                + "\"asks\": [[\"10001.0\", \"0.5\"]]"
                + "}";
        JsonNode node = mapper.readTree(json);

        when(streamingService.subscribeChannel(anyString())).thenReturn(Observable.just(node));

        TestObserver<OrderBook> observer = marketDataService.getOrderBook(CurrencyPair.BTC_EUR).test();

        observer.assertNoErrors();
        observer.assertValue(orderBook -> {
            return orderBook.getBids().size() == 1
                    && orderBook.getAsks().size() == 1
                    && orderBook.getBids().get(0).getLimitPrice().compareTo(new BigDecimal("10000.0")) == 0
                    && orderBook.getBids().get(0).getOriginalAmount().compareTo(new BigDecimal("1.0")) == 0
                    && orderBook.getAsks().get(0).getLimitPrice().compareTo(new BigDecimal("10001.0")) == 0
                    && orderBook.getAsks().get(0).getOriginalAmount().compareTo(new BigDecimal("0.5")) == 0;
        });
    }
}
