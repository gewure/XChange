package org.knowm.xchange.bitpanda.service;

import java.io.IOException;
import java.util.List;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.bitpanda.BitpandaAdapters;
import org.knowm.xchange.bitpanda.BitpandaExchange;
import org.knowm.xchange.bitpanda.dto.BitpandaResponse;
import org.knowm.xchange.bitpanda.dto.trade.BitpandaTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.TradeHistoryParamLimit;
import org.knowm.xchange.service.trade.params.TradeHistoryParamNextPageCursor;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;

public class BitpandaTradeService extends BitpandaBaseService implements TradeService {

  public BitpandaTradeService(Exchange exchange) {
    super(exchange);
  }

  @Override
  public TradeHistoryParams createTradeHistoryParams() {
    return new BitpandaTradeHistoryParams();
  }

  @Override
  public UserTrades getTradeHistory(TradeHistoryParams params) throws IOException {
    String cursor = null;
    Integer pageSize = null;
    String type = null;

    if (params instanceof TradeHistoryParamNextPageCursor) {
      cursor = ((TradeHistoryParamNextPageCursor) params).getNextPageCursor();
    }

    if (params instanceof TradeHistoryParamLimit) {
      pageSize = ((TradeHistoryParamLimit) params).getLimit();
    }

    if (params instanceof BitpandaTradeHistoryParams) {
      type = ((BitpandaTradeHistoryParams) params).getType();
    }

    String apiKey = exchange.getExchangeSpecification().getApiKey();

    BitpandaResponse<List<BitpandaTrade>> response = bitpanda.getTrades(apiKey, type, cursor, pageSize);

    return BitpandaAdapters.adaptTradeHistory(
        response.getData(), ((BitpandaExchange) exchange).getAssetIdMap());
  }
}
