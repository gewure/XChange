package org.knowm.xchange.uniswap.stream;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.uniswap.UniswapExchange;
import org.knowm.xchange.uniswap.dto.UniswapInstrument;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Log;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class UniswapStreamingMarketDataServiceTest {

  @Mock
  private UniswapStreamingService streamingService;
  @Mock
  private UniswapExchange exchange;
  @Mock
  private Web3j web3j;

  private UniswapStreamingMarketDataService marketDataService;

  @BeforeEach
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(streamingService.getWeb3j()).thenReturn(web3j);
    marketDataService = new UniswapStreamingMarketDataService(streamingService, exchange);
  }

  @Test
  public void testGetTrades() throws InterruptedException {
    // Mock log
    Log log = new Log();
    log.setTransactionHash("0x123");
    // Data: 0x + 160 bytes (5 * 32)
    // amount0 (0), amount1 (0), sqrtPriceX96 (2^96 -> price 1), liquidity (0), tick (0)
    // sqrtPriceX96 = 2^96. Hex: 1 followed by 24 zeros (96 bits = 24 hex chars)
    // Wait, 2^96 is 1 << 96.
    BigInteger q96 = new BigInteger("2").pow(96);
    String sqrtPriceHex = q96.toString(16);
    // Pad to 64 chars (32 bytes)
    while (sqrtPriceHex.length() < 64) {
      sqrtPriceHex = "0" + sqrtPriceHex;
    }
    
    StringBuilder data = new StringBuilder("0x");
    // amount0 (64 chars)
    for (int i = 0; i < 64; i++) data.append("0");
    // amount1 (64 chars)
    for (int i = 0; i < 64; i++) data.append("0");
    // sqrtPriceX96 (64 chars)
    data.append(sqrtPriceHex);
    // liquidity (64 chars)
    for (int i = 0; i < 64; i++) data.append("0");
    // tick (64 chars)
    for (int i = 0; i < 64; i++) data.append("0");
    
    log.setData(data.toString());

    when(web3j.ethLogFlowable(any())).thenReturn(io.reactivex.Flowable.just(log));

    UniswapInstrument instrument = new UniswapInstrument(Currency.ETH, Currency.USDC, "0xPool", 3000);
    TestObserver<Trade> observer = marketDataService.getTrades(instrument).test();

    observer.await();
    observer.assertValueCount(1);
    observer.assertValue(trade -> trade.getPrice().compareTo(BigDecimal.ONE) == 0);
  }
}
