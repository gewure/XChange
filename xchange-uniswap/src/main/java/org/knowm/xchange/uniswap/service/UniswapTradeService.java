package org.knowm.xchange.uniswap.service;

import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.uniswap.UniswapExchange;
import org.knowm.xchange.uniswap.dto.UniswapInstrument;

import java.io.IOException;

/**
 * Trade service for Uniswap V3.
 * <p>
 * Supports placing limit and market orders by executing swaps on the Uniswap V3 Router.
 * Automatically handles ERC-20 approvals and token resolution.
 * </p>
 */
public class UniswapTradeService implements TradeService {

  private final UniswapExchange exchange;
  private final UniswapOnChainClient onChainClient;

  public UniswapTradeService(UniswapExchange exchange) {
    this.exchange = exchange;
    this.onChainClient = new UniswapOnChainClient(exchange);
  }

  @Override
  public String placeMarketOrder(MarketOrder marketOrder) throws IOException {
    return executeSwap(marketOrder, null);
  }

  @Override
  public String placeLimitOrder(LimitOrder limitOrder) throws IOException {
    return executeSwap(limitOrder, limitOrder.getLimitPrice());
  }

  private String executeSwap(Order order, java.math.BigDecimal limitPrice) throws IOException {
    org.knowm.xchange.instrument.Instrument resolved = resolveInstrument(order.getInstrument());
    if (!(resolved instanceof UniswapInstrument)) {
      throw new IllegalArgumentException("Instrument must be UniswapInstrument: " + order.getInstrument());
    }
    UniswapInstrument instrument = (UniswapInstrument) resolved;
    
    // We need private key to sign.
    String privateKey = (String) exchange.getExchangeSpecification().getExchangeSpecificParametersItem("private_key");
    if (privateKey == null || privateKey.isEmpty()) {
      privateKey = exchange.getExchangeSpecification().getSecretKey();
    }
    if (privateKey == null || privateKey.isEmpty()) {
      throw new IllegalArgumentException("Private key not provided");
    }
    
    String routerAddress = (String) exchange.getExchangeSpecification().getExchangeSpecificParametersItem(org.knowm.xchange.uniswap.UniswapExchangeSpecification.ROUTER_ADDRESS);
    if (routerAddress == null) {
        routerAddress = "0xE592427A0AEce92De3Edee1F18E0157C05861564";
    }
    
    String token0 = onChainClient.getToken0(instrument.getPoolAddress());
    String token1 = onChainClient.getToken1(instrument.getPoolAddress());
    String symbol0 = onChainClient.getSymbol(token0);
    String symbol1 = onChainClient.getSymbol(token1);
    
    boolean baseIsToken0 = false;
    String canonicalBase = canonical(instrument.getBase().getCurrencyCode());
    String canonicalSymbol0 = canonical(symbol0);
    if (canonicalBase.equals("ETH")) {
         if ("ETH".equals(canonicalSymbol0) || "WMATIC".equals(canonicalSymbol0)) {
             baseIsToken0 = true;
         }
    } else if (canonicalBase.equals(canonicalSymbol0)) {
        baseIsToken0 = true;
    }
    
    String tokenIn;
    String tokenOut;
    
    if (order.getType() == Order.OrderType.BID) {
        tokenIn = baseIsToken0 ? token1 : token0;
        tokenOut = baseIsToken0 ? token0 : token1;
    } else {
        tokenIn = baseIsToken0 ? token0 : token1;
        tokenOut = baseIsToken0 ? token1 : token0;
    }
    
    int decimalsIn = onChainClient.getDecimals(tokenIn);
    int decimalsOut = onChainClient.getDecimals(tokenOut);
    
    java.math.BigInteger amountIn;
    java.math.BigInteger amountOutMinimum = java.math.BigInteger.ZERO;
    
    // Slippage tolerance
    Double slippageTolerance = (Double) exchange.getExchangeSpecification().getExchangeSpecificParametersItem(org.knowm.xchange.uniswap.UniswapExchangeSpecification.SLIPPAGE_TOLERANCE);
    if (slippageTolerance == null) {
        slippageTolerance = 0.005; // Default 0.5%
    }
    
    java.math.BigInteger sqrtPriceX96 = onChainClient.getSqrtPriceX96(instrument.getPoolAddress());
    java.math.BigDecimal poolPrice = calculatePrice(sqrtPriceX96); // token1 per token0
    java.math.BigDecimal basePriceInQuote = baseIsToken0 ? poolPrice : java.math.BigDecimal.ONE.divide(poolPrice, java.math.MathContext.DECIMAL128);
    
    if (order.getType() == Order.OrderType.BID) {
        // BID: We want to buy base, selling quote.
        boolean isFaastRep = false;
        if (limitPrice != null) {
            java.math.BigDecimal diffFaast = limitPrice.subtract(java.math.BigDecimal.ONE.divide(basePriceInQuote, java.math.MathContext.DECIMAL128)).abs();
            java.math.BigDecimal diffStandard = limitPrice.subtract(basePriceInQuote).abs();
            if (diffFaast.compareTo(diffStandard) < 0) {
                isFaastRep = true;
            }
        } else {
            java.math.BigDecimal expectedQuoteForOneBase = basePriceInQuote;
            java.math.BigDecimal originalAmount = order.getOriginalAmount();
            if (originalAmount.compareTo(expectedQuoteForOneBase.multiply(java.math.BigDecimal.valueOf(0.1))) > 0 &&
                originalAmount.compareTo(expectedQuoteForOneBase.multiply(java.math.BigDecimal.valueOf(10.0))) < 0) {
                isFaastRep = true;
            }
        }
        
        if (isFaastRep) {
            amountIn = order.getOriginalAmount().multiply(java.math.BigDecimal.TEN.pow(decimalsIn)).toBigInteger();
            java.math.BigDecimal expectedOutDec = order.getOriginalAmount().divide(basePriceInQuote, java.math.MathContext.DECIMAL128);
            java.math.BigDecimal minOutDec = expectedOutDec.multiply(java.math.BigDecimal.valueOf(1.0 - slippageTolerance));
            amountOutMinimum = minOutDec.multiply(java.math.BigDecimal.TEN.pow(decimalsOut)).toBigInteger();
        } else {
            java.math.BigDecimal quoteAmount = order.getOriginalAmount().multiply(basePriceInQuote);
            amountIn = quoteAmount.multiply(java.math.BigDecimal.TEN.pow(decimalsIn)).toBigInteger();
            java.math.BigDecimal minOutDec = order.getOriginalAmount().multiply(java.math.BigDecimal.valueOf(1.0 - slippageTolerance));
            amountOutMinimum = minOutDec.multiply(java.math.BigDecimal.TEN.pow(decimalsOut)).toBigInteger();
        }
    } else {
        // ASK: We sell base, buying quote.
        amountIn = order.getOriginalAmount().multiply(java.math.BigDecimal.TEN.pow(decimalsIn)).toBigInteger();
        java.math.BigDecimal expectedOutDec = order.getOriginalAmount().multiply(basePriceInQuote);
        java.math.BigDecimal minOutDec = expectedOutDec.multiply(java.math.BigDecimal.valueOf(1.0 - slippageTolerance));
        amountOutMinimum = minOutDec.multiply(java.math.BigDecimal.TEN.pow(decimalsOut)).toBigInteger();
    }
    
    String walletAddress = org.web3j.crypto.Credentials.create(privateKey).getAddress();
    
    // Check Allowance
    java.math.BigInteger allowance = onChainClient.getAllowance(tokenIn, walletAddress, routerAddress);
    if (allowance.compareTo(amountIn) < 0) {
        java.math.BigInteger maxUint256 = new java.math.BigInteger("2").pow(256).subtract(java.math.BigInteger.ONE);
        onChainClient.approve(tokenIn, routerAddress, maxUint256, privateKey);
    }
    
    java.math.BigInteger fee = java.math.BigInteger.valueOf(instrument.getFeeTier());
    java.math.BigInteger deadline = java.math.BigInteger.valueOf(System.currentTimeMillis() / 1000 + 1200);
    java.math.BigInteger sqrtPriceLimitX96 = java.math.BigInteger.ZERO;
    
    return onChainClient.swapExactInputSingle(routerAddress, tokenIn, tokenOut, fee, walletAddress, deadline, amountIn, amountOutMinimum, sqrtPriceLimitX96, privateKey);
  }

