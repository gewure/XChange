package org.knowm.xchange.uniswap.dto;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.instrument.Instrument;

import java.io.Serializable;
import java.util.Objects;

public class UniswapInstrument extends Instrument implements Serializable {

  private final Currency base;
  private final Currency counter;
  private final String poolAddress;
  private final int feeTier;

  public UniswapInstrument(Currency base, Currency counter, String poolAddress, int feeTier) {
    this.base = base;
    this.counter = counter;
    this.poolAddress = poolAddress;
    this.feeTier = feeTier;
  }

  @Override
  public Currency getBase() {
    return base;
  }

  @Override
  public Currency getCounter() {
    return counter;
  }

  public String getPoolAddress() {
    return poolAddress;
  }

  public int getFeeTier() {
    return feeTier;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    UniswapInstrument that = (UniswapInstrument) o;
    return feeTier == that.feeTier && Objects.equals(poolAddress, that.poolAddress);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), poolAddress, feeTier);
  }

  @Override
  public String toString() {
    return super.toString() + "[" + feeTier + "]";
  }
}
