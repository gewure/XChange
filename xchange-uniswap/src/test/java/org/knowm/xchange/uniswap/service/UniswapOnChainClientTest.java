package org.knowm.xchange.uniswap.service;

import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.uniswap.UniswapExchange;
import org.knowm.xchange.uniswap.UniswapExchangeSpecification;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class UniswapOnChainClientTest {

    @Test
    public void testMockClientReturnsFallbackValues() throws Exception {
        UniswapExchangeSpecification spec = new UniswapExchangeSpecification(UniswapExchange.class);
        spec.setRpcUri("http://mock-rpc.localhost");
        UniswapExchange exchange = (UniswapExchange) ExchangeFactory.INSTANCE.createExchange(spec);
        
        UniswapOnChainClient client = new UniswapOnChainClient(exchange);
        
        // Test Balance Fallback
        BigInteger balance = client.getBalance("0xDummy");
        assertEquals(new BigInteger("100000000000000000000"), balance);
        
        // Test Token Decimals Fallback
        int decimalsWeth = client.getDecimals("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2");
        assertEquals(18, decimalsWeth);
        
        int decimalsUsdt = client.getDecimals("0xdac17f958d2ee523a2206206994597c13d831ec7");
        assertEquals(6, decimalsUsdt);
        
        // Test Raw Payload Execution Fallback
        String txHash = client.sendRawTransactionPayload("0xRouter", "0xdata", BigInteger.ZERO, "dummyKey");
        assertEquals("mock_raw_payload_tx_hash", txHash);
        
        // Test Gas/Swap Execution Fallback
        String swapTxHash = client.swapExactInputSingle("0xRouter", "0xIn", "0xOut", BigInteger.ZERO, "0xRecipient", BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, "dummyKey");
        assertEquals("mock_swap_tx_hash", swapTxHash);
    }
}
