package org.knowm.xchange.bitpanda.service;

import java.io.IOException;
import java.util.List;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.bitpanda.BitpandaAdapters;
import org.knowm.xchange.bitpanda.dto.marketdata.BitpandaAsset;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.service.marketdata.MarketDataService;

public class BitpandaMarketDataService extends BitpandaBaseService implements MarketDataService {

  public BitpandaMarketDataService(Exchange exchange) {
    super(exchange);
  }

  @Override
  public Ticker getTicker(CurrencyPair currencyPair, Object... args) throws IOException {
    return BitpandaAdapters.adaptTicker(bitpanda.getTicker(), currencyPair);
  }

  @Override
  public OrderBook getOrderBook(CurrencyPair currencyPair, Object... args) throws IOException {
    throw new NotAvailableFromExchangeException();
  }

  @Override
  public Trades getTrades(CurrencyPair currencyPair, Object... args) throws IOException {
    throw new NotAvailableFromExchangeException();
  }

  public List<BitpandaAsset> getAssets() throws IOException {
    String apiKey = exchange.getExchangeSpecification().getApiKey();
    return bitpanda.getAssets(apiKey).getData();
  }
}
