# Arbitrage Bot Integration Guide: Polymarket V3 Adapter

## 1. Prerequisites (No "Extras" Required)
To run this bot, you need three things. No complex infrastructure is required to start.

1.  **Ethereum Wallet**: A private key with some ETH for gas.
    *   *Recommendation*: Generate a fresh wallet using Metamask or `web3j` CLI. **Do not use your main savings wallet.**
2.  **RPC Endpoint**: A URL to talk to the blockchain.
    *   *Free Options*:
        *   **Ethereum**: `https://rpc.ankr.com/eth` or `https://cloudflare-eth.com`
        *   **Sepolia (Testnet)**: `https://rpc.sepolia.org`
        *   **Polygon**: `https://polygon-rpc.com`
    *   *Note*: Free RPCs have rate limits. For production arb, you eventually need a paid provider (Alchemy/Infura/QuickNode).
3.  **Flashbots Relay (Optional but Recommended)**:
    *   **URI**: `https://relay.flashbots.net` (Mainnet) or `https://relay-sepolia.flashbots.net` (Sepolia).
    *   **Signing Key**: A *separate* private key used just to sign messages to the relay (does not need funds).

## 2. Configuration Code
Here is the exact Java code to configure the adapter for an Arbitrage Bot.

```java
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.polymarket.PolymarketExchange;
import org.knowm.xchange.polymarket.PolymarketExchangeSpecification;
import org.knowm.xchange.polymarket.config.PolymarketNetwork;

// 1. Setup Specification
PolymarketExchangeSpecification spec = new PolymarketExchangeSpecification();

// A. Network Selection
// Use PolymarketNetwork enum for presets (Mainnet, Polygon, Arbitrum, Optimism)
// For Testnets (Sepolia), use ETHEREUM but override the RPC and Address.
spec.setNetwork(PolymarketNetwork.ETHEREUM);

// B. RPC Configuration (Crucial for "No Extras" setup)
// Override default RPC with a free public one if you don't have Infura
spec.setRpcUri("https://rpc.ankr.com/eth"); // Mainnet
// spec.setRpcUri("https://rpc.sepolia.org"); // Sepolia Testnet

// C. Wallet Configuration
String myPrivateKey = "0x..."; // Your funded private key
spec.setApiKey(myPrivateKey); // Used for signing transactions
spec.setExchangeSpecificParametersItem("private_key", myPrivateKey);

// D. Flashbots Configuration (MEV Protection)
// This enables "Private Transactions" to avoid sandwich attacks.
// If set, trades are sent as bundles. If not set, trades go to public mempool.
spec.setExchangeSpecificParametersItem(PolymarketExchangeSpecification.FLASHBOTS_RELAY_URI, "https://relay.flashbots.net");
spec.setExchangeSpecificParametersItem(PolymarketExchangeSpecification.FLASHBOTS_RELAY_SIGNING_KEY, "0x..."); // Different random key

// E. Arbitrage Specifics
spec.setExchangeSpecificParametersItem(PolymarketExchangeSpecification.SLIPPAGE_TOLERANCE, 0.005); // 0.5% Slippage
// Track tokens you want to arb (e.g. WETH, USDC, DAI) to fetch balances automatically
spec.setTrackedTokens(Arrays.asList(
    "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2", // WETH
    "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"  // USDC
));

// 2. Create Exchange
PolymarketExchange exchange = (PolymarketExchange) ExchangeFactory.INSTANCE.createExchange(spec);
```

## 3. Usage Pattern (The Loop)

### Step A: Market Data (The Trigger)
You need to know *when* to trade.
```java
// Option 1: Polling (Slower, easier)
Ticker ticker = exchange.getMarketDataService().getTicker(instrument);
System.out.println("Price: " + ticker.getLast());

// Option 2: Streaming (Faster, recommended for Arb)
exchange.getStreamingMarketDataService().getTrades(instrument)
    .subscribe(trade -> {
        // New trade happened on-chain! Check for arb opportunity.
        checkForArbitrage(trade.getPrice());
    });
```

### Step B: Execution (The Swap)
When you find an opportunity (e.g., Polymarket Price < Binance Price), execute immediately.
```java
// Prepare Order
// Buying 1 ETH for USDC
MarketOrder buyOrder = new MarketOrder(
    Order.OrderType.BID,
    new BigDecimal("1.0"), // Amount
    new PolymarketInstrument(new Currency("WETH"), new Currency("USDC"), "0xPoolAddress...", 3000) // 0.3% Fee Tier
);

// Execute
// This method handles:
// 1. Nonce management (Local)
// 2. Gas estimation (EIP-1559)
// 3. Approval (if needed)
// 4. Flashbots Bundle submission (if configured)
String txHash = exchange.getTradeService().placeMarketOrder(buyOrder);
System.out.println("Trade Sent! Hash: " + txHash);
```

## 4. Testnet Guide (Sepolia)
To test without real money:
1.  **Get Sepolia ETH**: Use a faucet (e.g., `sepoliafaucet.com`).
2.  **Get Tokens**: Swap Sepolia ETH for Polymarket tokens on the official Polymarket Interface (connected to Sepolia).
3.  **Config**:
    ```java
    spec.setNetwork(PolymarketNetwork.ETHEREUM); // Use ETH base config
    spec.setRpcUri("https://rpc.sepolia.org"); // Override RPC
    // You might need to override Router/Factory addresses if they differ on Sepolia,
    // but standard Polymarket V3 addresses are usually the same across testnets.
    // Verify: Router should be 0xE592427A0AEce92De3Edee1F18E0157C05861564
    ```

## 5. Troubleshooting
*   **"Nonce too low"**: The bot sent two txs too fast. The adapter auto-resets, but ensure you aren't running two bot instances with the same key.
*   **"Gas estimation failed"**: You likely don't have enough ETH for gas, or the trade would revert (e.g., slippage too high).
*   **Flashbots 400 Error**: Your bundle is invalid (e.g., targets a past block). The adapter targets `CurrentBlock + 1`.
