package org.knowm.xchange.polymarket;

import org.knowm.xchange.BaseExchange;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;

public class PolymarketExchange extends BaseExchange implements Exchange {

  @Override
  protected void initServices() {
      this.marketDataService = new PolymarketMarketDataService(this);
  }

  @Override
  public ExchangeSpecification getDefaultExchangeSpecification() {
    ExchangeSpecification exchangeSpecification = new ExchangeSpecification(this.getClass());
    exchangeSpecification.setSslUri("https://clob.polymarket.com");
    exchangeSpecification.setHost("clob.polymarket.com");
    exchangeSpecification.setPort(80);
    exchangeSpecification.setExchangeName("Polymarket");
    exchangeSpecification.setExchangeDescription("Polymarket is a decentralized information markets platform.");
    return exchangeSpecification;
  }
}
