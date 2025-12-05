package org.knowm.xchange.uniswap.dto;

import lombok.Data;

@Data
public class UniswapPoolDTO {
  private String id;
  private TokenDTO token0;
  private TokenDTO token1;
  private String feeTier; // Subgraph returns feeTier as string sometimes, or int. Let's check. It's usually string in JSON.

  @Data
  public static class TokenDTO {
    private String id;
    private String symbol;
    private String decimals;
  }
}