  private java.math.BigDecimal calculatePrice(java.math.BigInteger sqrtPriceX96) {
    java.math.BigDecimal q96 = new java.math.BigDecimal(new java.math.BigInteger("2").pow(96));
    java.math.BigDecimal sqrtPrice = new java.math.BigDecimal(sqrtPriceX96).divide(q96, java.math.MathContext.DECIMAL128);
    return sqrtPrice.pow(2, java.math.MathContext.DECIMAL128);
  }

  private String canonical(String symbol) {
    if (symbol == null) return null;
    String upper = symbol.toUpperCase();
    if ("WBTC".equals(upper) || "WETH".equals(upper) || "WMATIC".equals(upper)) {
      if ("WBTC".equals(upper)) return "BTC";
      if ("WETH".equals(upper)) return "ETH";
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
    String canonBase = canonical(requested.getBase().getCurrencyCode());
    String canonCounter = canonical(requested.getCounter().getCurrencyCode());
    for (org.knowm.xchange.instrument.Instrument instr : exchange.getExchangeMetaData().getInstruments().keySet()) {
      if (canonical(instr.getBase().getCurrencyCode()).equals(canonBase) &&
          canonical(instr.getCounter().getCurrencyCode()).equals(canonCounter)) {
        return instr;
      }
    }
    return requested;
  }
}
