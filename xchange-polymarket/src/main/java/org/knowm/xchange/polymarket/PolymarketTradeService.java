package org.knowm.xchange.polymarket;

import java.io.IOException;
import java.util.Collection;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.StopOrder;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.CancelOrderParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;

public class PolymarketTradeService implements TradeService {

  private final Exchange exchange;
  private final Polymarket polymarket;

  public PolymarketTradeService(Exchange exchange, Polymarket polymarket) {
    this.exchange = exchange;
    this.polymarket = polymarket;
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
    // Polymarket requires properly formatted order objects and signing
    // Here we just map it out to call the postOrder interface which was explicitly requested
    // Full signing implementation is out of scope.
    return polymarket.postOrder(limitOrder).toString();
  }

  @Override
  public String placeStopOrder(StopOrder stopOrder) throws IOException {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public boolean cancelOrder(String orderId) throws IOException {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public boolean cancelOrder(CancelOrderParams orderParams) throws IOException {
    throw new UnsupportedOperationException("Not implemented yet");
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
    // throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public void verifyOrder(MarketOrder marketOrder) {
    // throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public Collection<Order> getOrder(String... orderIds) throws IOException {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
