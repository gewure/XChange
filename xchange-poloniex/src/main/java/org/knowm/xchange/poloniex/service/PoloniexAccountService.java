package org.knowm.xchange.poloniex.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.poloniex.PoloniexAdapters;
import org.knowm.xchange.poloniex.PoloniexErrorAdapter;
import org.knowm.xchange.poloniex.dto.PoloniexException;
import org.knowm.xchange.poloniex.dto.trade.PoloniexDepositsWithdrawalsResponse;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.trade.params.DefaultTradeHistoryParamsTimeSpan;
import org.knowm.xchange.service.trade.params.DefaultWithdrawFundsParams;
import org.knowm.xchange.service.trade.params.RippleWithdrawFundsParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsTimeSpan;
import org.knowm.xchange.service.trade.params.WithdrawFundsParams;

/**
 * @author Zach Holmes
 */
public class PoloniexAccountService extends PoloniexAccountServiceRaw implements AccountService {

  private static final String TRADING_WALLET_ID = "trading";
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /**
   * Constructor
   *
   * @param exchange
   */
  public PoloniexAccountService(Exchange exchange) {

    super(exchange);
  }

  private AccountInfo getMockAccountInfo() {
    List<org.knowm.xchange.dto.account.Balance> balances = new java.util.ArrayList<>();
    balances.add(new org.knowm.xchange.dto.account.Balance(Currency.BTC, new BigDecimal("1.5"), new BigDecimal("1.5"), BigDecimal.ZERO));
    balances.add(new org.knowm.xchange.dto.account.Balance(Currency.USDT, new BigDecimal("50000"), new BigDecimal("50000"), BigDecimal.ZERO));
    balances.add(new org.knowm.xchange.dto.account.Balance(Currency.ETH, new BigDecimal("10"), new BigDecimal("10"), BigDecimal.ZERO));
    balances.add(new org.knowm.xchange.dto.account.Balance(Currency.USDC, new BigDecimal("50000"), new BigDecimal("50000"), BigDecimal.ZERO));

    Wallet wallet = Wallet.Builder.from(balances)
        .id(TRADING_WALLET_ID)
        .features(new HashSet<>(Collections.singletonList(Wallet.WalletFeature.TRADING)))
        .build();
    return new AccountInfo(wallet);
  }

  @Override
  public AccountInfo getAccountInfo() throws IOException {
    // Detect dryRun status
    boolean dryRun = true;
    try {
      java.io.InputStream input = PoloniexAccountService.class.getClassLoader().getResourceAsStream("application.properties");
      if (input != null) {
        java.util.Properties prop = new java.util.Properties();
        prop.load(input);
        String dryRunProp = prop.getProperty("trader.dryRun");
        if (dryRunProp != null) {
          dryRun = Boolean.parseBoolean(dryRunProp);
        }
      }
    } catch (Exception ex) {
      // Ignore
    }
    String envDryRun = System.getenv("TRADER_DRY_RUN");
    if (envDryRun != null) {
      dryRun = Boolean.parseBoolean(envDryRun);
    }
    String sysDryRun = System.getProperty("trader.dryRun");
    if (sysDryRun != null) {
      dryRun = Boolean.parseBoolean(sysDryRun);
    }

    String key = exchange.getExchangeSpecification().getApiKey();
    String secret = exchange.getExchangeSpecification().getSecretKey();

    if (key == null || secret == null || key.isEmpty() || secret.isEmpty() || "api-key".equals(key)) {
      if (dryRun) {
        org.slf4j.LoggerFactory.getLogger(PoloniexAccountService.class)
            .warn("No valid credentials. Falling back to mock balances in Dry-Run.");
        return getMockAccountInfo();
      }
      throw new IOException("Invalid or missing Poloniex API credentials");
    }

    try {
      long timestamp = System.currentTimeMillis();
      String signatureString = "GET\n/accounts/balances\nsignTimestamp=" + timestamp;
      String signature = generateSignature(signatureString, secret);

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create("https://api.poloniex.com/accounts/balances"))
          .GET()
          .header("key", key)
          .header("signTimestamp", String.valueOf(timestamp))
          .header("signature", signature)
          .header("signatureMethod", "HmacSHA256")
          .header("signatureVersion", "2")
          .build();

      HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new IOException("Failed to fetch Poloniex balances, status code: " + response.statusCode() + ", body: " + response.body());
      }

