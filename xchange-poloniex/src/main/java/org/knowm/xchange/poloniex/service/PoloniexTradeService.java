package org.knowm.xchange.poloniex.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.poloniex.dto.PoloniexException;
import org.knowm.xchange.poloniex.PoloniexErrorAdapter;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.CancelOrderByIdParams;
import org.knowm.xchange.service.trade.params.CancelOrderParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamCurrencyPair;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsAll;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsTimeSpan;
import org.knowm.xchange.service.trade.params.orders.DefaultOpenOrdersParamCurrencyPair;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParamCurrencyPair;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PoloniexTradeService extends PoloniexTradeServiceRaw implements TradeService {

  private static final Logger LOG = LoggerFactory.getLogger(PoloniexTradeService.class);
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private PoloniexMarketDataService poloniexMarketDataService;

  public PoloniexTradeService(
      Exchange exchange, PoloniexMarketDataService poloniexMarketDataService) {

    super(exchange);
    this.poloniexMarketDataService = poloniexMarketDataService;
  }

  @Override
  public OpenOrders getOpenOrders() throws IOException {
    return getOpenOrders(createOpenOrdersParams());
  }

  @Override
  public OpenOrders getOpenOrders(OpenOrdersParams params) throws ExchangeException, IOException {
    String key = exchange.getExchangeSpecification().getApiKey();
    String secret = exchange.getExchangeSpecification().getSecretKey();

    if (key == null || secret == null || key.isEmpty() || secret.isEmpty() || "api-key".equals(key)) {
      throw new IOException("Invalid or missing Poloniex API credentials");
    }

    try {
      CurrencyPair currencyPair = null;
      if (params instanceof OpenOrdersParamCurrencyPair) {
        currencyPair = ((OpenOrdersParamCurrencyPair) params).getCurrencyPair();
      }

      String path = "/orders";
      String query = "";
      if (currencyPair != null) {
        String sym = currencyPair.getBase().getCurrencyCode().toUpperCase() + "_" + currencyPair.getCounter().getCurrencyCode().toUpperCase();
        query = "symbol=" + sym;
      }

      long timestamp = System.currentTimeMillis();
      String paramString = (query.isEmpty() ? "" : query + "&") + "signTimestamp=" + timestamp;
      String signatureString = "GET\n" + path + "\n" + paramString;
      String signature = generateSignature(signatureString, secret);

      String url = "https://api.poloniex.com" + path + "?" + paramString;

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .GET()
          .header("key", key)
          .header("signTimestamp", String.valueOf(timestamp))
          .header("signature", signature)
          .header("signatureMethod", "HmacSHA256")
          .header("signatureVersion", "2")
          .build();

      HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new IOException("Failed to fetch Poloniex open orders, status code: " + response.statusCode() + ", body: " + response.body());
      }

      JsonNode root = OBJECT_MAPPER.readTree(response.body());
      List<LimitOrder> openOrdersList = new ArrayList<>();
      if (root.isArray()) {
        for (JsonNode orderNode : root) {
          String id = orderNode.get("id").asText();
          String symbol = orderNode.get("symbol").asText();
          String[] parts = symbol.split("_");
          CurrencyPair pair = new CurrencyPair(parts[0], parts[1]);
          OrderType type = "BUY".equals(orderNode.get("side").asText()) ? OrderType.BID : OrderType.ASK;
          BigDecimal price = new BigDecimal(orderNode.get("price").asText());
          BigDecimal originalAmount = new BigDecimal(orderNode.get("quantity").asText());
          Date date = new Date(orderNode.get("createTime").asLong());
          openOrdersList.add(new LimitOrder(type, originalAmount, pair, id, date, price));
        }
      }
      return new OpenOrders(openOrdersList);
    } catch (Exception e) {
      if (e instanceof IOException) {
        throw (IOException) e;
      }
      throw new IOException("Failed to get open orders from Poloniex", e);
    }
  }

  @Override
  public String placeMarketOrder(MarketOrder marketOrder) throws IOException {
    throw new NotAvailableFromExchangeException();
  }

  @Override
  public String placeLimitOrder(LimitOrder limitOrder) throws IOException {
    String key = exchange.getExchangeSpecification().getApiKey();
    String secret = exchange.getExchangeSpecification().getSecretKey();

    if (key == null || secret == null || key.isEmpty() || secret.isEmpty() || "api-key".equals(key)) {
      throw new IOException("Invalid or missing Poloniex API credentials");
    }

    try {
      String symbol = limitOrder.getInstrument().getBase().getCurrencyCode().toUpperCase() + "_" +
                     limitOrder.getInstrument().getCounter().getCurrencyCode().toUpperCase();
      String side = (limitOrder.getType() == OrderType.BID || limitOrder.getType() == OrderType.EXIT_ASK) ? "BUY" : "SELL";
      String quantity = limitOrder.getOriginalAmount().toPlainString();
      String price = limitOrder.getLimitPrice().toPlainString();

      String jsonBody = String.format(
          "{\"symbol\":\"%s\",\"side\":\"%s\",\"type\":\"LIMIT\",\"quantity\":\"%s\",\"price\":\"%s\"}",
          symbol, side, quantity, price
      );

      long timestamp = System.currentTimeMillis();
      String encodedBody = java.net.URLEncoder.encode(jsonBody, StandardCharsets.UTF_8.name());
      String paramString = "requestBody=" + encodedBody + "&signTimestamp=" + timestamp;
      String signatureString = "POST\n/orders\n" + paramString;
      String signature = generateSignature(signatureString, secret);

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create("https://api.poloniex.com/orders"))
          .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
          .header("Content-Type", "application/json")
          .header("key", key)
          .header("signTimestamp", String.valueOf(timestamp))
          .header("signature", signature)
          .header("signatureMethod", "HmacSHA256")
          .header("signatureVersion", "2")
          .build();

      HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new IOException("Failed to place Poloniex limit order, status code: " + response.statusCode() + ", body: " + response.body());
      }

      JsonNode root = OBJECT_MAPPER.readTree(response.body());
      return root.get("id").asText();
    } catch (Exception e) {
      if (e instanceof IOException) {
        throw (IOException) e;
      }
      throw new IOException("Failed to place limit order", e);
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
  public boolean cancelOrder(String orderId) throws IOException {
    String key = exchange.getExchangeSpecification().getApiKey();
    String secret = exchange.getExchangeSpecification().getSecretKey();

    if (key == null || secret == null || key.isEmpty() || secret.isEmpty() || "api-key".equals(key)) {
      throw new IOException("Invalid or missing Poloniex API credentials");
    }

    try {
      long timestamp = System.currentTimeMillis();
      String signatureString = "DELETE\n/orders/" + orderId + "\nsignTimestamp=" + timestamp;
      String signature = generateSignature(signatureString, secret);

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create("https://api.poloniex.com/orders/" + orderId + "?signTimestamp=" + timestamp))
          .DELETE()
          .header("key", key)
          .header("signTimestamp", String.valueOf(timestamp))
          .header("signature", signature)
          .header("signatureMethod", "HmacSHA256")
          .header("signatureVersion", "2")
          .build();

      HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new IOException("Failed to cancel Poloniex order, status code: " + response.statusCode() + ", body: " + response.body());
      }
      return true;
    } catch (Exception e) {
      if (e instanceof IOException) {
        throw (IOException) e;
      }
      throw new IOException("Failed to cancel order", e);
    }
  }

  @Override
  public boolean cancelOrder(CancelOrderParams orderParams) throws IOException {
    try {
      if (orderParams instanceof CancelOrderByIdParams) {
        return cancelOrder(((CancelOrderByIdParams) orderParams).getOrderId());
      } else {
        return false;
      }
    } catch (PoloniexException e) {
      throw PoloniexErrorAdapter.adapt(e);
    }
  }

  @Override
  public UserTrades getTradeHistory(TradeHistoryParams params) throws IOException {
    throw new NotAvailableFromExchangeException();
  }

  @Override
  public TradeHistoryParams createTradeHistoryParams() {
    return new PoloniexTradeHistoryParams();
  }

  @Override
  public OpenOrdersParams createOpenOrdersParams() {
    return new DefaultOpenOrdersParamCurrencyPair();
  }

  @Override
  public Collection<Order> getOrder(OrderQueryParams... orderQueryParams) throws IOException {
    throw new NotAvailableFromExchangeException();
  }

  public static class PoloniexTradeHistoryParams
      implements TradeHistoryParamCurrencyPair, TradeHistoryParamsTimeSpan {

    private final TradeHistoryParamsAll all = new TradeHistoryParamsAll();

    @Override
    public CurrencyPair getCurrencyPair() {

      return all.getCurrencyPair();
    }

    @Override
    public void setCurrencyPair(CurrencyPair value) {

      all.setCurrencyPair(value);
    }

    @Override
    public Date getStartTime() {

      return all.getStartTime();
    }

    @Override
    public void setStartTime(Date value) {

      all.setStartTime(value);
    }

    @Override
    public Date getEndTime() {

      return all.getEndTime();
    }

    @Override
    public void setEndTime(Date value) {

      all.setEndTime(value);
    }
  }
}
