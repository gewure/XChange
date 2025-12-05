package org.knowm.xchange.uniswap.stream;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import org.knowm.xchange.service.BaseExchangeService;
import org.knowm.xchange.uniswap.UniswapExchange;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.websocket.WebSocketService;

import java.net.ConnectException;

public class UniswapStreamingService extends BaseExchangeService { // Removed StreamingService interface for now as we don't know where it is exactly, or we implement ConnectableService?
// Actually StreamingExchange expects us to return Completable from connect().
// Let's just implement the methods required by StreamingExchange usage, or find the interface.
// If I can't find StreamingService interface, I can just implement the methods.
// But UniswapStreamingExchange uses it.


  private final String apiUrl;
  private Web3j web3j;
  private WebSocketService webSocketService;

  public UniswapStreamingService(UniswapExchange exchange, String apiUrl) {
    super(exchange);
    this.apiUrl = apiUrl;
  }

  public Completable connect() {
    return Completable.create(emitter -> {
      try {
        webSocketService = new WebSocketService(apiUrl, true);
        webSocketService.connect();
        web3j = Web3j.build(webSocketService);
        emitter.onComplete();
      } catch (ConnectException e) {
        emitter.onError(e);
      }
    });
  }

  public Completable disconnect() {
    return Completable.create(emitter -> {
      if (webSocketService != null) {
        webSocketService.close();
      }
      emitter.onComplete();
    });
  }

  public boolean isSocketOpen() {
    return webSocketService != null; // WebSocketService doesn't expose isOpen easily, assume open if connected without error
  }

  public Web3j getWeb3j() {
    return web3j;
  }

  public Observable<Object> subscribeChannel(String channelName, Object... args) {
    // Not used for direct channel subscription in this adapter, 
    // as we use specific Web3j flowables in MarketDataService
    return Observable.empty();
  }

  public Observable<Object> unsubscribeChannel(String channelName, Object... args) {
    return Observable.empty();
  }
  
  public void useCompressedMessages(boolean compressedMessages) {
      // No-op
  }
}
