package org.knowm.xchange.uniswap.stream;

import io.reactivex.rxjava3.core.Observable;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.instrument.Instrument;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import org.knowm.xchange.uniswap.UniswapExchange;
import org.knowm.xchange.uniswap.dto.UniswapInstrument;
import org.knowm.xchange.uniswap.service.UniswapOnChainClient;
import org.web3j.protocol.core.DefaultBlockParameterName;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UniswapStreamingMarketDataService implements StreamingMarketDataService {

  private final UniswapStreamingService service;
  private final UniswapExchange exchange;
  private final UniswapOnChainClient onChainClient;

  // Cache to avoid slow network queries per block
  private final Map<String, String[]> poolTokensCache = new ConcurrentHashMap<>();
  private final Map<String, Integer> tokenDecimalsCache = new ConcurrentHashMap<>();
  private final Map<String, String> tokenSymbolsCache = new ConcurrentHashMap<>();

  public UniswapStreamingMarketDataService(UniswapStreamingService service, UniswapExchange exchange) {
    this.service = service;
    this.exchange = exchange;
    this.onChainClient = new UniswapOnChainClient(exchange);
  }

  @Override
  public Observable<Ticker> getTicker(org.knowm.xchange.currency.CurrencyPair currencyPair, Object... args) {
    return getTicker((Instrument) currencyPair, args);
  }

  @Override
  public Observable<Trade> getTrades(org.knowm.xchange.currency.CurrencyPair currencyPair, Object... args) {
    return getTrades((Instrument) currencyPair, args);
  }

  @Override
  public Observable<OrderBook> getOrderBook(org.knowm.xchange.currency.CurrencyPair currencyPair, Object... args) {
    return getOrderBook((Instrument) currencyPair, args);
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
    UniswapInstrument uniswapInstrument = null;
    if (instrument instanceof UniswapInstrument) {
      uniswapInstrument = (UniswapInstrument) instrument;
    } else if (exchange.getExchangeMetaData() != null && exchange.getExchangeMetaData().getInstruments() != null) {
      for (Instrument instr : exchange.getExchangeMetaData().getInstruments().keySet()) {
        if (instr instanceof UniswapInstrument
            && instr.getBase().equals(instrument.getBase())
            && instr.getCounter().equals(instrument.getCounter())) {
          uniswapInstrument = (UniswapInstrument) instr;
          break;
        }
      }
    }
    if (uniswapInstrument == null) {
      throw new IllegalArgumentException("Unsupported instrument: " + instrument);
    }
    String poolAddress = uniswapInstrument.getPoolAddress();

    if (service.isMockMode()) {
        return Observable.intervalRange(0, Long.MAX_VALUE, 2, 5, java.util.concurrent.TimeUnit.SECONDS, io.reactivex.rxjava3.schedulers.Schedulers.computation())
            .map(tick -> {
                BigDecimal basePrice = new BigDecimal("1.00");
                if (instrument.getBase().getSymbol().equalsIgnoreCase("ETH")) {
                    basePrice = new BigDecimal("3120.50");
                } else if (instrument.getBase().getSymbol().equalsIgnoreCase("BTC")) {
                    basePrice = new BigDecimal("92400.00");
                }
                
                BigDecimal mockPrice = basePrice.add(new BigDecimal(Math.random() - 0.5).multiply(basePrice.multiply(new BigDecimal("0.005"))));
                BigDecimal mockAmount = new BigDecimal(Math.random()).multiply(new BigDecimal("2.5")).add(new BigDecimal("0.1"));
                org.knowm.xchange.dto.Order.OrderType type = Math.random() > 0.5 ? org.knowm.xchange.dto.Order.OrderType.BID : org.knowm.xchange.dto.Order.OrderType.ASK;
                
                return Trade.builder()
                    .instrument(instrument)
                    .type(type)
                    .originalAmount(mockAmount)
                    .price(mockPrice)
                    .timestamp(new Date())
                    .id("mock_tx_" + tick)
                    .build();
            });
    }

    // Resolve tokens and decimals
    String[] tokens = poolTokensCache.get(poolAddress.toLowerCase());
    if (tokens == null) {
        try {
            String t0 = onChainClient.getToken0(poolAddress);
            String t1 = onChainClient.getToken1(poolAddress);
            tokens = new String[]{t0, t1};
            poolTokensCache.put(poolAddress.toLowerCase(), tokens);
        } catch (Exception e) {
            return Observable.empty();
        }
    }
    String token0 = tokens[0];
    String token1 = tokens[1];
    
    Integer dec0 = tokenDecimalsCache.get(token0.toLowerCase());
    if (dec0 == null) {
        try {
            dec0 = onChainClient.getDecimals(token0);
            tokenDecimalsCache.put(token0.toLowerCase(), dec0);
        } catch (Exception e) {
            dec0 = 18;
        }
    }
    
    Integer dec1 = tokenDecimalsCache.get(token1.toLowerCase());
    if (dec1 == null) {
        try {
            dec1 = onChainClient.getDecimals(token1);
            tokenDecimalsCache.put(token1.toLowerCase(), dec1);
        } catch (Exception e) {
            dec1 = 18;
        }
    }

    String symbol0 = tokenSymbolsCache.get(token0.toLowerCase());
    if (symbol0 == null) {
        try {
            symbol0 = onChainClient.getSymbol(token0);
            tokenSymbolsCache.put(token0.toLowerCase(), symbol0);
        } catch (Exception e) {
            symbol0 = "WETH";
        }
    }

    boolean baseIsToken0 = false;
    if (instrument.getBase().getCurrencyCode().equals("ETH")) {
         if (symbol0.contains("WETH") || symbol0.contains("WMATIC")) {
             baseIsToken0 = true;
         }
    } else if (instrument.getBase().getCurrencyCode().equals(symbol0)) {
        baseIsToken0 = true;
    }

    final int finalDec0 = dec0;
    final int finalDec1 = dec1;
    final boolean finalBaseIsToken0 = baseIsToken0;

    // Swap event signature: Swap(address,address,int256,int256,uint160,uint128,int24)
    // Topic 0: 0xc42079f94a6350d7e6235f29174924f928cc2ac818eb64fed8004e115fbcca67
    io.reactivex.Flowable<org.web3j.protocol.websocket.events.LogNotification> rx2Flowable =
        service.getWeb3j().logsNotifications(
            java.util.List.of(poolAddress),
            java.util.List.of("0xc42079f94a6350d7e6235f29174924f928cc2ac818eb64fed8004e115fbcca67")
        );

    return io.reactivex.rxjava3.core.Flowable.fromPublisher(rx2Flowable).toObservable()
        .flatMap(logNotification -> {
            try {
                org.web3j.protocol.websocket.events.Log log = logNotification.getParams().getResult();
                String data = log.getData();
                String cleanData = data.substring(2);
                if (cleanData.length() < 320) {
                    return Observable.empty(); 
                }
                
                // amount0: 0-64 chars (32 bytes)
                String amount0Hex = cleanData.substring(0, 64);
                java.math.BigInteger amount0 = new java.math.BigInteger(amount0Hex, 16);
                if (amount0.testBit(255)) {
                    amount0 = amount0.subtract(java.math.BigInteger.ONE.shiftLeft(256));
                }

                // amount1: 64-128 chars (32 bytes)
                String amount1Hex = cleanData.substring(64, 128);
                java.math.BigInteger amount1 = new java.math.BigInteger(amount1Hex, 16);
                if (amount1.testBit(255)) {
                    amount1 = amount1.subtract(java.math.BigInteger.ONE.shiftLeft(256));
                }
                
                // sqrtPriceX96: 128-192 chars (64-96 bytes)
                String sqrtPriceHex = cleanData.substring(128, 192);
                java.math.BigInteger sqrtPriceX96 = new java.math.BigInteger(sqrtPriceHex, 16);
                
                BigDecimal rawPriceToken0InToken1 = calculatePrice(sqrtPriceX96, finalDec0, finalDec1);
                
                BigDecimal price;
                BigDecimal amount;
                org.knowm.xchange.dto.Order.OrderType type;
                
                if (finalBaseIsToken0) {
                    price = rawPriceToken0InToken1;
                    amount = new BigDecimal(amount0.abs()).divide(BigDecimal.TEN.pow(finalDec0), java.math.MathContext.DECIMAL128);
                    type = amount0.signum() < 0 ? org.knowm.xchange.dto.Order.OrderType.BID : org.knowm.xchange.dto.Order.OrderType.ASK;
                } else {
                    price = BigDecimal.ONE.divide(rawPriceToken0InToken1, java.math.MathContext.DECIMAL128);
                    amount = new BigDecimal(amount1.abs()).divide(BigDecimal.TEN.pow(finalDec1), java.math.MathContext.DECIMAL128);
                    type = amount1.signum() < 0 ? org.knowm.xchange.dto.Order.OrderType.BID : org.knowm.xchange.dto.Order.OrderType.ASK;
                }
                
                Trade trade = Trade.builder()
                    .instrument(instrument)
                    .type(type)
                    .originalAmount(amount)
                    .price(price)
                    .timestamp(new Date())
                    .id(log.getTransactionHash())
                    .build();
                    
                return Observable.just(trade);
            } catch (Exception e) {
                return Observable.empty();
            }
        })
        .onErrorResumeNext(e -> Observable.empty());
  }

  private BigDecimal calculatePrice(java.math.BigInteger sqrtPriceX96, int dec0, int dec1) {
    BigDecimal q96 = new BigDecimal(new java.math.BigInteger("2").pow(96));
    BigDecimal sqrtPrice = new BigDecimal(sqrtPriceX96).divide(q96, java.math.MathContext.DECIMAL128);
    BigDecimal rawPrice = sqrtPrice.pow(2, java.math.MathContext.DECIMAL128);
    int diff = dec0 - dec1;
    if (diff > 0) {
        return rawPrice.multiply(BigDecimal.TEN.pow(diff), java.math.MathContext.DECIMAL128);
    } else if (diff < 0) {
        return rawPrice.divide(BigDecimal.TEN.pow(-diff), java.math.MathContext.DECIMAL128);
    }
    return rawPrice;
  }

  @Override
  public Observable<OrderBook> getOrderBook(Instrument instrument, Object... args) {
    return getTrades(instrument, args)
        .map(trade -> {
            BigDecimal price = trade.getPrice();
            BigDecimal spread = price.multiply(new BigDecimal("0.001")); // 0.1% spread
            BigDecimal bidPrice = price.subtract(spread.divide(BigDecimal.valueOf(2)));
            BigDecimal askPrice = price.add(spread.divide(BigDecimal.valueOf(2)));
            
            java.util.List<org.knowm.xchange.dto.trade.LimitOrder> bids = new java.util.ArrayList<>();
            java.util.List<org.knowm.xchange.dto.trade.LimitOrder> asks = new java.util.ArrayList<>();
            
            bids.add(new org.knowm.xchange.dto.trade.LimitOrder(org.knowm.xchange.dto.Order.OrderType.BID, new BigDecimal("100"), instrument, null, new Date(), bidPrice));
            asks.add(new org.knowm.xchange.dto.trade.LimitOrder(org.knowm.xchange.dto.Order.OrderType.ASK, new BigDecimal("100"), instrument, null, new Date(), askPrice));
            
            return new OrderBook(new Date(), asks, bids);
        });
  }
}
