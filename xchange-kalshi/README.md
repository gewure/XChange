# XChange Kalshi

This module provides an XChange adapter for Kalshi.

## Features

- **Market Data**: Fetch order books.
- **Streaming**: Stream real-time order books via WebSocket.

## Limitations

- Kalshi's API uses specific fixed point formats for sizes and dollars for prices. The implementation converts them appropriately.

### Configuration

Use `KalshiExchangeSpecification` to configure the exchange.

```java
ExchangeSpecification spec = new KalshiExchange.getDefaultExchangeSpecification();
Exchange exchange = ExchangeFactory.INSTANCE.createExchange(spec);
```

## Streaming

To use streaming, include `xchange-stream-kalshi`:

```java
StreamingExchange exchange = (StreamingExchange) ExchangeFactory.INSTANCE.createExchange(KalshiStreamingExchange.class);
exchange.connect().blockingAwait();
```
