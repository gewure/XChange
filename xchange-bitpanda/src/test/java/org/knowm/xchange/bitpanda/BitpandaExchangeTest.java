package org.knowm.xchange.bitpanda;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;

public class BitpandaExchangeTest {

  @Test
  public void shouldCreateExchange() {
    Exchange exchange = ExchangeFactory.INSTANCE.createExchange(BitpandaExchange.class);
    assertThat(exchange).isNotNull();
    assertThat(exchange.getDefaultExchangeSpecification().getExchangeName()).isEqualTo("Bitpanda");
    assertThat(exchange.getMarketDataService()).isNotNull();
    assertThat(exchange.getTradeService()).isNotNull();
    assertThat(exchange.getAccountService()).isNotNull();
  }

  @Test
  public void shouldHaveCorrectDefaultSpecification() {
    BitpandaExchange exchange = new BitpandaExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    assertThat(spec.getSslUri()).isEqualTo("https://api.bitpanda.com");
    assertThat(spec.getHost()).isEqualTo("api.bitpanda.com");
    assertThat(spec.getPort()).isEqualTo(80);
    assertThat(spec.getExchangeName()).isEqualTo("Bitpanda");
  }
}
