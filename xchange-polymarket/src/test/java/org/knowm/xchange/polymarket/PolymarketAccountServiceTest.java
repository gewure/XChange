package org.knowm.xchange.polymarket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.currency.Currency;
import okhttp3.*;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class PolymarketAccountServiceTest {

  private Exchange exchange;
  private PolymarketAccountService accountService;
  private OkHttpClient mockClient;
  private Call mockCall;
  private Response mockResponse;
  private ResponseBody mockResponseBody;
  private Request dummyRequest;

  @BeforeEach
  void setUp() throws Exception {
    exchange = mock(Exchange.class);
    mockClient = mock(OkHttpClient.class);
    mockCall = mock(Call.class);

    ExchangeSpecification spec = new ExchangeSpecification(PolymarketExchange.class);
    spec.setApiKey("test-api-key");
    spec.setSecretKey(Base64.getEncoder().encodeToString("test-secret".getBytes()));
    spec.setExchangeSpecificParametersItem("passphrase", "test-passphrase");
    spec.setUserName("0x1234567890123456789012345678901234567890");
    spec.setSslUri("https://clob.polymarket.com");

    when(exchange.getExchangeSpecification()).thenReturn(spec);

    accountService = new PolymarketAccountService(exchange);

    Field clientField = PolymarketAccountService.class.getDeclaredField("client");
    clientField.setAccessible(true);
    clientField.set(accountService, mockClient);

    dummyRequest = new Request.Builder().url("https://clob.polymarket.com").build();
  }

  @Test
  void testBuildPolyHmacSignature() {
    String secret = Base64.getEncoder().encodeToString("secret-key".getBytes());
    String sig = PolymarketAccountService.buildPolyHmacSignature(secret, 123456789L, "GET", "/test-path", "test-body");
    assertThat(sig).isNotEmpty();
  }

  @Test
  void testGetAccountInfo_missingCredentials_returnsFallback() throws Exception {
    // Arrange
    ExchangeSpecification spec = new ExchangeSpecification(PolymarketExchange.class); // empty credentials
    when(exchange.getExchangeSpecification()).thenReturn(spec);

    // Act
    AccountInfo info = accountService.getAccountInfo();

    // Assert
    assertThat(info.getUsername()).isEqualTo("simulated_polymarket");
    assertThat(info.getWallet().getBalance(Currency.USDC).getAvailable()).isEqualByComparingTo("10000.00");
  }

  @Test
  void testGetAccountInfo_success() throws Exception {
    // Arrange
    mockResponseBody = ResponseBody.create(
        "{\"balance\":\"1234.56\"}",
        MediaType.get("application/json")
    );
    mockResponse = new Response.Builder()
        .request(dummyRequest)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(mockResponseBody)
        .build();

    when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(mockResponse);

    // Act
    AccountInfo info = accountService.getAccountInfo();

    // Assert
    assertThat(info.getUsername()).isEqualTo("0x1234567890123456789012345678901234567890");
    assertThat(info.getWallet().getBalance(Currency.USDC).getAvailable()).isEqualByComparingTo("1234.56");
  }

  @Test
  void testGetAccountInfo_httpError_returnsZeroBalance() throws Exception {
    // Arrange
    mockResponseBody = ResponseBody.create(
        "Error message from server",
        MediaType.get("text/plain")
    );
    mockResponse = new Response.Builder()
        .request(dummyRequest)
        .protocol(Protocol.HTTP_1_1)
        .code(500)
        .message("Internal Server Error")
        .body(mockResponseBody)
        .build();

    when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(mockResponse);

    // Act
    AccountInfo info = accountService.getAccountInfo();

    // Assert
    assertThat(info.getWallet().getBalance(Currency.USDC).getAvailable()).isEqualByComparingTo("0.00");
  }

  @Test
  void testGetAccountInfo_exception_returnsZeroBalance() throws Exception {
    // Arrange
    when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);
    when(mockCall.execute()).thenThrow(new IOException("Connection timed out"));

    // Act
    AccountInfo info = accountService.getAccountInfo();

    // Assert
    assertThat(info.getWallet().getBalance(Currency.USDC).getAvailable()).isEqualByComparingTo("0.00");
  }
}
