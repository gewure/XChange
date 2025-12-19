package org.knowm.xchange.uniswap.stream;

import io.reactivex.rxjava3.core.Observable;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.instrument.Instrument;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import org.knowm.xchange.uniswap.UniswapExchange;
import org.knowm.xchange.uniswap.dto.UniswapInstrument;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;

import java.math.BigDecimal;
import java.util.Date;

public class UniswapStreamingMarketDataService implements StreamingMarketDataService {

  private final UniswapStreamingService service;
  private final UniswapExchange exchange;

  public UniswapStreamingMarketDataService(UniswapStreamingService service, UniswapExchange exchange) {
    this.service = service;
    this.exchange = exchange;
  }

  @Override
  public Observable<Ticker> getTicker(Instrument instrument, Object... args) {
    return getTrades(instrument, args)
        .map(trade -> new Ticker.Builder()
            .instrument(instrument)
            .last(trade.getPrice())
            .timestamp(trade.getTimestamp())
            .build());
  }

  @Override
  public Observable<Trade> getTrades(Instrument instrument, Object... args) {
    if (!(instrument instanceof UniswapInstrument)) {
      throw new IllegalArgumentException("Instrument must be UniswapInstrument");
    }
    UniswapInstrument uniswapInstrument = (UniswapInstrument) instrument;
    String poolAddress = uniswapInstrument.getPoolAddress();

    // Swap event signature: Swap(address,address,int256,int256,uint160,uint128,int24)
    // Topic 0: 0xc42079f94a6350d7e6235f29174924f928cc2ac818eb64fed8004e115fbcca67
    org.web3j.protocol.core.methods.request.EthFilter filter = new org.web3j.protocol.core.methods.request.EthFilter(
        DefaultBlockParameterName.LATEST,
        DefaultBlockParameterName.LATEST,
        poolAddress
    );
    filter.addSingleTopic("0xc42079f94a6350d7e6235f29174924f928cc2ac818eb64fed8004e115fbcca67");

    // Bridge RxJava 2 Flowable (from Web3j 4.10.0) to RxJava 3 Observable
    io.reactivex.Flowable<org.web3j.protocol.core.methods.response.Log> rx2Flowable = service.getWeb3j().ethLogFlowable(filter);

    // Using simple From/To Publisher conversion via Reactive Streams interface
    return io.reactivex.rxjava3.core.Flowable.fromPublisher(rx2Flowable).toObservable()
        .flatMap(log -> {
            try {
                // Decode log data
                // Topics: [signature]
                // Data: sender, recipient, amount0, amount1, sqrtPriceX96, liquidity, tick
                
                String data = log.getData();
                // Note: data starts with "0x"
                String cleanData = data.substring(2);
                if (cleanData.length() < 320) { // Need at least up to tick (160 bytes * 2)
                    return Observable.empty(); 
                }
                
                // amount0: 0-64 chars (32 bytes)
                String amount0Hex = cleanData.substring(0, 64);
                java.math.BigInteger amount0 = new java.math.BigInteger(amount0Hex, 16);
                // Handle two's complement for negative numbers (32 bytes = 256 bits)
                if (amount0.testBit(255)) {
                    amount0 = amount0.subtract(java.math.BigInteger.ONE.shiftLeft(256));
                }
                
                // sqrtPriceX96: 128-192 chars (64-96 bytes)
                String sqrtPriceHex = cleanData.substring(128, 192);
                java.math.BigInteger sqrtPriceX96 = new java.math.BigInteger(sqrtPriceHex, 16);
                
                BigDecimal price = calculatePrice(sqrtPriceX96);
                
                // Determine side: amount0 < 0 => Pool sold token0 => User bought token0
                // We assume Token0 is Base for simplicity, or we should check instrument.
                // If Token0 is Base: amount0 < 0 -> BID, amount0 > 0 -> ASK
                org.knowm.xchange.dto.Order.OrderType type = amount0.signum() < 0 ? org.knowm.xchange.dto.Order.OrderType.BID : org.knowm.xchange.dto.Order.OrderType.ASK;
                
                Trade trade = Trade.builder()
                    .instrument(instrument)
                    .type(type)
                    .originalAmount(new BigDecimal(amount0.abs())) // This is raw amount, should be scaled by decimals
                    .price(price)
                    .timestamp(new Date())
                    .id(log.getTransactionHash())
                    .build();
                    
                return Observable.just(trade);
            } catch (Exception e) {
                // Log error but don't crash stream
                return Observable.empty();
            }
        })
        .onErrorResumeNext(e -> Observable.empty()); // Recover from stream errors
  }

  private BigDecimal calculatePrice(java.math.BigInteger sqrtPriceX96) {
    BigDecimal q96 = new BigDecimal(new java.math.BigInteger("2").pow(96));
    BigDecimal sqrtPrice = new BigDecimal(sqrtPriceX96).divide(q96, java.math.MathContext.DECIMAL128);
    return sqrtPrice.pow(2, java.math.MathContext.DECIMAL128);
  }

  @Override
  public Observable<OrderBook> getOrderBook(Instrument instrument, Object... args) {
    return Observable.empty(); // AMM has no order book
  }
}
