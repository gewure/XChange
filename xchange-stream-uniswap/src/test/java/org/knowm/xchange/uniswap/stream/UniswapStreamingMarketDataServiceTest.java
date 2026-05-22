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
import org.web3j.protocol.websocket.events.NotificationParams;
import org.web3j.protocol.websocket.events.LogNotification;

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
    // Data: 0x + 160 bytes (5 * 32)
    BigInteger q96 = new BigInteger("2").pow(96);
    String sqrtPriceHex = q96.toString(16);
    while (sqrtPriceHex.length() < 64) {
      sqrtPriceHex = "0" + sqrtPriceHex;
    }
    
    StringBuilder data = new StringBuilder("0x");
    for (int i = 0; i < 64; i++) data.append("0"); // amount0
    for (int i = 0; i < 64; i++) data.append("0"); // amount1
    data.append(sqrtPriceHex);
    for (int i = 0; i < 64; i++) data.append("0"); // liquidity
    for (int i = 0; i < 64; i++) data.append("0"); // tick

    org.web3j.protocol.websocket.events.Log mockLog = org.mockito.Mockito.mock(org.web3j.protocol.websocket.events.Log.class);
    org.mockito.Mockito.when(mockLog.getTransactionHash()).thenReturn("0x123");
    org.mockito.Mockito.when(mockLog.getData()).thenReturn(data.toString());

    LogNotification notification = org.mockito.Mockito.mock(LogNotification.class);
    NotificationParams params = org.mockito.Mockito.mock(NotificationParams.class);
    org.mockito.Mockito.when(params.getResult()).thenReturn(mockLog);
    org.mockito.Mockito.when(notification.getParams()).thenReturn(params);

    when(web3j.logsNotifications(any(), any())).thenReturn(io.reactivex.Flowable.just(notification));

    try {
        java.lang.reflect.Field poolTokensCache = UniswapStreamingMarketDataService.class.getDeclaredField("poolTokensCache");
        poolTokensCache.setAccessible(true);
        ((java.util.Map<String, String[]>) poolTokensCache.get(marketDataService)).put("0xpool", new String[]{"0xToken0", "0xToken1"});

        java.lang.reflect.Field tokenDecimalsCache = UniswapStreamingMarketDataService.class.getDeclaredField("tokenDecimalsCache");
        tokenDecimalsCache.setAccessible(true);
        ((java.util.Map<String, Integer>) tokenDecimalsCache.get(marketDataService)).put("0xtoken0", 18);
        ((java.util.Map<String, Integer>) tokenDecimalsCache.get(marketDataService)).put("0xtoken1", 6);

        java.lang.reflect.Field tokenSymbolsCache = UniswapStreamingMarketDataService.class.getDeclaredField("tokenSymbolsCache");
        tokenSymbolsCache.setAccessible(true);
        ((java.util.Map<String, String>) tokenSymbolsCache.get(marketDataService)).put("0xtoken0", "WETH");
        ((java.util.Map<String, String>) tokenSymbolsCache.get(marketDataService)).put("0xtoken1", "USDC");
    } catch (Exception e) {}

    UniswapInstrument instrument = new UniswapInstrument(Currency.ETH, Currency.USDC, "0xPool", 3000);
    TestObserver<Trade> observer = marketDataService.getTrades(instrument).test();

    observer.await();
    observer.assertValueCount(1);
    observer.assertValue(trade -> trade.getPrice().compareTo(new java.math.BigDecimal("1000000000000")) == 0);
  }
}
