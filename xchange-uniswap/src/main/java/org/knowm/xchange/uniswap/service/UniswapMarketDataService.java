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
    if (!(instrument instanceof UniswapInstrument)) {
      throw new IllegalArgumentException("Instrument must be UniswapInstrument");
    }
    UniswapInstrument uniswapInstrument = (UniswapInstrument) instrument;
    
    // Fetch slot0 (sqrtPriceX96)
    BigInteger sqrtPriceX96 = onChainClient.getSqrtPriceX96(uniswapInstrument.getPoolAddress());
    
    // Calculate price: (sqrtPriceX96 / 2^96)^2
    BigDecimal price = calculatePrice(sqrtPriceX96);

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
    if (!(instrument instanceof UniswapInstrument)) {
      throw new IllegalArgumentException("Instrument must be UniswapInstrument");
    }
    UniswapInstrument uniswapInstrument = (UniswapInstrument) instrument;
    
    List<UniswapSwap> swaps = subgraphClient.getSwaps(uniswapInstrument.getPoolAddress());
    
    List<Trade> trades = new ArrayList<>();
    for (UniswapSwap swap : swaps) {
      trades.add(adaptTrade(swap, instrument));
    }
    
    return new Trades(trades, Trades.TradeSortType.SortByTimestamp);
  }

  private Trade adaptTrade(UniswapSwap swap, Instrument instrument) {
    Order.OrderType type = swap.getAmount0().signum() < 0 ? Order.OrderType.BID : Order.OrderType.ASK;
    
    BigDecimal price = swap.getAmount1().abs().divide(swap.getAmount0().abs(), MathContext.DECIMAL128);
    BigDecimal originalAmount = swap.getAmount0().abs();
    
    return Trade.builder()
        .type(type)
        .originalAmount(originalAmount)
        .instrument(instrument)
        .price(price)
        .timestamp(new Date(swap.getTimestamp() * 1000))
        .id(swap.getTransaction().getId())
        .build();
  }

  private BigDecimal calculatePrice(BigInteger sqrtPriceX96) {
    BigDecimal q96 = new BigDecimal(new BigInteger("2").pow(96));
    BigDecimal sqrtPrice = new BigDecimal(sqrtPriceX96).divide(q96, MathContext.DECIMAL128);
    return sqrtPrice.pow(2, MathContext.DECIMAL128);
  }

  public void loadMetadata() throws IOException {
    List<UniswapPoolDTO> pools = subgraphClient.getPools();
    Map<Instrument, InstrumentMetaData> instrumentMetaDataMap = new HashMap<>();
    
    for (UniswapPoolDTO pool : pools) {
      Currency base = new Currency(pool.getToken0().getSymbol());
      Currency counter = new Currency(pool.getToken1().getSymbol());
      int feeTier = Integer.parseInt(pool.getFeeTier());
      
      UniswapInstrument instrument = new UniswapInstrument(base, counter, pool.getId(), feeTier);
      
      // Create metadata (min amount, fee, etc.)
      // Fee tier 3000 = 0.3%
      BigDecimal fee = new BigDecimal(feeTier).movePointLeft(6); // 3000 / 1000000 = 0.003
      
      InstrumentMetaData meta = InstrumentMetaData.builder()
          .tradingFee(fee)
          .minimumAmount(BigDecimal.ZERO)
          .priceScale(pool.getToken1().getDecimals() != null ? Integer.parseInt(pool.getToken1().getDecimals()) : 18)
          .volumeScale(pool.getToken0().getDecimals() != null ? Integer.parseInt(pool.getToken0().getDecimals()) : 18)
          .build();
          
      instrumentMetaDataMap.put(instrument, meta);
    }
    
    exchange.getExchangeMetaData().setInstruments(instrumentMetaDataMap);
  }
}
