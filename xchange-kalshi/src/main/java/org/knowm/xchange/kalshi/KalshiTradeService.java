package org.knowm.xchange.kalshi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.StopOrder;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.CancelOrderParams;
import org.knowm.xchange.service.trade.params.CancelOrderByIdParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.UUID;

public class KalshiTradeService implements TradeService {

  private final KalshiExchange exchange;
  private final OkHttpClient client = new OkHttpClient();
  private final ObjectMapper mapper = new ObjectMapper();

  public KalshiTradeService(KalshiExchange exchange) {
    this.exchange = exchange;
  }

  @Override
  public OpenOrders getOpenOrders() throws IOException {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public OpenOrders getOpenOrders(OpenOrdersParams params) throws IOException {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public String placeMarketOrder(MarketOrder marketOrder) throws IOException {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public String placeLimitOrder(LimitOrder limitOrder) throws IOException {
    String apiKey = exchange.getExchangeSpecification().getApiKey();
    KalshiDigest digest = exchange.getSignatureCreator();
    if (apiKey == null || digest == null) {
      throw new ExchangeException("Exchange credentials not configured");
    }

    String ticker = limitOrder.getInstrument().toString();
    if (ticker.contains("/")) {
      ticker = ticker.split("/")[0];
    }
    String action = limitOrder.getType() == OrderType.BID ? "buy" : "sell";

    BigDecimal limitPrice = limitOrder.getLimitPrice();
    int yesPriceCents = limitPrice.multiply(BigDecimal.valueOf(100)).intValue();

    BigDecimal amount = limitOrder.getOriginalAmount();
    int count = amount.intValue();

    String clientOrderId = "tr-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    String path = "/portfolio/orders";

    ObjectNode body = mapper.createObjectNode();
    body.put("ticker", ticker);
    body.put("action", action);
    body.put("type", "limit");
    body.put("yes_price", yesPriceCents);
    body.put("no_price", 100 - yesPriceCents);
    body.put("count", count);
    body.put("client_order_id", clientOrderId);

    String jsonBody = mapper.writeValueAsString(body);
    String timestamp = KalshiDigest.getCalibratedTimestamp(digest);
    String signature = digest.sign(timestamp, "POST", "/trade-api/v2" + path);

    Request request = new Request.Builder()
        .url(exchange.getExchangeSpecification().getSslUri() + path)
        .post(RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8")))
        .addHeader("KALSHI-ACCESS-KEY", apiKey)
        .addHeader("KALSHI-ACCESS-SIGNATURE", signature)
        .addHeader("KALSHI-ACCESS-TIMESTAMP", timestamp)
        .addHeader("Content-Type", "application/json")
        .build();

    try (Response response = client.newCall(request).execute()) {
      String responseBody = response.body() != null ? response.body().string() : "";
      if (response.isSuccessful()) {
        JsonNode resp = mapper.readTree(responseBody);
        return resp.path("order").path("order_id").asText(clientOrderId);
      } else {
        throw new ExchangeException("Kalshi order placement failed HTTP " + response.code() + ": " + responseBody);
      }
    }
  }

  @Override
  public String placeStopOrder(StopOrder stopOrder) throws IOException {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public boolean cancelOrder(String orderId) throws IOException {
    String apiKey = exchange.getExchangeSpecification().getApiKey();
    KalshiDigest digest = exchange.getSignatureCreator();
    if (apiKey == null || digest == null) {
      throw new ExchangeException("Exchange credentials not configured");
    }

    String path = "/portfolio/orders/" + orderId;
    String timestamp = KalshiDigest.getCalibratedTimestamp(digest);
    String signature = digest.sign(timestamp, "DELETE", "/trade-api/v2" + path);

    Request request = new Request.Builder()
        .url(exchange.getExchangeSpecification().getSslUri() + path)
        .delete()
        .addHeader("KALSHI-ACCESS-KEY", apiKey)
        .addHeader("KALSHI-ACCESS-SIGNATURE", signature)
        .addHeader("KALSHI-ACCESS-TIMESTAMP", timestamp)
        .build();

    try (Response response = client.newCall(request).execute()) {
      if (response.isSuccessful()) {
        return true;
      } else {
        String responseBody = response.body() != null ? response.body().string() : "";
        throw new ExchangeException("Kalshi order cancellation failed HTTP " + response.code() + ": " + responseBody);
      }
    }
  }

  @Override
  public boolean cancelOrder(CancelOrderParams orderParams) throws IOException {
    if (orderParams instanceof CancelOrderByIdParams) {
      return cancelOrder(((CancelOrderByIdParams) orderParams).getOrderId());
    }
    throw new UnsupportedOperationException("CancelOrderParams must implement CancelOrderByIdParams");
  }

  @Override
  public UserTrades getTradeHistory(TradeHistoryParams params) throws IOException {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public TradeHistoryParams createTradeHistoryParams() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public OpenOrdersParams createOpenOrdersParams() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public void verifyOrder(LimitOrder limitOrder) {
  }

  @Override
  public void verifyOrder(MarketOrder marketOrder) {
  }

  @Override
  public Collection<Order> getOrder(String... orderIds) throws IOException {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
