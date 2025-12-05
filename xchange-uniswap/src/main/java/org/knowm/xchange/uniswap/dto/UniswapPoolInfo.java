package org.knowm.xchange.uniswap.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UniswapPoolInfo {
  private final String poolAddress;
  private final UniswapTokenInfo token0;
  private final UniswapTokenInfo token1;
  private final int feeTier;
  private final int tickSpacing;
}
