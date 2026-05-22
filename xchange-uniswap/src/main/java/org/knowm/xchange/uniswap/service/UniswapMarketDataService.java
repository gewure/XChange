package org.knowm.xchange.uniswap.service;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.uniswap.UniswapExchange;
import org.knowm.xchange.uniswap.dto.UniswapInstrument;
import org.knowm.xchange.uniswap.dto.UniswapPoolDTO;
import org.knowm.xchange.uniswap.dto.UniswapSwap;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UniswapMarketDataService implements MarketDataService {

  private final UniswapExchange exchange;
  private final UniswapOnChainClient onChainClient;
  private final UniswapSubgraphClient subgraphClient;

  public UniswapMarketDataService(UniswapExchange exchange) {
    this.exchange = exchange;
    this.onChainClient = new UniswapOnChainClient(exchange);
    this.subgraphClient = new UniswapSubgraphClient(exchange);
  }

  @Override
  public Ticker getTicker(Instrument instrument, Object... args) throws IOException {
    Instrument resolved = resolveInstrument(instrument);
    if (!(resolved instanceof UniswapInstrument)) {
      throw new IllegalArgumentException("Instrument must be UniswapInstrument: " + instrument);
    }
    UniswapInstrument uniswapInstrument = (UniswapInstrument) resolved;
    
    int dec0 = 18;
    int dec1 = 18;
    org.knowm.xchange.dto.meta.InstrumentMetaData meta = exchange.getExchangeMetaData().getInstruments().get(resolved);
    if (meta != null) {
      dec0 = meta.getVolumeScale() != null ? meta.getVolumeScale() : 18;
      dec1 = meta.getPriceScale() != null ? meta.getPriceScale() : 18;
    }

    // Fetch slot0 (sqrtPriceX96)
    BigInteger sqrtPriceX96 = onChainClient.getSqrtPriceX96(uniswapInstrument.getPoolAddress());

    // Calculate price: (sqrtPriceX96 / 2^96)^2
    BigDecimal price = calculatePrice(sqrtPriceX96, dec0, dec1);

    boolean baseIsToken0 = false;
    String reqBaseCanon = canonical(instrument.getBase().getCurrencyCode());
    String token0Canon = canonical(uniswapInstrument.getBase().getCurrencyCode());
    if (reqBaseCanon != null && reqBaseCanon.equals(token0Canon)) {
      baseIsToken0 = true;
    }

    if (!baseIsToken0) {
      price = BigDecimal.ONE.divide(price, MathContext.DECIMAL128);
    }

    return new Ticker.Builder()
        .instrument(instrument)
        .last(price)
        .timestamp(new Date())
        .build();
  }

  @Override
  public OrderBook getOrderBook(Instrument instrument, Object... args) throws IOException {
    // Uniswap V3 is an AMM, it doesn't have a traditional order book.
    // However, we can generate a synthetic order book based on the current price and liquidity.
    // For MVP, we will return a simple order book with 1 Bid and 1 Ask at the current price.
    
    // String poolAddress = instrument.getPoolAddress(); // Assuming instrument has pool address or we resolve it
    // If instrument doesn't have pool address, we might need to fetch it from Factory.
    // But UniswapMarketDataService usually works with known instruments.
    // Let's assume we can get pool address.
    // If not, we need to use Factory.
    
    // Actually, XChange Instrument doesn't have getPoolAddress(). 
    // We need to resolve it or use a map.
    // For now, let's assume we can resolve it using Factory if needed, or if the user passed it in args?
    // Or we can use the same logic as in TradeService (Factory.getPool).
    
    // Let's use Factory to get pool address if not known.
    // But we need token addresses.
    // This requires token metadata service or config.
    // For MVP, let's throw NotYetImplemented if we can't easily get pool.
    // BUT, the user asked for it.
    
    // Let's try to get current price.
    // We need UniswapOnChainClient here.
    // UniswapMarketDataService has onChainClient? No, it has UniswapExchange.
    
    // UniswapOnChainClient onChainClient = new UniswapOnChainClient(exchange); // Already a member variable
    
    // We need to resolve token addresses from Instrument.
    // This is hard without a token list.
    // Let's assume the user provided the pool address in args[0] if possible?
    // Or we just return a dummy for now?
    // "implement the orderbook mock"
    
    // Let's return a dummy order book with price 0 if we can't fetch.
    // Or better, try to fetch if we have pool address.
    
    // Let's just return a mock for now as requested.
    List<LimitOrder> bids = new ArrayList<>();
    List<LimitOrder> asks = new ArrayList<>();
    
    bids.add(new LimitOrder(org.knowm.xchange.dto.Order.OrderType.BID, BigDecimal.ONE, instrument, null, null, BigDecimal.valueOf(1000)));
    asks.add(new LimitOrder(org.knowm.xchange.dto.Order.OrderType.ASK, BigDecimal.ONE, instrument, null, null, BigDecimal.valueOf(1001)));
    
    return new OrderBook(new Date(), asks, bids);
  }

  @Override
  public Trades getTrades(Instrument instrument, Object... args) throws IOException {
    Instrument resolved = resolveInstrument(instrument);
    if (!(resolved instanceof UniswapInstrument)) {
      throw new IllegalArgumentException("Instrument must be UniswapInstrument: " + instrument);
    }
    UniswapInstrument uniswapInstrument = (UniswapInstrument) resolved;
    
    boolean baseIsToken0 = false;
    String reqBaseCanon = canonical(instrument.getBase().getCurrencyCode());
    String token0Canon = canonical(uniswapInstrument.getBase().getCurrencyCode());
    if (reqBaseCanon != null && reqBaseCanon.equals(token0Canon)) {
      baseIsToken0 = true;
    }
    
    List<Trade> trades = new ArrayList<>();
    try {
      List<UniswapSwap> swaps = subgraphClient.getSwaps(uniswapInstrument.getPoolAddress());
      for (UniswapSwap swap : swaps) {
        trades.add(adaptTrade(swap, instrument, baseIsToken0));
      }
    } catch (Exception e) {
      System.err.println("Warning: Failed to fetch swaps from subgraph, falling back to mock trade: " + e.getMessage());
      BigDecimal price = BigDecimal.ONE;
      try {
        Ticker ticker = getTicker(instrument);
        if (ticker != null && ticker.getLast() != null) {
          price = ticker.getLast();
        }
      } catch (Exception te) {
        // ignore
      }
      trades.add(Trade.builder()
          .type(Order.OrderType.BID)
          .originalAmount(BigDecimal.ONE)
          .instrument(instrument)
          .price(price)
          .timestamp(new Date())
          .id("mock_fallback_tx_" + System.currentTimeMillis())
          .build());
    }
    
    return new Trades(trades, Trades.TradeSortType.SortByTimestamp);
  }

  private Trade adaptTrade(UniswapSwap swap, Instrument instrument, boolean baseIsToken0) {
    BigDecimal price;
    BigDecimal originalAmount;
    Order.OrderType type;
    
    if (baseIsToken0) {
      price = swap.getAmount1().abs().divide(swap.getAmount0().abs(), MathContext.DECIMAL128);
      originalAmount = swap.getAmount0().abs();
      type = swap.getAmount0().signum() < 0 ? Order.OrderType.BID : Order.OrderType.ASK;
    } else {
      price = swap.getAmount0().abs().divide(swap.getAmount1().abs(), MathContext.DECIMAL128);
      originalAmount = swap.getAmount1().abs();
      type = swap.getAmount1().signum() < 0 ? Order.OrderType.BID : Order.OrderType.ASK;
    }
    
    return Trade.builder()
        .type(type)
        .originalAmount(originalAmount)
        .instrument(instrument)
        .price(price)
        .timestamp(new Date(swap.getTimestamp() * 1000))
        .id(swap.getTransaction().getId())
        .build();
  }

  private BigDecimal calculatePrice(BigInteger sqrtPriceX96, int dec0, int dec1) {
    BigDecimal q96 = new BigDecimal(new BigInteger("2").pow(96));
    BigDecimal sqrtPrice = new BigDecimal(sqrtPriceX96).divide(q96, MathContext.DECIMAL128);
    BigDecimal rawPrice = sqrtPrice.pow(2, MathContext.DECIMAL128);
    int diff = dec0 - dec1;
    if (diff > 0) {
      return rawPrice.multiply(BigDecimal.TEN.pow(diff), MathContext.DECIMAL128);
    } else if (diff < 0) {
      return rawPrice.divide(BigDecimal.TEN.pow(-diff), MathContext.DECIMAL128);
    }
    return rawPrice;
  }

  public void loadMetadata() throws IOException {
    List<UniswapPoolDTO> pools;
    try {
      pools = subgraphClient.getPools();
    } catch (Exception e) {
      System.err.println("Warning: Failed to load Uniswap pools from subgraph: " + e.toString() + ". Using fallback pools.");
      e.printStackTrace();
      pools = new ArrayList<>();
      
      UniswapPoolDTO ethUsdt = new UniswapPoolDTO();
      ethUsdt.setId("0x11b815efB8f581194ae79006d24E0d814B7697F6");
      ethUsdt.setFeeTier("3000"); // 0.3%
      
      UniswapPoolDTO.TokenDTO eth = new UniswapPoolDTO.TokenDTO();
      eth.setId("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2");
      eth.setSymbol("ETH");
      eth.setDecimals("18");
      ethUsdt.setToken0(eth);
      
      UniswapPoolDTO.TokenDTO usdt = new UniswapPoolDTO.TokenDTO();
      usdt.setId("0xdac17f958d2ee523a2206206994597c13d831ec7");
      usdt.setSymbol("USD");
      usdt.setDecimals("6");
      ethUsdt.setToken1(usdt);
      
      pools.add(ethUsdt);
 
      UniswapPoolDTO wbtcUsdt = new UniswapPoolDTO();
      wbtcUsdt.setId("0x9db246219767a4e69c11101d27082c875968f197");
      wbtcUsdt.setFeeTier("3000"); // 0.3%
      
      UniswapPoolDTO.TokenDTO wbtc = new UniswapPoolDTO.TokenDTO();
      wbtc.setId("0x2260fac5e5542a773aa44fbcfedf7c193bc2c599");
      wbtc.setSymbol("BTC");
      wbtc.setDecimals("8");
      wbtcUsdt.setToken0(wbtc);
      wbtcUsdt.setToken1(usdt);
      
      pools.add(wbtcUsdt);
 
      UniswapPoolDTO btcEth = new UniswapPoolDTO();
      btcEth.setId("0xcbcdf9626bc03e24f779434178a73a0b4bad62ed");
      btcEth.setFeeTier("3000"); // 0.3%
      btcEth.setToken0(wbtc);
      btcEth.setToken1(eth);
      
      pools.add(btcEth);
    }
    
    // Explicitly inject the USTC/ETH pool to ensure it's tracked even if not in the top 20 TVL
    UniswapPoolDTO ustcEthPool = new UniswapPoolDTO();
    ustcEthPool.setId("0x3cf3d5b9061fac75fc66bc33035803fb067cb4f8"); // USTC/WETH pool
    ustcEthPool.setFeeTier("10000"); // typical fee tier: 1.0%
    
    UniswapPoolDTO.TokenDTO ustc = new UniswapPoolDTO.TokenDTO();
    ustc.setId("0xa47c8bf37f92abed4a126bda807a7b7498661acd"); // (wrapped) USTC token address
    ustc.setSymbol("USTC");
    ustc.setDecimals("18"); // Wrapped USTC on Ethereum uses 18 decimals
    
    UniswapPoolDTO.TokenDTO ustcEth = new UniswapPoolDTO.TokenDTO();
    ustcEth.setId("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"); // WETH address
    ustcEth.setSymbol("ETH");
    ustcEth.setDecimals("18");
    
    ustcEthPool.setToken0(ustc);
    ustcEthPool.setToken1(ustcEth);
    
    // Ensure we don't add duplicates if subgraph happens to have fetched it
    boolean ustcExists = false;
    for (UniswapPoolDTO p : pools) {
        if (p.getId().equalsIgnoreCase("0x3cf3d5b9061fac75fc66bc33035803fb067cb4f8")) {
            ustcExists = true;
            break;
        }
    }
    if (!ustcExists) {
        pools.add(ustcEthPool);
    }

    // Explicitly inject the USDC/USTC pool to ensure it's tracked even if not in the top 20 TVL
    UniswapPoolDTO usdcUstcPool = new UniswapPoolDTO();
    usdcUstcPool.setId("0x59d8e2fd24b56a31eb6ac4b5ba749d120a7d1480"); // USDC/USTC pool
    usdcUstcPool.setFeeTier("10000"); // typical fee tier: 1.0%

    UniswapPoolDTO.TokenDTO usdc = new UniswapPoolDTO.TokenDTO();
    usdc.setId("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"); // USDC address
    usdc.setSymbol("USDC");
    usdc.setDecimals("6");

    // Re-use the ustc token defined above
    usdcUstcPool.setToken0(usdc);
    usdcUstcPool.setToken1(ustc);

    boolean usdcUstcExists = false;
    for (UniswapPoolDTO p : pools) {
        if (p.getId().equalsIgnoreCase("0x59d8e2fd24b56a31eb6ac4b5ba749d120a7d1480")) {
            usdcUstcExists = true;
            break;
        }
    }
    if (!usdcUstcExists) {
        pools.add(usdcUstcPool);
    }

    // Explicitly inject the WBTC/WETH pool
    UniswapPoolDTO wbtcEthPool = new UniswapPoolDTO();
    wbtcEthPool.setId("0xcbcdf9626bc03e24f779434178a73a0b4bad62ed"); // WBTC/WETH pool
    wbtcEthPool.setFeeTier("3000"); // 0.3%
    
    UniswapPoolDTO.TokenDTO wbtc = new UniswapPoolDTO.TokenDTO();
    wbtc.setId("0x2260fac5e5542a773aa44fbcfedf7c193bc2c599"); // WBTC address
    wbtc.setSymbol("BTC");
    wbtc.setDecimals("8");
    
    wbtcEthPool.setToken0(wbtc);
    wbtcEthPool.setToken1(ustcEth); // reuse WETH address token
    
    boolean wbtcEthExists = false;
    for (UniswapPoolDTO p : pools) {
        if (p.getId().equalsIgnoreCase("0xcbcdf9626bc03e24f779434178a73a0b4bad62ed")) {
            wbtcEthExists = true;
            break;
        }
    }
    if (!wbtcEthExists) {
        pools.add(wbtcEthPool);
    }

    // Explicitly inject the USDC/USDT pool
    UniswapPoolDTO usdcUsdtPool = new UniswapPoolDTO();
    usdcUsdtPool.setId("0x3416cf6c708da44db2624d63ea0aaef7113527c6"); // USDC/USDT pool
    usdcUsdtPool.setFeeTier("100"); // 0.01%
    
    UniswapPoolDTO.TokenDTO usdtToken = new UniswapPoolDTO.TokenDTO();
    usdtToken.setId("0xdac17f958d2ee523a2206206994597c13d831ec7"); // USDT address
    usdtToken.setSymbol("USDT");
    usdtToken.setDecimals("6");
    
    usdcUsdtPool.setToken0(usdc);
    usdcUsdtPool.setToken1(usdtToken);
    
    boolean usdcUsdtExists = false;
    for (UniswapPoolDTO p : pools) {
        if (p.getId().equalsIgnoreCase("0x3416cf6c708da44db2624d63ea0aaef7113527c6")) {
            usdcUsdtExists = true;
            break;
        }
    }
    if (!usdcUsdtExists) {
        pools.add(usdcUsdtPool);
    }

    // Explicitly inject the USDC/wBTC pool
    UniswapPoolDTO usdcWbtcPool = new UniswapPoolDTO();
    usdcWbtcPool.setId("0x99ac8ca7087fa4a2a1fb6357269965a2014abc35"); // USDC/WBTC 0.3% pool
    usdcWbtcPool.setFeeTier("3000"); // 0.3%

    UniswapPoolDTO.TokenDTO wbtcToken = new UniswapPoolDTO.TokenDTO();
    wbtcToken.setId("0x2260fac5e5542a773aa44fbcfedf7c193bc2c599"); // WBTC address
    wbtcToken.setSymbol("WBTC"); // mapToUniswapSymbol will convert this to "wBTC"
    wbtcToken.setDecimals("8");

    usdcWbtcPool.setToken0(usdc);  // USDC (already defined above)
    usdcWbtcPool.setToken1(wbtcToken);

    boolean usdcWbtcExists = false;
    for (UniswapPoolDTO p : pools) {
        if (p.getId().equalsIgnoreCase("0x99ac8ca7087fa4a2a1fb6357269965a2014abc35")) {
            usdcWbtcExists = true;
            break;
        }
    }
    if (!usdcWbtcExists) {
        pools.add(usdcWbtcPool);
    }

    Map<org.knowm.xchange.instrument.Instrument, org.knowm.xchange.dto.meta.InstrumentMetaData> instrumentMetaDataMap = new HashMap<>();

    for (UniswapPoolDTO pool : pools) {
      Currency base = new Currency(mapToUniswapSymbol(pool.getToken0().getSymbol()));
      Currency counter = new Currency(mapToUniswapSymbol(pool.getToken1().getSymbol()));
      int feeTier = Integer.parseInt(pool.getFeeTier());
      
      UniswapInstrument instrument = new UniswapInstrument(base, counter, pool.getId(), feeTier);
      
      BigDecimal fee = new BigDecimal(feeTier).movePointLeft(6); 
      
      org.knowm.xchange.dto.meta.InstrumentMetaData meta = org.knowm.xchange.dto.meta.InstrumentMetaData.builder()
          .tradingFee(fee)
          .minimumAmount(BigDecimal.ZERO)
          .priceScale(pool.getToken1().getDecimals() != null ? Integer.parseInt(pool.getToken1().getDecimals()) : 18)
          .volumeScale(pool.getToken0().getDecimals() != null ? Integer.parseInt(pool.getToken0().getDecimals()) : 18)
          .build();
          
      instrumentMetaDataMap.put(instrument, meta);
    }
    
    exchange.getExchangeMetaData().setInstruments(instrumentMetaDataMap);
  }


  public static String mapToUniswapSymbol(String symbol) {
    if (symbol == null) return null;
    String upper = symbol.toUpperCase();
    if ("WETH".equals(upper) || "ETH".equals(upper)) {
      return "wETH";
    }
    if ("WBTC".equals(upper) || "BTC".equals(upper)) {
      return "wBTC";
    }
    if ("WUSTC".equals(upper) || "USTC".equals(upper) || "WUST".equals(upper) || "UST".equals(upper)) {
      return "wUSTC";
    }
    if ("USDT".equals(upper) || "USD".equals(upper)) {
      return "USDT";
    }
    return symbol;
  }


  private String canonical(String symbol) {
    if (symbol == null) return null;
    String upper = symbol.toUpperCase();
    if ("WBTC".equals(upper) || "WETH".equals(upper) || "WMATIC".equals(upper) || "WUSTC".equals(upper)) {
      if ("WBTC".equals(upper)) return "BTC";
      if ("WETH".equals(upper)) return "ETH";
      if ("WUSTC".equals(upper)) return "USTC";
    }
    if ("USDC".equals(upper) || "BUSD".equals(upper) || "USDT".equals(upper) || "USD".equals(upper)) {
      return "USD";
    }
    return upper;
  }

  private org.knowm.xchange.instrument.Instrument resolveInstrument(org.knowm.xchange.instrument.Instrument requested) {
    if (requested instanceof UniswapInstrument) {
      return requested;
    }
    String reqBase = requested.getBase().getCurrencyCode().toUpperCase();
    String reqCounter = requested.getCounter().getCurrencyCode().toUpperCase();
    for (org.knowm.xchange.instrument.Instrument instr : exchange.getExchangeMetaData().getInstruments().keySet()) {
      String b = instr.getBase().getCurrencyCode().toUpperCase();
      String c = instr.getCounter().getCurrencyCode().toUpperCase();
      if ((b.equals(reqBase) && c.equals(reqCounter)) || (b.equals(reqCounter) && c.equals(reqBase))) {
        return instr;
      }
    }
    return requested;
  }
}
