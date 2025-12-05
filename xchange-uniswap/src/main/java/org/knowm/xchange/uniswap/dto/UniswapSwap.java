package org.knowm.xchange.uniswap.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UniswapSwap {
  private String id;
  private BigDecimal amount0;
  private BigDecimal amount1;
  private BigDecimal amountUSD;
  private long timestamp;
  private Transaction transaction;

  @Data
  public static class Transaction {
    private String id;
  }
}
