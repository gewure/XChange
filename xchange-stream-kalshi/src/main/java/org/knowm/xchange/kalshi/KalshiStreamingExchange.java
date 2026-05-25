package org.knowm.xchange.kalshi;

import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.core.StreamingTradeService;
import io.reactivex.rxjava3.core.Completable;
import org.knowm.xchange.ExchangeSpecification;

public class KalshiStreamingExchange extends KalshiExchange implements StreamingExchange {

    private KalshiStreamingMarketDataService streamingMarketDataService;

    @Override
    protected void initServices() {
        super.initServices();
        this.streamingMarketDataService = new KalshiStreamingMarketDataService(this);
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
        return streamingMarketDataService.isAlive();
    }

    @Override
    public StreamingMarketDataService getStreamingMarketDataService() {
        return streamingMarketDataService;
    }

    @Override
    public StreamingTradeService getStreamingTradeService() {
        return null;
    }

    @Override
    public void useCompressedMessages(boolean compressedMessages) {
        // Not implemented
    }

    @Override
    public ExchangeSpecification getDefaultExchangeSpecification() {
        ExchangeSpecification spec = super.getDefaultExchangeSpecification();
        spec.setExchangeSpecificParametersItem("streaming_uri", "wss://external-api.kalshi.com/trade-api/ws/v2");
        return spec;
    }
}
