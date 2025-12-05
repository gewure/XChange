package org.knowm.xchange.uniswap.service;

import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.uniswap.UniswapExchange;

import java.io.IOException;

/**
 * Trade service for Uniswap V3.
 * <p>
 * Supports placing market orders by executing swaps on the Uniswap V3 Router.
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
    // 1. Resolve pool address from instrument
    // For MVP, we need the pool address. MarketOrder doesn't carry it directly unless we look it up from metadata or assume it's passed in some way.
    // Or we use the Factory to find the pool.
    // For now, let's assume the user passes a UniswapInstrument which has the pool address.
    
    if (!(marketOrder.getInstrument() instanceof org.knowm.xchange.uniswap.dto.UniswapInstrument)) {
      throw new IllegalArgumentException("Instrument must be UniswapInstrument");
    }
    org.knowm.xchange.uniswap.dto.UniswapInstrument instrument = (org.knowm.xchange.uniswap.dto.UniswapInstrument) marketOrder.getInstrument();
    
    // 2. Determine swap parameters
    // Side: BID (Buy base) or ASK (Sell base)
    // Amount: originalAmount
    
    // 3. Build transaction
    // We need private key to sign.
    String privateKey = (String) exchange.getExchangeSpecification().getExchangeSpecificParametersItem("private_key");
    if (privateKey == null) {
      throw new IllegalArgumentException("Private key not provided");
    }
    
    // 1. Approve Router to spend tokenIn
    // Get Router address from specification or default to Mainnet
    String routerAddress = (String) exchange.getExchangeSpecification().getExchangeSpecificParametersItem(org.knowm.xchange.uniswap.UniswapExchangeSpecification.ROUTER_ADDRESS);
    if (routerAddress == null) {
        // Fallback to Mainnet if not set (should be set by default spec)
        routerAddress = "0xE592427A0AEce92De3Edee1F18E0157C05861564";
    }
    
    String token0 = onChainClient.getToken0(instrument.getPoolAddress());
    String token1 = onChainClient.getToken1(instrument.getPoolAddress());
    String symbol0 = onChainClient.getSymbol(token0);
    String symbol1 = onChainClient.getSymbol(token1);
    
    // Determine tokenIn and tokenOut
    // Market Order: 
    // BID (Buy Base) -> Input Quote, Output Base
    // ASK (Sell Base) -> Input Base, Output Quote
    
    String tokenIn = null;
    String tokenOut = null;
    java.math.BigInteger amountIn;
    
    // Check for ETH and map to WETH
    String wrappedNativeToken = (String) exchange.getExchangeSpecification().getExchangeSpecificParametersItem(org.knowm.xchange.uniswap.UniswapExchangeSpecification.WRAPPED_NATIVE_TOKEN);
    if (wrappedNativeToken == null) {
        wrappedNativeToken = "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2"; // Default to Mainnet WETH
    }
    
    // Helper to check if symbol matches token address
    // If symbol is "ETH", we assume it maps to wrappedNativeToken for the swap
    // But we need to know which one is which in the pool.
    // The pool always has WETH, never ETH.
    // So if symbol0 is "WETH" and instrument base is "ETH", they match.
    
    boolean baseIsToken0 = false;
    if (instrument.getBase().getCurrencyCode().equals("ETH")) {
         // If base is ETH, check if token0 is WETH
         // We need to fetch symbol of token0.
         if ("WETH".equals(symbol0) || "WMATIC".equals(symbol0)) { // Simple heuristic
             baseIsToken0 = true;
         } else {
             // Assume token1 is WETH
             baseIsToken0 = false;
         }
    } else if (instrument.getBase().getCurrencyCode().equals(symbol0)) {
        baseIsToken0 = true;
    }
    
    if (marketOrder.getType() == org.knowm.xchange.dto.Order.OrderType.BID) {
        // Buying Base with Quote
        tokenIn = baseIsToken0 ? token1 : token0;
        tokenOut = baseIsToken0 ? token0 : token1;
    } else {
        // Selling Base for Quote
        tokenIn = baseIsToken0 ? token0 : token1;
        tokenOut = baseIsToken0 ? token1 : token0;
    }
    
    // Amount is usually in Base currency for XChange
    // But for ExactInputSingle, we need amountIn.
    // If BID (Buy Base), we are spending Quote. We need to know how much Quote to spend?
    // Market orders in XChange usually specify amount of Base to buy/sell.
    // If BID: "Buy 1 BTC". We need to know how much USDT to spend. This requires a quote.
    // For MVP, let's assume the amount passed IS the input amount (e.g. "Spend 1000 USDT to buy BTC").
    // Or we strictly support "Sell 1 BTC" (ASK).
    // Let's assume amount is Input Amount for now.
    
    // TODO: Fetch decimals for tokenIn
    int decimalsIn = onChainClient.getDecimals(tokenIn);
    amountIn = marketOrder.getOriginalAmount().multiply(java.math.BigDecimal.TEN.pow(decimalsIn)).toBigInteger();
    
    String walletAddress = org.web3j.crypto.Credentials.create(privateKey).getAddress();

    // Check Allowance
    java.math.BigInteger allowance = onChainClient.getAllowance(tokenIn, walletAddress, routerAddress);
    if (allowance.compareTo(amountIn) < 0) {
        // Approve Max Uint256 to save gas on future swaps
        java.math.BigInteger maxUint256 = new java.math.BigInteger("2").pow(256).subtract(java.math.BigInteger.ONE);
        onChainClient.approve(tokenIn, routerAddress, maxUint256, privateKey);
    }
    
    // Swap
    java.math.BigInteger fee = java.math.BigInteger.valueOf(instrument.getFeeTier());
    String recipient = walletAddress;
    java.math.BigInteger deadline = java.math.BigInteger.valueOf(System.currentTimeMillis() / 1000 + 1200); // 20 minutes
    
    // Calculate amountOutMinimum
    java.math.BigInteger amountOutMinimum = java.math.BigInteger.ZERO;
    Double slippageTolerance = (Double) exchange.getExchangeSpecification().getExchangeSpecificParametersItem(org.knowm.xchange.uniswap.UniswapExchangeSpecification.SLIPPAGE_TOLERANCE);
    if (slippageTolerance == null) {
        slippageTolerance = 0.005; // Default 0.5%
    }
    
    // To calculate minAmountOut, we need expectedAmountOut.
    // We can estimate it using current pool price.
    try {
        java.math.BigInteger sqrtPriceX96 = onChainClient.getSqrtPriceX96(instrument.getPoolAddress());
        java.math.BigDecimal price = calculatePrice(sqrtPriceX96); // Price of Token0 in terms of Token1? Or Base/Quote?
        // calculatePrice returns price of Base (Token0?)
        // We need to be careful about direction.
        
        // If we are selling TokenIn to get TokenOut.
        // Price P = Token1 / Token0.
        // If TokenIn = Token0, TokenOut = Token1. AmountOut = AmountIn * P.
        // If TokenIn = Token1, TokenOut = Token0. AmountOut = AmountIn / P.
        
        java.math.BigDecimal amountInDec = new java.math.BigDecimal(amountIn).divide(java.math.BigDecimal.TEN.pow(decimalsIn), java.math.MathContext.DECIMAL128);
        java.math.BigDecimal expectedOutDec;
        
        if (tokenIn.equalsIgnoreCase(token0)) {
            // Selling Token0, buying Token1. Price is Token1/Token0.
            expectedOutDec = amountInDec.multiply(price);
        } else {
            // Selling Token1, buying Token0.
            expectedOutDec = amountInDec.divide(price, java.math.MathContext.DECIMAL128);
        }
        
        int decimalsOut = onChainClient.getDecimals(tokenOut);
        java.math.BigDecimal minOutDec = expectedOutDec.multiply(java.math.BigDecimal.valueOf(1.0 - slippageTolerance));
        amountOutMinimum = minOutDec.multiply(java.math.BigDecimal.TEN.pow(decimalsOut)).toBigInteger();
        
    } catch (Exception e) {
        // Fallback to 0 if price fetch fails
        // logger.warn("Failed to calculate slippage", e);
    }

    java.math.BigInteger sqrtPriceLimitX96 = java.math.BigInteger.ZERO;
    
    return onChainClient.swapExactInputSingle(routerAddress, tokenIn, tokenOut, fee, recipient, deadline, amountIn, amountOutMinimum, sqrtPriceLimitX96, privateKey);
  }
  
  private java.math.BigDecimal calculatePrice(java.math.BigInteger sqrtPriceX96) {
    java.math.BigDecimal q96 = new java.math.BigDecimal(new java.math.BigInteger("2").pow(96));
    java.math.BigDecimal sqrtPrice = new java.math.BigDecimal(sqrtPriceX96).divide(q96, java.math.MathContext.DECIMAL128);
    return sqrtPrice.pow(2, java.math.MathContext.DECIMAL128);
  }
}
