package org.knowm.xchange.bitpanda.service;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.service.account.AccountService;

public class BitpandaAccountService extends BitpandaBaseService implements AccountService {

  public BitpandaAccountService(Exchange exchange) {
    super(exchange);
  }
}
