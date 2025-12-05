package org.knowm.xchange.uniswap.stream;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import org.knowm.xchange.ExchangeSpecification;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.core.StreamingTradeService;
import info.bitrich.xchangestream.core.StreamingAccountService;
import org.knowm.xchange.uniswap.UniswapExchange;

import info.bitrich.xchangestream.core.ProductSubscription;

public class UniswapStreamingExchange extends UniswapExchange implements StreamingExchange {

  private UniswapStreamingService streamingService;
  private UniswapStreamingMarketDataService streamingMarketDataService;

  @Override
  protected void initServices() {
    super.initServices();
    // Streaming service is initialized in connect() or manually
  }

  @Override
  public Completable connect(ProductSubscription... args) {
    if (streamingService == null) {
      ExchangeSpecification spec = getExchangeSpecification();
      // Use a specific streaming URI or default to a known WS provider if not set
      // Note: Cloudflare doesn't support WS. Infura/Alchemy do.
      String streamingUri = (String) spec.getExchangeSpecificParametersItem("streaming_uri");
      if (streamingUri == null) {
        throw new IllegalArgumentException("Streaming URI must be provided in ExchangeSpecification (param 'streaming_uri')");
      }
      streamingService = new UniswapStreamingService(this, streamingUri);
      streamingMarketDataService = new UniswapStreamingMarketDataService(streamingService, this);
    }
    return streamingService.connect();
  }

  @Override
  public Completable disconnect() {
    if (streamingService != null) {
      return streamingService.disconnect();
    }
    return Completable.complete();
  }

  @Override
  public boolean isAlive() {
    return streamingService != null && streamingService.isSocketOpen();
  }

  @Override
  public StreamingMarketDataService getStreamingMarketDataService() {
    return streamingMarketDataService;
  }

  @Override
  public StreamingTradeService getStreamingTradeService() {
    return null; // Not implemented
  }
  
  @Override
  public StreamingAccountService getStreamingAccountService() {
      return null;
  }

  @Override
  public void useCompressedMessages(boolean compressedMessages) {
    if (streamingService != null) {
        streamingService.useCompressedMessages(compressedMessages);
    }
  }
}
