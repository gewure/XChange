package org.knowm.xchange.uniswap.stream;

import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.uniswap.UniswapExchangeSpecification;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UniswapStreamingExchangeTest {

    @Test
    public void testMockInitializationAndFallbackPools() throws Exception {
        UniswapExchangeSpecification spec = new UniswapExchangeSpecification(UniswapStreamingExchange.class);
        spec.setRpcUri("http://mock-rpc.localhost");
        spec.setSubgraphUri("http://mock-subgraph.localhost");
        spec.setExchangeSpecificParametersItem("streaming_uri", "wss://mock-wss.localhost");
        
        UniswapStreamingExchange exchange = (UniswapStreamingExchange) ExchangeFactory.INSTANCE.createExchange(spec);
        
        // Connect should initialize remote metadata using mock subgraph/onchain client
        exchange.connect().blockingAwait();
        
        // Assert that instruments were loaded (even if fallback)
        assertNotNull(exchange.getExchangeMetaData());
        assertNotNull(exchange.getExchangeMetaData().getInstruments());
        assertTrue(exchange.getExchangeMetaData().getInstruments().size() > 0, "Fallback pools should be loaded into metadata");
        
        // Verify streaming service is instantiated
        assertNotNull(exchange.getStreamingMarketDataService());
        
        // Cleanup
        exchange.disconnect().blockingAwait();
    }
}
