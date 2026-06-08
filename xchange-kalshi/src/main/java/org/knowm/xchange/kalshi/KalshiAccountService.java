package org.knowm.xchange.kalshi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.service.account.AccountService;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class KalshiAccountService implements AccountService {

  private final KalshiExchange exchange;
  private final OkHttpClient client = new OkHttpClient();
  private final ObjectMapper mapper = new ObjectMapper();

  public KalshiAccountService(KalshiExchange exchange) {
    this.exchange = exchange;
  }

  @Override
  public AccountInfo getAccountInfo() throws IOException {
    String apiKey = exchange.getExchangeSpecification().getApiKey();
    KalshiDigest digest = exchange.getSignatureCreator();

    if (apiKey == null || digest == null) {
      // Fallback/Simulated balance to prevent startup crashes when running in dry-run
      List<Balance> balances = new ArrayList<>();
      balances.add(new Balance(Currency.USD, new BigDecimal("5000.00"), new BigDecimal("5000.00")));
      return new AccountInfo("simulated_kalshi", Wallet.Builder.from(balances).id("simulated").build());
    }

    String timestamp = KalshiDigest.getCalibratedTimestamp(digest);
    String method = "GET";
    String path = "/trade-api/v2/portfolio/balance";
    String signature = digest.sign(timestamp, method, path);

    Request request = new Request.Builder()
        .url(exchange.getExchangeSpecification().getSslUri() + "/portfolio/balance")
        .header("KALSHI-ACCESS-KEY", apiKey)
        .header("KALSHI-ACCESS-TIMESTAMP", timestamp)
        .header("KALSHI-ACCESS-SIGNATURE", signature)
        .build();

    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        BigDecimal dummyVal = new BigDecimal("0.00");
        List<Balance> balances = new ArrayList<>();
        balances.add(new Balance(Currency.USD, dummyVal, dummyVal));
        return new AccountInfo(apiKey, Wallet.Builder.from(balances).id(apiKey).build());
      }

      JsonNode node = mapper.readTree(response.body().string());
      BigDecimal usdBalance;
      if (node.has("balance_dollars")) {
        usdBalance = new BigDecimal(node.get("balance_dollars").asText());
      } else if (node.has("balance")) {
        usdBalance = new BigDecimal(node.get("balance").asLong()).divide(new BigDecimal("100"));
      } else {
        usdBalance = BigDecimal.ZERO;
      }

      List<Balance> balances = new ArrayList<>();
      balances.add(new Balance(Currency.USD, usdBalance, usdBalance));

      return new AccountInfo(apiKey, Wallet.Builder.from(balances).id(apiKey).build());
    } catch (Exception e) {
      BigDecimal dummyVal = new BigDecimal("0.00");
      List<Balance> balances = new ArrayList<>();
      balances.add(new Balance(Currency.USD, dummyVal, dummyVal));
      return new AccountInfo(apiKey, Wallet.Builder.from(balances).id(apiKey).build());
    }
  }
}
