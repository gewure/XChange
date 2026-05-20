package org.knowm.xchange.uniswap;

import org.knowm.xchange.BaseExchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.uniswap.service.UniswapAccountService;
import org.knowm.xchange.uniswap.service.UniswapMarketDataService;
import org.knowm.xchange.uniswap.service.UniswapTradeService;

import java.io.IOException;

/**
 * Exchange adapter for Uniswap V3.
 * <p>
 * Supports reading market data via The Graph and executing trades via Web3j.
 * Defaults to Ethereum Mainnet but can be configured for other EVM chains.
 * </p>
 */
public class UniswapExchange extends BaseExchange {

  @Override
  protected void initServices() {
    if (this.exchangeMetaData == null) {
      this.exchangeMetaData = new org.knowm.xchange.dto.meta.ExchangeMetaData(
          new java.util.HashMap<>(), new java.util.HashMap<>(), null, null, false);
    }
    this.marketDataService = new UniswapMarketDataService(this);
    this.accountService = new UniswapAccountService(this);
    this.tradeService = new UniswapTradeService(this);
  }

  @Override
  public ExchangeSpecification getDefaultExchangeSpecification() {
    UniswapExchangeSpecification spec = new UniswapExchangeSpecification();
    spec.setExchangeName("Uniswap");
    spec.setExchangeDescription("Uniswap V3 DEX on EVM Chains");
    // Default to Ethereum Mainnet
    spec.setNetwork(org.knowm.xchange.uniswap.config.UniswapNetwork.ETHEREUM);
    return spec;
  }

  @Override
  public void remoteInit() throws IOException, org.knowm.xchange.exceptions.ExchangeException {
    // Load metadata from subgraph
    // We need to cast marketDataService to UniswapMarketDataService or access client directly?
    // Better to expose a method in UniswapMarketDataService to get metadata or load it.
    // But remoteInit is called after initServices.
    
    // Let's delegate to UniswapMarketDataService to load metadata
    ((UniswapMarketDataService) marketDataService).loadMetadata();
  }
}
