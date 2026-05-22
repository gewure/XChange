package org.knowm.xchange.uniswap.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.uniswap.UniswapExchange;
import org.knowm.xchange.uniswap.UniswapExchangeSpecification;
import org.knowm.xchange.uniswap.dto.UniswapPoolDTO;
import org.knowm.xchange.uniswap.dto.UniswapSwap;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UniswapSubgraphClientTest {

    private static HttpServer subgraphServer;
    private static HttpServer rpcServer;
    private static int subgraphPort;
    private static int rpcPort;

    @BeforeAll
    public static void setup() throws Exception {
        subgraphServer = HttpServer.create(new InetSocketAddress(0), 0);
        subgraphServer.createContext("/subgraphs/id/test", exchange -> {
            String requestMethod = exchange.getRequestMethod();
            if (requestMethod.equalsIgnoreCase("POST")) {
                // Mock JSON response for pools and swaps depending on request body
                String body = new String(exchange.getRequestBody().readAllBytes());
                String response = "";
                if (body.contains("pools")) {
                    response = "{\"data\": {\"pools\": [" +
                        "{\"id\": \"0x8ad599c3a0ff1de082011efddc58f1908eb6e6d8\", \"token0\": {\"symbol\": \"USDC\", \"decimals\": \"6\", \"id\": \"0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48\"}, \"token1\": {\"symbol\": \"WETH\", \"decimals\": \"18\", \"id\": \"0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2\"}, \"feeTier\": \"3000\"}" +
                        "]}}";
                } else if (body.contains("swaps")) {
                    response = "{\"data\": {\"swaps\": [" +
                        "{\"transaction\": {\"id\": \"0x123\"}, \"timestamp\": \"1620000000\", \"amount0\": \"1000\", \"amount1\": \"-0.5\", \"amountUSD\": \"1000\"}" +
                        "]}}";
                }
                
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        });
        subgraphServer.start();
        subgraphPort = subgraphServer.getAddress().getPort();
        
        rpcServer = HttpServer.create(new InetSocketAddress(0), 0);
        rpcServer.createContext("/", exchange -> {
            String response = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"0x1\"}";
            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        });
        rpcServer.start();
        rpcPort = rpcServer.getAddress().getPort();
    }

    @AfterAll
    public static void teardown() {
        if (subgraphServer != null) {
            subgraphServer.stop(0);
        }
        if (rpcServer != null) {
            rpcServer.stop(0);
        }
    }

    @Test
    public void testGetPoolsParsing() throws Exception {
        UniswapExchangeSpecification spec = new UniswapExchangeSpecification(UniswapExchange.class);
        spec.setSubgraphUri("http://localhost:" + subgraphPort + "/subgraphs/id/test");
        spec.setRpcUri("http://localhost:" + rpcPort); // valid HTTP endpoint
        UniswapExchange exchange = (UniswapExchange) ExchangeFactory.INSTANCE.createExchange(spec);
        
        UniswapSubgraphClient client = new UniswapSubgraphClient(exchange);
        List<UniswapPoolDTO> pools = client.getPools();
        
        assertNotNull(pools);
        assertEquals(1, pools.size());
        assertEquals("0x8ad599c3a0ff1de082011efddc58f1908eb6e6d8", pools.get(0).getId());
        assertEquals("3000", pools.get(0).getFeeTier());
        assertEquals("USDC", pools.get(0).getToken0().getSymbol());
    }

    @Test
    public void testGetSwapsParsing() throws Exception {
        UniswapExchangeSpecification spec = new UniswapExchangeSpecification(UniswapExchange.class);
        spec.setSubgraphUri("http://localhost:" + subgraphPort + "/subgraphs/id/test");
        spec.setRpcUri("http://localhost:" + rpcPort);
        UniswapExchange exchange = (UniswapExchange) ExchangeFactory.INSTANCE.createExchange(spec);
        
        UniswapSubgraphClient client = new UniswapSubgraphClient(exchange);
        List<UniswapSwap> swaps = client.getSwaps("0x8ad599c3a0ff1de082011efddc58f1908eb6e6d8");
        
        assertNotNull(swaps);
        assertEquals(1, swaps.size());
        assertEquals("0x123", swaps.get(0).getTransaction().getId());
        assertTrue(swaps.get(0).getAmount0().compareTo(new java.math.BigDecimal("1000")) == 0);
    }
}
