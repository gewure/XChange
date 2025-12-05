# XChange Uniswap

This module provides an XChange adapter for Uniswap V3 on Ethereum.

## Features

- **Market Data**: Fetch pool metadata (tickers, instruments) and trade history.
- **Account Service**: Fetch ETH balance.
- **Streaming**: Stream real-time `Swap` events (trades) and tickers via WebSocket.

## Limitations

- **Swaps**: Actual on-chain swap execution is **NOT** fully implemented. The `placeMarketOrder` method exists but throws an exception because it requires generating Web3j wrappers for the Uniswap V3 Router contract, which is outside the scope of this MVP.
- **Token Balances**: Currently only fetches ETH balance. ERC-20 token balances require ERC-20 contract wrappers.
- **Order Book**: As an AMM, Uniswap does not have a traditional order book. `getOrderBook` returns empty.

### Configuration

Use `UniswapExchangeSpecification` to configure the exchange. You can easily switch between supported EVM networks (Ethereum, Polygon, Arbitrum, Optimism) using the `setNetwork` method.

```java
UniswapExchangeSpecification spec = new UniswapExchangeSpecification();
// Switch to Polygon
spec.setNetwork(UniswapNetwork.POLYGON);
// Optional: Override RPC URI if needed
spec.setRpcUri("https://your-custom-rpc.com");
// Set private key for trading
spec.setExchangeSpecificParametersItem("private_key", "YOUR_PRIVATE_KEY");

Exchange exchange = ExchangeFactory.INSTANCE.createExchange(spec);
```

Supported Networks:
- `ETHEREUM` (Default)
- `POLYGON`
- `ARBITRUM`
- `OPTIMISM`

## Streaming

To use streaming, include `xchange-stream-uniswap` and provide a WebSocket URI:

```java
spec.setExchangeSpecificParametersItem("streaming_uri", "wss://mainnet.infura.io/ws/v3/YOUR_KEY");
StreamingExchange exchange = (StreamingExchange) ExchangeFactory.INSTANCE.createExchange(spec);
exchange.connect().blockingAwait();
```

## Examples

See `org.knowm.xchange.examples.uniswap.UniswapExample` in `xchange-examples` for a full demonstration.
