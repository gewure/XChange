package org.knowm.xchange.examples.uniswap;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.uniswap.UniswapExchange;
import org.knowm.xchange.uniswap.dto.UniswapInstrument;
import org.knowm.xchange.uniswap.stream.UniswapStreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingMarketDataService;

import java.io.IOException;
import java.math.BigDecimal;

public class UniswapExample {

    public static void main(String[] args) throws IOException, InterruptedException {

        // 1. Setup Exchange Specification
        ExchangeSpecification spec = new ExchangeSpecification(UniswapStreamingExchange.class);
        // Note: Users should replace these with their own endpoints
        spec.setExchangeSpecificParametersItem("rpc_uri", "https://mainnet.infura.io/v3/YOUR_INFURA_KEY");
        spec.setExchangeSpecificParametersItem("subgraph_uri",
                "https://api.thegraph.com/subgraphs/name/uniswap/uniswap-v3");
        spec.setExchangeSpecificParametersItem("streaming_uri", "wss://mainnet.infura.io/ws/v3/YOUR_INFURA_KEY");
        spec.setExchangeSpecificParametersItem("wallet_address", "0xYourWalletAddress");
        spec.setExchangeSpecificParametersItem("private_key", "0xYourPrivateKey"); // CAUTION: Never hardcode real keys
                                                                                   // in production!

        // 2. Create Exchange
        Exchange exchange = ExchangeFactory.INSTANCE.createExchange(spec);

        // 3. Market Data (REST/RPC)
        System.out.println("--- Market Data ---");
        MarketDataService marketDataService = exchange.getMarketDataService();

        // Define Instrument: ETH/USDC 0.3% pool
        UniswapInstrument ethUsdc = new UniswapInstrument(Currency.ETH, Currency.USDC,
                "0x8ad599c3a0ff1de082011efddc58f1908eb6e6d8", 3000);

        Ticker ticker = marketDataService.getTicker(ethUsdc);
        System.out.println("Ticker: " + ticker);

        // 4. Account Service
        System.out.println("--- Account Service ---");
        try {
            AccountInfo accountInfo = exchange.getAccountService().getAccountInfo();
            System.out.println("Account Info: " + accountInfo);
        } catch (Exception e) {
            System.out.println("Account Info failed (expected if no valid wallet/RPC): " + e.getMessage());
        }

        // 5. Streaming Market Data
        System.out.println("--- Streaming Market Data ---");
        StreamingExchange streamingExchange = (StreamingExchange) exchange;
        streamingExchange.connect().blockingAwait();

        StreamingMarketDataService streamingMarketDataService = streamingExchange.getStreamingMarketDataService();

        streamingMarketDataService.getTrades(ethUsdc)
                .subscribe(trade -> {
                    System.out.println("Streamed Trade: " + trade);
                }, throwable -> {
                    System.err.println("Error in stream: " + throwable.getMessage());
                });

        // Keep alive for a bit to receive events
        Thread.sleep(10000);

        streamingExchange.disconnect().blockingAwait();
        System.out.println("Done.");
    }
}
