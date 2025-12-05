package org.knowm.xchange.bitpanda;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.knowm.xchange.BaseExchange;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bitpanda.dto.marketdata.BitpandaAsset;
import org.knowm.xchange.bitpanda.service.BitpandaAccountService;
import org.knowm.xchange.bitpanda.service.BitpandaMarketDataService;
import org.knowm.xchange.bitpanda.service.BitpandaTradeService;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.utils.nonce.CurrentTimeIncrementalNonceFactory;
import si.mazi.rescu.SynchronizedValueFactory;

public class BitpandaExchange extends BaseExchange implements Exchange {

  private SynchronizedValueFactory<Long> incrementalNonceFactory = new CurrentTimeIncrementalNonceFactory(
      TimeUnit.MILLISECONDS);

  private final Map<String, Currency> assetIdMap = new HashMap<>();

  @Override
  protected void initServices() {
    this.marketDataService = new BitpandaMarketDataService(this);
    this.accountService = new BitpandaAccountService(this);
    this.tradeService = new BitpandaTradeService(this);
  }

  @Override
  public ExchangeSpecification getDefaultExchangeSpecification() {
    ExchangeSpecification exchangeSpecification = new ExchangeSpecification(this.getClass());
    exchangeSpecification.setSslUri("https://api.bitpanda.com");
    exchangeSpecification.setHost("api.bitpanda.com");
    exchangeSpecification.setPort(80);
    exchangeSpecification.setExchangeName("Bitpanda");
    exchangeSpecification.setExchangeDescription("Bitpanda is a bitcoin exchange based in Austria.");
    return exchangeSpecification;
  }

  @Override
  public void remoteInit() throws IOException, ExchangeException {
    String apiKey = getExchangeSpecification().getApiKey();
    if (apiKey == null || apiKey.isEmpty()) {
      // Cannot fetch assets without API key
      return;
    }
    BitpandaMarketDataService marketDataService = (BitpandaMarketDataService) this.marketDataService;
    List<BitpandaAsset> assets = marketDataService.getAssets();
    for (BitpandaAsset asset : assets) {
      assetIdMap.put(asset.getId(), new Currency(asset.getSymbol()));
    }
  }

  public Map<String, Currency> getAssetIdMap() {
    return assetIdMap;
  }

  @Override
  public SynchronizedValueFactory<Long> getNonceFactory() {
    return incrementalNonceFactory;
  }
}
