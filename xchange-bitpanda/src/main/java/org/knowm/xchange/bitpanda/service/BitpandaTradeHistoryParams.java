package org.knowm.xchange.bitpanda.service;

import org.knowm.xchange.service.trade.params.TradeHistoryParamLimit;
import org.knowm.xchange.service.trade.params.TradeHistoryParamNextPageCursor;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;

public class BitpandaTradeHistoryParams implements TradeHistoryParams, TradeHistoryParamNextPageCursor, TradeHistoryParamLimit {

  private String nextPageCursor;
  private Integer limit;
  private String type;

  @Override
  public String getNextPageCursor() {
    return nextPageCursor;
  }

  @Override
  public void setNextPageCursor(String cursor) {
    this.nextPageCursor = cursor;
  }

  @Override
  public Integer getLimit() {
    return limit;
  }

  @Override
  public void setLimit(Integer limit) {
    this.limit = limit;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }
}
