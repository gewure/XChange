package org.knowm.xchange.uniswap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.uniswap.UniswapExchange;
import org.knowm.xchange.uniswap.UniswapExchangeSpecification;
import org.knowm.xchange.uniswap.dto.UniswapInstrument;

public class UniswapTradeServiceTest {

  private UniswapExchange exchange;
  private UniswapOnChainClient onChainClient;
  private UniswapTradeService tradeService;

  @Before
  public void setUp() {
    exchange = mock(UniswapExchange.class);
    onChainClient = mock(UniswapOnChainClient.class);
    UniswapExchangeSpecification spec = mock(UniswapExchangeSpecification.class);
    when(exchange.getExchangeSpecification()).thenReturn(spec);
    // Mock private key and other specs
    when(spec.getExchangeSpecificParametersItem("private_key")).thenReturn("0x1234567890123456789012345678901234567890123456789012345678901234");

    tradeService = new UniswapTradeService(exchange, onChainClient);
  }

  @Test
  public void testPlaceMarketOrder_Bid() throws IOException {
    // Setup Instrument
    UniswapInstrument instrument = new UniswapInstrument(Currency.ETH, Currency.USDT, "0xPoolAddress", 3000);

    // Mock OnChainClient calls
    when(onChainClient.getToken0("0xPoolAddress")).thenReturn("0xToken0"); // ETH
    when(onChainClient.getToken1("0xPoolAddress")).thenReturn("0xToken1"); // USDT
    when(onChainClient.getSymbol("0xToken0")).thenReturn("WETH");
    when(onChainClient.getSymbol("0xToken1")).thenReturn("USDT");

    // For BID: Buy ETH (Base) with USDT (Quote)
    // baseIsToken0 calculation: Base is ETH. Token0 is WETH. Symbol0 is WETH. Matches.
    // baseIsToken0 = true.
    // BID -> tokenIn = token1 (USDT), tokenOut = token0 (WETH).

    String tokenIn = "0xToken1";
    String tokenOut = "0xToken0";

    when(onChainClient.getDecimals(tokenIn)).thenReturn(6); // USDT decimals
    when(onChainClient.getDecimals(tokenOut)).thenReturn(18); // WETH decimals

    // Mock Allowance
    when(onChainClient.getAllowance(eq(tokenIn), any(), any())).thenReturn(new BigInteger("1000000000000")); // Sufficient allowance

    // Mock Swap return
    when(onChainClient.swapExactInputSingle(any(), eq(tokenIn), eq(tokenOut), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn("0xTxHash");

    // Mock SqrtPriceX96 for calculation (optional but prevents NPE if used)
    when(onChainClient.getSqrtPriceX96("0xPoolAddress")).thenReturn(new BigInteger("79228162514264337593543950336")); // 1:1 roughly

    // Create Market Order
    // "Buy 1 ETH" -> In XChange MarketOrder usually defines amount of Base.
    // However, the implementation assumes amount is Input Amount (Spending USDT).
    // Let's assume user wants to spend 1000 USDT.
    // Original Amount = 1000.
    MarketOrder marketOrder = new MarketOrder(Order.OrderType.BID, new BigDecimal("1000"), instrument, "id", new Date());

    String txHash = tradeService.placeMarketOrder(marketOrder);

    assertThat(txHash).isEqualTo("0xTxHash");

    // Verify getDecimals was called for tokenIn (USDT)
    verify(onChainClient).getDecimals(tokenIn);

    // Verify amountIn calculation: 1000 * 10^6 = 1,000,000,000
    BigInteger expectedAmountIn = new BigInteger("1000000000");
    verify(onChainClient).swapExactInputSingle(
        any(),
        eq(tokenIn),
        eq(tokenOut),
        any(),
        any(),
        any(),
        eq(expectedAmountIn),
        any(),
        any(),
        any()
    );
  }
}
