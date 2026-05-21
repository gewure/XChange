package org.knowm.xchange.bybit.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bybit.BybitExchange;
import org.knowm.xchange.bybit.dto.account.walletbalance.BybitAccountType;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.service.account.AccountService;

public class BybitPrivateApiIntegration {

  private String apiKey;
  private String privateKeyPem;
  private Exchange exchange;

  @Before
  public void setup() throws IOException {
    apiKey = System.getProperty("bybit.api.key", System.getenv("BYBIT_API_KEY"));
    if (apiKey == null || apiKey.isEmpty()) {
      apiKey = "juEQPs7HtshbrE9g43"; // Default provided key
    }

    privateKeyPem = System.getProperty("bybit.private.key", System.getenv("BYBIT_PRIVATE_KEY"));
    if (privateKeyPem == null || privateKeyPem.isEmpty()) {
      // Try to load from various potential paths
      Path[] paths = {
          Paths.get("/home/jasp/Faast/bybitkey_private.pem"),
          Paths.get("bybitkey_private.pem"),
          Paths.get("../bybitkey_private.pem"),
          Paths.get("../../bybitkey_private.pem"),
          Paths.get("../../../bybitkey_private.pem")
      };

      for (Path path : paths) {
        if (Files.exists(path)) {
          privateKeyPem = Files.readString(path);
          break;
        }
      }
    }

    Assume.assumeTrue("Ignore test because API Key or Private Key PEM is missing",
        apiKey != null && !apiKey.isEmpty() && privateKeyPem != null && !privateKeyPem.isEmpty());

    ExchangeSpecification spec = new BybitExchange().getDefaultExchangeSpecification();
    spec.setApiKey(apiKey);
    spec.setSecretKey(privateKeyPem);
    spec.setExchangeSpecificParametersItem(BybitExchange.SPECIFIC_PARAM_ACCOUNT_TYPE, BybitAccountType.UNIFIED);

    exchange = ExchangeFactory.INSTANCE.createExchange(spec);
  }

  @Test
  public void getAccountInfoTest() throws IOException {
    AccountService accountService = exchange.getAccountService();
    AccountInfo accountInfo = accountService.getAccountInfo();
    System.out.println("Bybit Account Info: " + accountInfo);
    assertThat(accountInfo).isNotNull();

    java.util.Map<String, Wallet> wallets = accountInfo.getWallets();
    assertThat(wallets).isNotEmpty();
    
    // Print balances of non-zero currencies for visibility
    for (Wallet wallet : wallets.values()) {
      wallet.getBalances().forEach((currency, balance) -> {
        if (balance.getTotal().compareTo(java.math.BigDecimal.ZERO) > 0) {
          System.out.println("Wallet Balance: " + currency + " = " + balance.getTotal());
        }
      });
    }
  }
}
