package org.knowm.xchange.uniswap;

import org.junit.jupiter.api.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.uniswap.dto.UniswapInstrument;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class UniswapIntegrationTest {

  @Test
  public void testMarketData() throws IOException {
    org.knowm.xchange.ExchangeSpecification spec = new UniswapExchangeSpecification();
    spec.setExchangeSpecificParametersItem("RpcUri", "http://mock-rpc.localhost");
    Exchange exchange = ExchangeFactory.INSTANCE.createExchange(spec);
    exchange.remoteInit();
    
    Map<Instrument, org.knowm.xchange.dto.meta.InstrumentMetaData> instruments = exchange.getExchangeMetaData().getInstruments();
    assertThat(instruments).isNotEmpty();
    
    Instrument instrument = instruments.keySet().iterator().next();
    assertThat(instrument).isInstanceOf(UniswapInstrument.class);
    
    System.out.println("Testing with instrument: " + instrument);
    
    Ticker ticker = exchange.getMarketDataService().getTicker(instrument);
    assertThat(ticker).isNotNull();
    System.out.println("Ticker: " + ticker);
    
    Trades trades = exchange.getMarketDataService().getTrades(instrument);
    assertThat(trades.getTrades()).isNotEmpty();
    System.out.println("Trades: " + trades.getTrades().size());
  }
}
