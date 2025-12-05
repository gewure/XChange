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
import org.knowm.xchange.dto.marketdata.Ticker;

public class BitpandaStreamingMarketDataServiceTest {

    private BitpandaStreamingService streamingService;
    private BitpandaStreamingMarketDataService marketDataService;
    private ObjectMapper mapper = new ObjectMapper();

    @Before
    public void setUp() {
        streamingService = mock(BitpandaStreamingService.class);
        marketDataService = new BitpandaStreamingMarketDataService(streamingService);
    }

    @Test
    public void testGetTicker() throws Exception {
        String json = "{"
                + "\"channel_name\": \"TICKER\","
                + "\"instrument_code\": \"BTC_EUR\","
                + "\"last_price\": \"10000.0\","
                + "\"high\": \"10100.0\","
                + "\"low\": \"9900.0\","
                + "\"volume\": \"5.0\","
                + "\"best_bid\": \"9999.0\","
                + "\"best_ask\": \"10001.0\""
                + "}";
        JsonNode node = mapper.readTree(json);

        when(streamingService.subscribeChannel(anyString())).thenReturn(Observable.just(node));

        TestObserver<Ticker> observer = marketDataService.getTicker(CurrencyPair.BTC_EUR).test();

        observer.assertNoErrors();
        observer.assertValue(ticker -> {
            return ticker.getInstrument().equals(CurrencyPair.BTC_EUR)
                    && ticker.getLast().compareTo(new BigDecimal("10000.0")) == 0
                    && ticker.getHigh().compareTo(new BigDecimal("10100.0")) == 0
                    && ticker.getLow().compareTo(new BigDecimal("9900.0")) == 0
                    && ticker.getVolume().compareTo(new BigDecimal("5.0")) == 0
                    && ticker.getBid().compareTo(new BigDecimal("9999.0")) == 0
                    && ticker.getAsk().compareTo(new BigDecimal("10001.0")) == 0;
        });
    }
}
