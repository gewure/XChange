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

  public UniswapSubgraphClient(UniswapExchange exchange) {
    UniswapExchangeSpecification spec = (UniswapExchangeSpecification) exchange.getExchangeSpecification();
    this.subgraphUri = spec.getSubgraphUri();
    this.httpClient = HttpClient.newHttpClient();
    this.objectMapper = new ObjectMapper();
  }
  
  public List<UniswapSwap> getSwaps(String poolAddress) throws IOException {
    String query = String.format(
        "{\"query\": \"{ swaps(first: 100, orderBy: timestamp, orderDirection: desc, where: { pool: \\\"%s\\\" }) { transaction { id } timestamp amount0 amount1 amountUSD } }\"}",
        poolAddress.toLowerCase()
    );

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(subgraphUri))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(query))
        .build();

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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
    String query = "{\"query\": \"{ pools(first: 20, orderBy: totalValueLockedUSD, orderDirection: desc) { id token0 { symbol decimals id } token1 { symbol decimals id } feeTier } }\"}";

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(subgraphUri))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(query))
        .build();

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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
