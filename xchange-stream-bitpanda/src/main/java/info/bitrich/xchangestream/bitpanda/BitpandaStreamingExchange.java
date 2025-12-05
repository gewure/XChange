package info.bitrich.xchangestream.bitpanda;

import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import io.reactivex.rxjava3.core.Completable;
import org.knowm.xchange.bitpanda.BitpandaExchange;

public class BitpandaStreamingExchange extends BitpandaExchange implements StreamingExchange {

  private static final String API_URI = "wss://api.bitpanda.com/v1/ws";

  private BitpandaStreamingService streamingService;
  private BitpandaStreamingMarketDataService streamingMarketDataService;

  @Override
  protected void initServices() {
    super.initServices();
    String apiKey = exchangeSpecification.getApiKey();
    this.streamingService = new BitpandaStreamingService(API_URI, apiKey);
    this.streamingMarketDataService = new BitpandaStreamingMarketDataService(streamingService);
  }

  @Override
  public Completable connect(ProductSubscription... args) {
    return streamingService.connect();
  }

  @Override
  public Completable disconnect() {
    return streamingService.disconnect();
  }

  @Override
  public boolean isAlive() {
    return streamingService.isSocketOpen();
  }

  @Override
  public StreamingMarketDataService getStreamingMarketDataService() {
    return streamingMarketDataService;
  }

  @Override
  public void useCompressedMessages(boolean compressedMessages) {
    streamingService.useCompressedMessages(compressedMessages);
  }
}
