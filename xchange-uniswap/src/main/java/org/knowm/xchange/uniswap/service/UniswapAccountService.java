package org.knowm.xchange.uniswap.service;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.uniswap.UniswapExchange;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

public class UniswapAccountService implements AccountService {

  private final UniswapExchange exchange;
  private final UniswapOnChainClient onChainClient;

  public UniswapAccountService(UniswapExchange exchange) {
    this.exchange = exchange;
    this.onChainClient = new UniswapOnChainClient(exchange);
  }

  @Override
  public AccountInfo getAccountInfo() throws IOException {
    String walletAddress = exchange.getExchangeSpecification().getApiKey(); // Use ApiKey field for wallet address? Or custom?
    
    if (walletAddress == null || walletAddress.isEmpty()) {
       walletAddress = (String) exchange.getExchangeSpecification().getExchangeSpecificParametersItem("wallet_address");
    }
    
    if (walletAddress == null || walletAddress.isEmpty()) {
        String privateKey = exchange.getExchangeSpecification().getSecretKey();
        if (privateKey == null || privateKey.isEmpty()) {
            privateKey = (String) exchange.getExchangeSpecification().getExchangeSpecificParametersItem("private_key");
        }
        if (privateKey != null && !privateKey.isEmpty()) {
            try {
                walletAddress = org.web3j.crypto.Credentials.create(privateKey).getAddress();
            } catch (Exception ignored) {}
        }
    }
     
    if (walletAddress == null || walletAddress.isEmpty()) {
       throw new IllegalArgumentException("Wallet address not provided");
    }

    BigInteger balanceWei = onChainClient.getBalance(walletAddress);
    BigDecimal balanceEth = new BigDecimal(balanceWei).divide(new BigDecimal("1000000000000000000"), java.math.MathContext.DECIMAL128);
    
    java.util.List<Balance> balances = new java.util.ArrayList<>();
    balances.add(new Balance.Builder()
        .currency(Currency.ETH)
        .total(balanceEth)
        .available(balanceEth)
        .build());
        
    // Fetch tracked tokens
    String trackedTokensStr = (String) exchange.getExchangeSpecification().getExchangeSpecificParametersItem(org.knowm.xchange.uniswap.UniswapExchangeSpecification.TRACKED_TOKENS);
    if (trackedTokensStr != null && !trackedTokensStr.isEmpty()) {
        String[] tokenAddresses = trackedTokensStr.split(",");
        for (String tokenAddress : tokenAddresses) {
            try {
                tokenAddress = tokenAddress.trim();
                BigInteger tokenBalance = onChainClient.getERC20Balance(tokenAddress, walletAddress);
                
                // We need decimals to convert to BigDecimal. For now, let's fetch decimals or assume 18 if failing?
                // Ideally we should cache decimals.
                // Let's try to fetch symbol and decimals.
                String symbol = onChainClient.getSymbol(tokenAddress);
                int decimals = onChainClient.getDecimals(tokenAddress);
                
                BigDecimal balanceToken = new BigDecimal(tokenBalance).divide(BigDecimal.TEN.pow(decimals), java.math.MathContext.DECIMAL128);
                
                balances.add(new Balance.Builder()
                    .currency(new Currency(symbol))
                    .total(balanceToken)
                    .available(balanceToken)
                    .build());
            } catch (Exception e) {
                // Log error but continue
                // logger.warn("Failed to fetch balance for token " + tokenAddress, e);
            }
        }
    }
        
    return new AccountInfo(walletAddress, Wallet.Builder.from(balances).id(walletAddress).build());
  }
}
