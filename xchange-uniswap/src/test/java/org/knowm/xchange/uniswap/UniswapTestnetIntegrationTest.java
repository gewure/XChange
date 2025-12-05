package org.knowm.xchange.uniswap;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.uniswap.config.UniswapNetwork;
import org.knowm.xchange.uniswap.dto.UniswapInstrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Integration test for running against a real testnet (e.g. Sepolia, Goerli).
 * Requires a private key with funds.
 */
@Ignore("Manual integration test requiring private key and funds")
public class UniswapTestnetIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(UniswapTestnetIntegrationTest.class);
    
    private Exchange exchange;
    private TradeService tradeService;
    private AccountService accountService;
    
    // Configure these before running
    private static final String PRIVATE_KEY = System.getProperty("uniswap.privateKey");
    private static final String RPC_URI = "https://rpc.sepolia.org"; // Example Sepolia RPC
    private static final UniswapNetwork NETWORK = UniswapNetwork.ETHEREUM; // Or create a custom one for Sepolia if needed
    
    // Testnet addresses (Sepolia examples - need to be verified)
    // WETH Sepolia: 0xfFf9976782d46CC05630D1f6eBAb18b2324d6B14
    // UNI Sepolia: 0x1f9840a85d5aF5bf1D1762F925BDADdC4201F984
    // Pool UNI/WETH 0.3%: ...
    
    @Before
    public void setUp() {
        if (PRIVATE_KEY == null) {
            logger.warn("Skipping test: uniswap.privateKey system property not set");
            return;
        }
        
        UniswapExchangeSpecification spec = new UniswapExchangeSpecification();
        spec.setNetwork(NETWORK); // Note: Mainnet config might not work for Sepolia unless we override RPC/Addresses
        spec.setRpcUri(RPC_URI);
        spec.setApiKey(org.web3j.crypto.Credentials.create(PRIVATE_KEY).getAddress());
        spec.setExchangeSpecificParametersItem("private_key", PRIVATE_KEY);
        spec.setExchangeSpecificParametersItem(UniswapExchangeSpecification.TRACKED_TOKENS, "0xfFf9976782d46CC05630D1f6eBAb18b2324d6B14"); // WETH Sepolia
        
        exchange = ExchangeFactory.INSTANCE.createExchange(spec);
        tradeService = exchange.getTradeService();
        accountService = exchange.getAccountService();
    }
    
    @Test
    public void testAccountInfo() throws IOException {
        if (exchange == null) return;
        
        AccountInfo accountInfo = accountService.getAccountInfo();
        logger.info("Account Info: {}", accountInfo);
    }
    
    @Test
    public void testSwap() throws IOException {
        if (exchange == null) return;
        
        // Define instrument
        // Need valid pool address for Sepolia
        String poolAddress = "0x..."; 
        UniswapInstrument instrument = new UniswapInstrument(new Currency("WETH"), new Currency("UNI"), poolAddress, 3000);
        
        // Swap WETH for UNI
        MarketOrder order = new MarketOrder(Order.OrderType.BID, new BigDecimal("0.001"), instrument);
        String txHash = tradeService.placeMarketOrder(order);
        logger.info("Swap Tx: {}", txHash);
    }
}