      JsonNode root = OBJECT_MAPPER.readTree(response.body());
      List<org.knowm.xchange.dto.account.Balance> balances = new java.util.ArrayList<>();
      if (root.isArray() && root.size() > 0) {
        JsonNode account = root.get(0);
        JsonNode balancesNode = account.get("balances");
        if (balancesNode != null && balancesNode.isArray()) {
          for (JsonNode bal : balancesNode) {
            String currency = bal.get("currency").asText().toUpperCase();
            BigDecimal available = new BigDecimal(bal.get("available").asText());
            BigDecimal hold = new BigDecimal(bal.get("hold").asText());
            balances.add(new org.knowm.xchange.dto.account.Balance(
                Currency.getInstance(currency),
                available.add(hold),
                available,
                hold
            ));
          }
        }
      }

      if (balances.isEmpty()) {
        if (dryRun) {
          org.slf4j.LoggerFactory.getLogger(PoloniexAccountService.class)
              .warn("Poloniex account returned no assets. Falling back to mock balances in Dry-Run.");
          return getMockAccountInfo();
        }
      }

      Wallet build = Wallet.Builder.from(balances)
          .id(TRADING_WALLET_ID)
          .features(new HashSet<>(Collections.singletonList(Wallet.WalletFeature.TRADING)))
          .build();
      return new AccountInfo(build);
    } catch (Exception e) {
      if (dryRun) {
        org.slf4j.LoggerFactory.getLogger(PoloniexAccountService.class)
            .warn("Poloniex API call failed: {}. Falling back to mock balances in Dry-Run.", e.getMessage());
        return getMockAccountInfo();
      }
      if (e instanceof IOException) {
        throw (IOException) e;
      }
      throw new IOException("Error querying Poloniex balance", e);
    }
  }

  private String generateSignature(String data, String secret) {
    try {
      Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
      SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
      sha256_HMAC.init(secret_key);
      byte[] hash = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hash);
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate signature", e);
    }
  }

  @Override
  public String withdrawFunds(Currency currency, BigDecimal amount, String address)
      throws IOException {
    // does not support XRP withdrawals, use RippleWithdrawFundsParams instead
    return withdrawFunds(new DefaultWithdrawFundsParams(address, currency, amount));
  }

  @Override
  public String withdrawFunds(WithdrawFundsParams params) throws IOException {
    try {
      if (params instanceof RippleWithdrawFundsParams) {
        RippleWithdrawFundsParams xrpParams = (RippleWithdrawFundsParams) params;

        return withdraw(
            xrpParams.getCurrency(),
            xrpParams.getAmount(),
            xrpParams.getAddress(),
            xrpParams.getTag());
      }

      if (params instanceof DefaultWithdrawFundsParams) {
        DefaultWithdrawFundsParams defaultParams = (DefaultWithdrawFundsParams) params;

        return withdraw(
            defaultParams.getCurrency(),
            defaultParams.getAmount(),
            defaultParams.getAddress(),
            null);
      }

      throw new IllegalStateException("Don't know how to withdraw: " + params);
    } catch (PoloniexException e) {
      throw PoloniexErrorAdapter.adapt(e);
    }
  }

  @Override
  public String requestDepositAddress(Currency currency, String... args) throws IOException {
    try {
      return getDepositAddress(currency.toString());
    } catch (PoloniexException e) {
      throw PoloniexErrorAdapter.adapt(e);
    }
  }

  @Override
  public TradeHistoryParams createFundingHistoryParams() {
    final DefaultTradeHistoryParamsTimeSpan params = new DefaultTradeHistoryParamsTimeSpan();
    params.setStartTime(
        new Date(System.currentTimeMillis() - 366L * 24 * 60 * 60 * 1000)); // just over one year
    params.setEndTime(new Date());
    return params;
  }

  @Override
  public List<FundingRecord> getFundingHistory(TradeHistoryParams params) throws IOException {
    try {
      Date start = null;
      Date end = null;
      if (params instanceof TradeHistoryParamsTimeSpan) {
        start = ((TradeHistoryParamsTimeSpan) params).getStartTime();
        end = ((TradeHistoryParamsTimeSpan) params).getEndTime();
      }
      final PoloniexDepositsWithdrawalsResponse poloFundings =
          returnDepositsWithdrawals(start, end);
      return PoloniexAdapters.adaptFundingRecords(poloFundings);
    } catch (PoloniexException e) {
      throw PoloniexErrorAdapter.adapt(e);
    }
  }
}
