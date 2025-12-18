package org.knowm.xchange.examples.uniswap;

import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import io.reactivex.rxjava3.disposables.Disposable;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.uniswap.dto.UniswapInstrument;
import org.knowm.xchange.uniswap.stream.UniswapStreamingExchange;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A real-world test that listens to prices on Uniswap V3 and then executes a trade.
 * <p>
 * Configuration (Environment Variables):
 * - UNISWAP_WALLET_ADDRESS: Your wallet address (public key).
 * - UNISWAP_PRIVATE_KEY: Your wallet private key (keep secret!).
 * - UNISWAP_RPC_URI: Ethereum RPC URL (e.g., https://mainnet.infura.io/v3/...).
 * - UNISWAP_STREAMING_URI: Ethereum WebSocket RPC URL (e.g., wss://mainnet.infura.io/ws/v3/...).
 * </p>
 */
public class UniswapRealWorld {

    public static void main(String[] args) throws IOException, InterruptedException {
        // 1. Configuration
        String walletAddress = getEnv("UNISWAP_WALLET_ADDRESS", "0xYourWalletAddress");
        String privateKey = getEnv("UNISWAP_PRIVATE_KEY", "0xYourPrivateKey");
        String rpcUri = getEnv("UNISWAP_RPC_URI", "https://mainnet.infura.io/v3/YOUR_INFURA_KEY");
        String streamingUri = getEnv("UNISWAP_STREAMING_URI", "wss://mainnet.infura.io/ws/v3/YOUR_INFURA_KEY");

        System.out.println("--- Uniswap Real World Test ---");
        System.out.println("Wallet: " + walletAddress);
        System.out.println("RPC: " + rpcUri);

        if (privateKey.equals("0xYourPrivateKey")) {
            System.err.println("WARNING: Using dummy private key. Trade execution will likely fail or be simulated if supported.");
        }

        ExchangeSpecification spec = new ExchangeSpecification(UniswapStreamingExchange.class);
        spec.setExchangeSpecificParametersItem("rpc_uri", rpcUri);
        spec.setExchangeSpecificParametersItem("streaming_uri", streamingUri);
        spec.setExchangeSpecificParametersItem("wallet_address", walletAddress);
        spec.setExchangeSpecificParametersItem("private_key", privateKey);
        // spec.setExchangeSpecificParametersItem("subgraph_uri", "..."); // Optional if needed for metadata

        // 2. Initialize Exchange
        StreamingExchange exchange = (StreamingExchange) ExchangeFactory.INSTANCE.createExchange(spec);
        exchange.connect().blockingAwait();

        // 3. Define Instrument (ETH/USDT 0.05% Pool)
        // Pool: 0x11b815efB8f581194ae79006d24E0d814B7697F6 (USDT/WETH 500)
        // Note: USDT is usually Counter, ETH is Base.
        UniswapInstrument ethUsdt = new UniswapInstrument(Currency.ETH, Currency.USDT, "0x11b815efB8f581194ae79006d24E0d814B7697F6", 500);

        // 4. Listen to prices (Streaming)
        System.out.println("--- Listening to prices for 5 seconds ---");
        StreamingMarketDataService streamingService = exchange.getStreamingMarketDataService();

        CountDownLatch latch = new CountDownLatch(1);
        Disposable sub = streamingService.getTrades(ethUsdt)
                .subscribe(
                        trade -> System.out.println("Trade: " + trade),
                        throwable -> System.err.println("Error streaming trades: " + throwable.getMessage())
                );

        // Wait for 5 seconds to simulate "listening before"
        latch.await(5, TimeUnit.SECONDS);
        sub.dispose();

        // 5. Execute Trade
        System.out.println("--- Executing Trade ---");
        System.out.println("Buying ETH with 1 USDT...");

        TradeService tradeService = exchange.getTradeService();

        // Order: Buy ETH (Base) using 1 USDT (Counter/Quote)
        // The Uniswap adapter implementation interprets 'originalAmount' as the input amount for the swap.
        // For a BID (Buy Base), Input is Quote (USDT).
        MarketOrder order = new MarketOrder(
                Order.OrderType.BID,
                new BigDecimal("1"), // 1 USDT
                ethUsdt
        );

        try {
            String txHash = tradeService.placeMarketOrder(order);
            System.out.println("Order Placed! Transaction Hash: " + txHash);
            System.out.println("View on Etherscan: https://etherscan.io/tx/" + txHash);
        } catch (Exception e) {
            System.err.println("Trade failed: " + e.getMessage());
            e.printStackTrace();
        }

        // Cleanup
        exchange.disconnect().blockingAwait();
        System.out.println("--- Done ---");
    }

    private static String getEnv(String key, String defaultValue) {
        String val = System.getenv(key);
        return val != null ? val : defaultValue;
    }
}
