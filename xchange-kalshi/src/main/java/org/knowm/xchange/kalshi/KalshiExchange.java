package org.knowm.xchange.kalshi;

import org.knowm.xchange.BaseExchange;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;

public class KalshiExchange extends BaseExchange implements Exchange {

  protected KalshiDigest signatureCreator;

  @Override
  protected void initServices() {
      if (this.exchangeSpecification.getSecretKey() != null) {
          this.signatureCreator = KalshiDigest.createInstance(this.exchangeSpecification.getSecretKey());
      }
      this.marketDataService = new KalshiMarketDataService(this);
      this.accountService = new KalshiAccountService(this);
      this.tradeService = new KalshiTradeService(this);
  }

  @Override
  public ExchangeSpecification getDefaultExchangeSpecification() {
    ExchangeSpecification exchangeSpecification = new ExchangeSpecification(this.getClass());
    exchangeSpecification.setSslUri("https://external-api.kalshi.com/trade-api/v2");
    exchangeSpecification.setHost("external-api.kalshi.com");
    exchangeSpecification.setPort(443);
    exchangeSpecification.setExchangeName("Kalshi");
    exchangeSpecification.setExchangeDescription("Kalshi is a financial exchange that allows people to trade on the outcome of events.");
    return exchangeSpecification;
  }

  public KalshiDigest getSignatureCreator() {
      return signatureCreator;
  }
}
