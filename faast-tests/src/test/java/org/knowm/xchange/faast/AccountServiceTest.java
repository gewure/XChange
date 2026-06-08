package org.knowm.xchange.faast;

import org.junit.jupiter.api.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.polymarket.PolymarketAccountService;
import org.knowm.xchange.kalshi.KalshiAccountService;
import org.knowm.xchange.kalshi.KalshiExchange;

import java.io.IOException;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class AccountServiceTest {

  @Test
  public void testPolymarketAccountServiceSimulated() throws IOException {
    Exchange mockExchange = mock(Exchange.class);
    ExchangeSpecification spec = new ExchangeSpecification(org.knowm.xchange.polymarket.PolymarketExchange.class);
    // Credentials left null to trigger simulated path
    when(mockExchange.getExchangeSpecification()).thenReturn(spec);

    PolymarketAccountService service = new PolymarketAccountService(mockExchange);
    AccountInfo info = service.getAccountInfo();

    assertThat(info).isNotNull();
    assertThat(info.getUsername()).isEqualTo("simulated_polymarket");
    Balance usdBalance = info.getWallet().getBalance(Currency.USDC);
    assertThat(usdBalance).isNotNull();
    assertThat(usdBalance.getTotal()).isEqualTo(new BigDecimal("10000.00"));
  }

  @Test
  public void testKalshiAccountServiceSimulated() throws IOException {
    KalshiExchange mockExchange = mock(KalshiExchange.class);
    ExchangeSpecification spec = new ExchangeSpecification(org.knowm.xchange.kalshi.KalshiExchange.class);
    when(mockExchange.getExchangeSpecification()).thenReturn(spec);
    when(mockExchange.getSignatureCreator()).thenReturn(null);

    KalshiAccountService service = new KalshiAccountService(mockExchange);
    AccountInfo info = service.getAccountInfo();

    assertThat(info).isNotNull();
    assertThat(info.getUsername()).isEqualTo("simulated_kalshi");
    Balance usdBalance = info.getWallet().getBalance(Currency.USD);
    assertThat(usdBalance).isNotNull();
    assertThat(usdBalance.getTotal()).isEqualTo(new BigDecimal("5000.00"));
  }

  @Test
  public void testUniswapAccountServiceSimulated() throws IOException {
    org.knowm.xchange.uniswap.UniswapExchange mockExchange = mock(org.knowm.xchange.uniswap.UniswapExchange.class);
    ExchangeSpecification spec = new ExchangeSpecification(org.knowm.xchange.uniswap.UniswapExchange.class);
    spec.setApiKey("0xdf51ed4ad0857733f4c74402cbc034c3367d8f06");
    spec.setSslUri("http://mock-rpc.localhost");
    when(mockExchange.getExchangeSpecification()).thenReturn(spec);

    org.knowm.xchange.uniswap.service.UniswapAccountService service = new org.knowm.xchange.uniswap.service.UniswapAccountService(mockExchange);
    AccountInfo info = service.getAccountInfo();

    assertThat(info).isNotNull();
    assertThat(info.getUsername()).isEqualTo("0xdf51ed4ad0857733f4c74402cbc034c3367d8f06");
    Balance ethBalance = info.getWallet().getBalance(Currency.ETH);
    assertThat(ethBalance).isNotNull();
    assertThat(ethBalance.getTotal()).isEqualTo(new BigDecimal("100"));
  }
}
