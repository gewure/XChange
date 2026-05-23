package org.knowm.xchange.polymarket;

import io.reactivex.rxjava3.core.Completable;
import org.knowm.xchange.ExchangeSpecification;
import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingMarketDataService;

public class PolymarketStreamingExchange extends PolymarketExchange implements StreamingExchange {

  private PolymarketStreamingMarketDataService streamingMarketDataService;

  @Override
  protected void initServices() {
    super.initServices();
    this.streamingMarketDataService = new PolymarketStreamingMarketDataService(getExchangeSpecification());
  }

  @Override
  public Completable connect(ProductSubscription... args) {
    return streamingMarketDataService.connect();
  }

  @Override
  public Completable disconnect() {
    return streamingMarketDataService.disconnect();
  }

  @Override
  public boolean isAlive() {
    return streamingMarketDataService.isSocketOpen();
  }

  @Override
  public StreamingMarketDataService getStreamingMarketDataService() {
    return streamingMarketDataService;
  }

  @Override
  public void useCompressedMessages(boolean compressedMessages) {}
}
