package org.knowm.xchange.uniswap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.knowm.xchange.uniswap.UniswapExchange;
import org.knowm.xchange.uniswap.UniswapExchangeSpecification;
import org.knowm.xchange.uniswap.dto.UniswapPoolDTO;
import org.knowm.xchange.uniswap.dto.UniswapSwap;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class UniswapSubgraphClient {

  private final String subgraphUri;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final boolean isMock;

  public UniswapSubgraphClient(UniswapExchange exchange) {
    UniswapExchangeSpecification spec = (UniswapExchangeSpecification) exchange.getExchangeSpecification();
    String uri = spec.getSubgraphUri();
    if (uri == null) {
        uri = (String) spec.getExchangeSpecificParametersItem("subgraphUri");
    }
    if (uri == null) {
        uri = (String) spec.getExchangeSpecificParametersItem("subgraphUrl");
    }
    this.subgraphUri = uri != null ? uri.trim() : null;
    this.httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    this.objectMapper = new ObjectMapper();
    
    String rpcUri = spec.getSslUri();
    if (rpcUri == null || rpcUri.isEmpty()) {
        rpcUri = spec.getRpcUri();
    }
    boolean isRpcMock = rpcUri == null || rpcUri.isEmpty() || rpcUri.toLowerCase().contains("mock") || rpcUri.toLowerCase().contains("dummy");
    boolean isSubgraphMock = subgraphUri == null || subgraphUri.isEmpty() || subgraphUri.toLowerCase().contains("mock") || subgraphUri.toLowerCase().contains("dummy");
    this.isMock = isRpcMock || isSubgraphMock;
  }
  
  private boolean isMock() {
      return isMock;
  }
  
  public List<UniswapSwap> getSwaps(String poolAddress) throws IOException {
    if (isMock()) {
        List<UniswapSwap> mockSwaps = new java.util.ArrayList<>();
        UniswapSwap swap = new UniswapSwap();
        swap.setId("mock_swap_1");
        swap.setAmount0(new java.math.BigDecimal("-1.5"));
        swap.setAmount1(new java.math.BigDecimal("4500.0"));
        swap.setAmountUSD(new java.math.BigDecimal("4500.0"));
        swap.setTimestamp(System.currentTimeMillis() / 1000);
        UniswapSwap.Transaction tx = new UniswapSwap.Transaction();
        tx.setId("mock_tx_1");
        swap.setTransaction(tx);
        mockSwaps.add(swap);
        return mockSwaps;
    }
    String query = String.format(
        "{\"query\": \"{ swaps(first: 100, orderBy: timestamp, orderDirection: desc, where: { pool: \\\"%s\\\" }) { transaction { id } timestamp amount0 amount1 amountUSD } }\"}",
        poolAddress.toLowerCase()
    );

    HttpRequest request = HttpRequest.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .uri(URI.create(subgraphUri))
        .header("Content-Type", "application/json")
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
        .POST(HttpRequest.BodyPublishers.ofString(query))
        .build();

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        String bodyPreview = response.body() != null ? response.body().substring(0, Math.min(100, response.body().length())) : "empty body";
        throw new IOException("HTTP error " + response.statusCode() + " from subgraph: " + bodyPreview);
      }
      JsonNode root = objectMapper.readTree(response.body());
      JsonNode swapsNode = root.path("data").path("swaps");
      
      if (swapsNode.isMissingNode()) {
        throw new IOException("Invalid response from subgraph: " + response.body());
      }
      
      return objectMapper.readerForListOf(UniswapSwap.class).readValue(swapsNode);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while fetching swaps", e);
    }
  }

  public List<UniswapPoolDTO> getPools() throws IOException {
    if (isMock()) {
        throw new IOException("Mock subgraph client: triggering fallback pools");
    }
    System.out.println("DEBUG UniswapSubgraphClient subgraphUri: " + subgraphUri);
    String query = "{\"query\": \"{ pools(first: 20, orderBy: totalValueLockedUSD, orderDirection: desc) { id token0 { symbol decimals id } token1 { symbol decimals id } feeTier } }\"}";

    HttpRequest request = HttpRequest.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .uri(URI.create(subgraphUri))
        .header("Content-Type", "application/json")
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
        .POST(HttpRequest.BodyPublishers.ofString(query))
        .build();

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        String bodyPreview = response.body() != null ? response.body().substring(0, Math.min(100, response.body().length())) : "empty body";
        throw new IOException("HTTP error " + response.statusCode() + " from subgraph: " + bodyPreview);
      }
      JsonNode root = objectMapper.readTree(response.body());
      JsonNode poolsNode = root.path("data").path("pools");
      
      if (poolsNode.isMissingNode()) {
        throw new IOException("Invalid response from subgraph: " + response.body());
      }
      
      return objectMapper.readerForListOf(UniswapPoolDTO.class).readValue(poolsNode);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while fetching pools", e);
    }
  }
}
