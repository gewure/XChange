package org.knowm.xchange.polymarket;

import com.fasterxml.jackson.annotation.JsonProperty;
import si.mazi.rescu.HttpStatusExceptionSupport;

public class PolymarketException extends HttpStatusExceptionSupport {

  public PolymarketException(@JsonProperty("message") String message) {
    super(message);
  }
}
