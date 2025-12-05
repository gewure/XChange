package org.knowm.xchange.bitpanda.service;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.bitpanda.Bitpanda;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.service.BaseExchangeService;
import org.knowm.xchange.service.BaseService;

public class BitpandaBaseService extends BaseExchangeService implements BaseService {

  protected final Bitpanda bitpanda;

  protected BitpandaBaseService(Exchange exchange) {
    super(exchange);
    this.bitpanda =
        ExchangeRestProxyBuilder.forInterface(Bitpanda.class, exchange.getExchangeSpecification())
            .build();
  }
}
