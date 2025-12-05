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
import org.knowm.xchange.service.trade.params.TradeHistoryParams;

public class BitpandaTradeService extends BitpandaBaseService implements TradeService {

  public BitpandaTradeService(Exchange exchange) {
    super(exchange);
  }

  @Override
  public UserTrades getTradeHistory(TradeHistoryParams params) throws IOException {
    // TODO: Handle params (cursor, page_size, type)
    // For now, fetch default (all/recent)
    BitpandaResponse<List<BitpandaTrade>> response = bitpanda.getTrades(
        null, // apiKey is handled by header param or signature? Wait, Bitpanda interface has
              // @HeaderParam("X-Api-Key")
        null, // type
        null, // cursor
        null // page_size
    );

    // Wait, apiKey is injected by Si.mazi.rescu if configured?
    // In Bitpanda.java I defined @HeaderParam("X-Api-Key") String apiKey.
    // I should pass it here.
    String apiKey = exchange.getExchangeSpecification().getApiKey();

    response = bitpanda.getTrades(apiKey, null, null, null);

    return BitpandaAdapters.adaptTradeHistory(
        response.getData(), ((BitpandaExchange) exchange).getAssetIdMap());
  }
}
