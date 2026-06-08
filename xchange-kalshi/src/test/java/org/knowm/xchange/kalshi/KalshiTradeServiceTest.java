package org.knowm.xchange.kalshi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.exceptions.ExchangeException;
import okhttp3.*;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class KalshiTradeServiceTest {

  static {
    System.setProperty("kalshi.skip.clock.calibration", "true");
  }

  private KalshiExchange exchange;
  private KalshiDigest digest;
  private KalshiTradeService tradeService;
  private OkHttpClient mockClient;
  private Call mockCall;
  private Response mockResponse;
  private ResponseBody mockResponseBody;
  private Request dummyRequest;

  @BeforeEach
  void setUp() throws Exception {
    exchange = mock(KalshiExchange.class);
    digest = mock(KalshiDigest.class);
    
    ExchangeSpecification spec = new ExchangeSpecification(KalshiExchange.class);
    spec.setApiKey("test-api-key");
    spec.setSecretKey("test-secret-key");
    spec.setSslUri("https://external-api.kalshi.com/trade-api/v2");
    
    when(exchange.getExchangeSpecification()).thenReturn(spec);
    when(exchange.getSignatureCreator()).thenReturn(digest);
    when(digest.sign(any(), any(), any())).thenReturn("test-signature");
    
    tradeService = new KalshiTradeService(exchange);
    
    // Mock OkHttpClient
    mockClient = mock(OkHttpClient.class);
    mockCall = mock(Call.class);
    
    mockResponseBody = ResponseBody.create(
        "{}",
        MediaType.get("application/json")
    );
    
    dummyRequest = new Request.Builder().url("https://external-api.kalshi.com").build();
    mockResponse = new Response.Builder()
        .request(dummyRequest)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(mockResponseBody)
        .build();
    
    when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(mockResponse);
    
    Field clientField = KalshiTradeService.class.getDeclaredField("client");
    clientField.setAccessible(true);
    clientField.set(tradeService, mockClient);
  }

  @Test
  void testPlaceLimitOrder_stripsTickerSlashSuccessfully() throws Exception {
    // Arrange
    CurrencyPair pair = new CurrencyPair("BTC-26MAY26-T60000", "USD");
    LimitOrder order = new LimitOrder.Builder(OrderType.BID, pair)
        .originalAmount(BigDecimal.valueOf(10))
        .limitPrice(BigDecimal.valueOf(0.55))
        .build();
    
    mockResponseBody = ResponseBody.create(
        "{\"order\":{\"order_id\":\"ord-12345\"}}",
        MediaType.get("application/json")
    );
    mockResponse = new Response.Builder()
        .request(dummyRequest)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(mockResponseBody)
        .build();
    when(mockCall.execute()).thenReturn(mockResponse);
    
    ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
    
    // Act
    String orderId = tradeService.placeLimitOrder(order);
    
    // Assert
    assertThat(orderId).isEqualTo("ord-12345");
    verify(mockClient).newCall(requestCaptor.capture());
    
    Request sentRequest = requestCaptor.getValue();
    assertThat(sentRequest.url().toString()).isEqualTo("https://external-api.kalshi.com/trade-api/v2/portfolio/orders");
    assertThat(sentRequest.header("KALSHI-ACCESS-KEY")).isEqualTo("test-api-key");
    assertThat(sentRequest.header("KALSHI-ACCESS-SIGNATURE")).isEqualTo("test-signature");
    
    // Verify JSON body contains stripped ticker
    RequestBody body = sentRequest.body();
    okio.Buffer buffer = new okio.Buffer();
    body.writeTo(buffer);
    String bodyString = buffer.readUtf8();
    
    assertThat(bodyString).contains("\"ticker\":\"BTC-26MAY26-T60000\"");
    assertThat(bodyString).contains("\"action\":\"buy\"");
    assertThat(bodyString).contains("\"yes_price\":55");
    assertThat(bodyString).contains("\"no_price\":45");
    assertThat(bodyString).contains("\"count\":10");
  }

  @Test
  void testPlaceLimitOrder_missingCredentials_throwsException() {
    // Arrange
    when(exchange.getExchangeSpecification()).thenReturn(new ExchangeSpecification(KalshiExchange.class));
    CurrencyPair pair = new CurrencyPair("BTC-26MAY26-T60000", "USD");
    LimitOrder order = new LimitOrder.Builder(OrderType.BID, pair)
        .originalAmount(BigDecimal.valueOf(10))
        .limitPrice(BigDecimal.valueOf(0.55))
        .build();
    
    // Act & Assert
    assertThatThrownBy(() -> tradeService.placeLimitOrder(order))
        .isInstanceOf(ExchangeException.class)
        .hasMessageContaining("Exchange credentials not configured");
  }

  @Test
  void testCancelOrder_success() throws Exception {
    // Arrange
    ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
    
    // Act
    boolean cancelled = tradeService.cancelOrder("ord-12345");
    
    // Assert
    assertThat(cancelled).isTrue();
    verify(mockClient).newCall(requestCaptor.capture());
    
    Request sentRequest = requestCaptor.getValue();
    assertThat(sentRequest.url().toString()).isEqualTo("https://external-api.kalshi.com/trade-api/v2/portfolio/orders/ord-12345");
    assertThat(sentRequest.method()).isEqualTo("DELETE");
  }
}
