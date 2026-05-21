package org.knowm.xchange.uniswap.stream;

import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.uniswap.dto.UniswapInstrument;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class UniswapStreamingMarketDataServiceIntegrationTest {

  @Test
  public void testLiveMainnetQuoteAndStream() throws Exception {
    UniswapStreamingExchange exchange = new UniswapStreamingExchange();
    org.knowm.xchange.ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setExchangeSpecificParametersItem("RpcUri", "https://ethereum-rpc.publicnode.com");
    spec.setExchangeSpecificParametersItem("streaming_uri", "wss://ethereum-rpc.publicnode.com");
    exchange.applySpecification(spec);
    exchange.remoteInit();

    // 1. Find quote dynamically using UniswapMarketDataService
    UniswapInstrument instrument = new UniswapInstrument(Currency.ETH, Currency.USDT, "0x11b815efB8f581194ae79006d24E0d814B7697F6", 3000);
    Ticker ticker = exchange.getMarketDataService().getTicker(instrument);
    assertThat(ticker).isNotNull();
    assertThat(ticker.getLast()).isGreaterThan(BigDecimal.ZERO);
    System.out.println("Live Uniswap ETH/USDT Ticker Price: " + ticker.getLast());

    // 2. Connect to the stream and verify socket handshake
    exchange.connect().blockingAwait();
    assertThat(exchange.isAlive()).isTrue();
    assertThat(exchange.getStreamingMarketDataService()).isNotNull();

    // Try to receive a stream message (wait up to 10 seconds, but do not fail if block time/swap frequency is slow)
    CountDownLatch latch = new CountDownLatch(1);
    io.reactivex.rxjava3.disposables.Disposable sub = exchange.getStreamingMarketDataService()
        .getTrades(instrument)
        .subscribe(trade -> {
            System.out.println("Live Uniswap Trade Event received: " + trade);
            latch.countDown();
        }, err -> {
            System.err.println("Stream error: " + err.getMessage());
        });

    boolean received = latch.await(10, TimeUnit.SECONDS);
    sub.dispose();
    exchange.disconnect().blockingAwait();

    if (received) {
        System.out.println("Successfully streamed Uniswap V3 events!");
    } else {
        System.out.println("Stream initialized successfully, but no trade occurred in the last 10 seconds.");
    }
  }
}
