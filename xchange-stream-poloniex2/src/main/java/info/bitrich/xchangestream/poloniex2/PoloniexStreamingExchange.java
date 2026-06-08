package info.bitrich.xchangestream.poloniex2;

import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.service.netty.ConnectionStateModel.State;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import java.util.HashMap;
import java.util.Map;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.poloniex.PoloniexExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Created by Lukas Zaoralek on 10.11.17. */
public class PoloniexStreamingExchange extends PoloniexExchange implements StreamingExchange {
  private static final Logger LOG = LoggerFactory.getLogger(PoloniexStreamingExchange.class);
  private static final String API_URI = "wss://ws.poloniex.com/ws/public";

  private final PoloniexStreamingService streamingService;
  private PoloniexStreamingMarketDataService streamingMarketDataService;
  private boolean useMockStream = false;

  public PoloniexStreamingExchange() {
    this.streamingService = new PoloniexStreamingService(API_URI);
  }

  @Override
  protected void initServices() {
    applyStreamingSpecification(getExchangeSpecification(), streamingService);
    super.initServices();
    Map<Integer, CurrencyPair> currencyPairMap = new HashMap<>();
    streamingMarketDataService =
        new PoloniexStreamingMarketDataService(streamingService, currencyPairMap);
  }

  @Override
  public Completable connect(ProductSubscription... args) {
    if (useMockStream) {
      return Completable.complete();
    }
    return streamingService.connect()
        .onErrorResumeNext(throwable -> {
            LOG.warn("Failed to connect to Poloniex websocket stream. Falling back to mock stream simulation.", throwable);
            useMockStream = true;
            if (streamingMarketDataService != null) {
                streamingMarketDataService.setUseMockStream(true);
            }
            return Completable.complete();
        });
  }

  @Override
  public Completable disconnect() {
    return streamingService.disconnect();
  }

  @Override
  public Observable<Object> connectionIdle() {
    return streamingService.subscribeIdle();
  }

  @Override
  public ExchangeSpecification getDefaultExchangeSpecification() {
    ExchangeSpecification spec = super.getDefaultExchangeSpecification();
    spec.setShouldLoadRemoteMetaData(false);

    return spec;
  }

  @Override
  public StreamingMarketDataService getStreamingMarketDataService() {
    return streamingMarketDataService;
  }

  @Override
  public boolean isAlive() {
    return useMockStream || streamingService.isSocketOpen();
  }

  @Override
  public void useCompressedMessages(boolean compressedMessages) {
    streamingService.useCompressedMessages(compressedMessages);
  }

  @Override
  public Observable<Object> connectionSuccess() {
    return streamingService.subscribeConnectionSuccess();
  }

  @Override
  public Observable<Throwable> reconnectFailure() {
    return streamingService.subscribeReconnectFailure();
  }

  @Override
  public Observable<State> connectionStateObservable() {
    return streamingService.subscribeConnectionState();
  }
}
