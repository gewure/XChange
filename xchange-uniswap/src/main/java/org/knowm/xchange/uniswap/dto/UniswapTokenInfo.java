package org.knowm.xchange.uniswap.dto;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UniswapTokenInfo {

  @EqualsAndHashCode.Include
  private final String address;
  private final String symbol;
  private final int decimals;
  private final String name;

  public UniswapTokenInfo(String address, String symbol, int decimals, String name) {
    this.address = address.toLowerCase();
    this.symbol = symbol;
    this.decimals = decimals;
    this.name = name;
  }
}
