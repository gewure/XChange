package org.knowm.xchange.uniswap.config;

public enum UniswapNetwork {
  ETHEREUM(
      1L,
      "https://ethereum-rpc.publicnode.com",
      "https://gateway.thegraph.com/api/1cd4d7cdffb4e9e368b72b943046f8de/subgraphs/id/2SNYtSof7BDC8aCfPy85JZ9Mrh8vYTVecYkeNNtcmQXN",
      "0xE592427A0AEce92De3Edee1F18E0157C05861564",
      "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2" // WETH
  ),
  POLYGON(
      137L,
      "https://polygon-rpc.com",
      "https://api.thegraph.com/subgraphs/name/ianlapham/uniswap-v3-polygon",
      "0xE592427A0AEce92De3Edee1F18E0157C05861564",
      "0x0d500B1d8E8eF31E21C99d1Db9A6444d3ADf1270" // WMATIC
  ),
  ARBITRUM(
      42161L,
      "https://arb1.arbitrum.io/rpc",
      "https://api.thegraph.com/subgraphs/name/ianlapham/arbitrum-minimal",
      "0xE592427A0AEce92De3Edee1F18E0157C05861564",
      "0x82aF49447D8a07e3bd95BD0d56f35241523fBab1" // WETH
  ),
  OPTIMISM(
      10L,
      "https://mainnet.optimism.io",
      "https://api.thegraph.com/subgraphs/name/ianlapham/optimism-post-regenesis",
      "0xE592427A0AEce92De3Edee1F18E0157C05861564",
      "0x4200000000000000000000000000000000000006" // WETH
  );

  private final long chainId;
  private final String rpcUri;
  private final String subgraphUri;
  private final String routerAddress;
  private final String wrappedNativeToken;

  UniswapNetwork(long chainId, String rpcUri, String subgraphUri, String routerAddress, String wrappedNativeToken) {
    this.chainId = chainId;
    this.rpcUri = rpcUri;
    this.subgraphUri = subgraphUri;
    this.routerAddress = routerAddress;
    this.wrappedNativeToken = wrappedNativeToken;
  }

  public long getChainId() {
    return chainId;
  }

  public String getRpcUri() {
    return rpcUri;
  }

  public String getSubgraphUri() {
    return subgraphUri;
  }

  public String getRouterAddress() {
    return routerAddress;
  }
  
  public String getWrappedNativeToken() {
      return wrappedNativeToken;
  }
}
