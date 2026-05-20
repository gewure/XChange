package org.knowm.xchange.uniswap;

import org.knowm.xchange.ExchangeSpecification;

public class UniswapExchangeSpecification extends ExchangeSpecification {

  public static final String RPC_URI = "RpcUri";
  public static final String SUBGRAPH_URI = "SubgraphUri";
  public static final String CHAIN_ID = "ChainId";
  public static final String ROUTER_ADDRESS = "RouterAddress";
  public static final String TRACKED_TOKENS = "TrackedTokens"; // Comma-separated addresses
  public static final String WRAPPED_NATIVE_TOKEN = "WrappedNativeToken";
  public static final String SLIPPAGE_TOLERANCE = "SlippageTolerance"; // Double, e.g. 0.005 for 0.5%
  public static final String FLASHBOTS_RELAY_URI = "FlashbotsRelayUri"; // e.g. https://relay.flashbots.net
  public static final String FLASHBOTS_RELAY_SIGNING_KEY = "FlashbotsRelaySigningKey"; // Private key for signing relay requests

  public UniswapExchangeSpecification() {
    super(UniswapExchange.class);
  }

  public UniswapExchangeSpecification(Class<? extends org.knowm.xchange.Exchange> exchangeClass) {
    super(exchangeClass);
  }
  
  public void setTrackedTokens(java.util.List<String> tokenAddresses) {
      setExchangeSpecificParametersItem(TRACKED_TOKENS, String.join(",", tokenAddresses));
  }

  public void setNetwork(org.knowm.xchange.uniswap.config.UniswapNetwork network) {
      setRpcUri(network.getRpcUri());
      setSubgraphUri(network.getSubgraphUri());
      setExchangeSpecificParametersItem(CHAIN_ID, network.getChainId());
      setExchangeSpecificParametersItem(ROUTER_ADDRESS, network.getRouterAddress());
      setExchangeSpecificParametersItem(WRAPPED_NATIVE_TOKEN, network.getWrappedNativeToken());
  }

  public void setRpcUri(String rpcUri) {
    setExchangeSpecificParametersItem(RPC_URI, rpcUri);
  }

  public String getRpcUri() {
    return (String) getExchangeSpecificParametersItem(RPC_URI);
  }

  public void setSubgraphUri(String subgraphUri) {
    setExchangeSpecificParametersItem(SUBGRAPH_URI, subgraphUri);
  }

  public String getSubgraphUri() {
    return (String) getExchangeSpecificParametersItem(SUBGRAPH_URI);
  }

  public void setChainId(long chainId) {
    setExchangeSpecificParametersItem(CHAIN_ID, chainId);
  }

  public Long getChainId() {
    return (Long) getExchangeSpecificParametersItem(CHAIN_ID);
  }

  public void setWalletAddress(String walletAddress) {
    setExchangeSpecificParametersItem("wallet_address", walletAddress);
  }

  public String getWalletAddress() {
    return (String) getExchangeSpecificParametersItem("wallet_address");
  }
}
