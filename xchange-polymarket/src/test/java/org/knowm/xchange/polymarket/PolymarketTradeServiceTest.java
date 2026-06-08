package org.knowm.xchange.polymarket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.LimitOrder;

import java.io.IOException;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class PolymarketTradeServiceTest {

  private Exchange exchange;
  private Polymarket polymarket;
  private PolymarketTradeService tradeService;

  @BeforeEach
  void setUp() {
    exchange = mock(Exchange.class);
    polymarket = mock(Polymarket.class);
    tradeService = new PolymarketTradeService(exchange, polymarket);
  }

  @Test
  void testPlaceLimitOrder_delegatesToPolymarketApi() throws Exception {
    // Arrange
    CurrencyPair pair = new CurrencyPair("BTC", "USDC");
    LimitOrder order = new LimitOrder.Builder(OrderType.BID, pair)
        .originalAmount(BigDecimal.valueOf(100))
        .limitPrice(BigDecimal.valueOf(0.52))
        .build();

    when(polymarket.postOrder(any())).thenReturn("order-id-998877");

    // Act
    String orderId = tradeService.placeLimitOrder(order);

    // Assert
    assertThat(orderId).isEqualTo("order-id-998877");
    verify(polymarket).postOrder(order);
  }
}
