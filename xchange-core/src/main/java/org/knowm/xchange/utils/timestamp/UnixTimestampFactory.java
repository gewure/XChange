package org.knowm.xchange.utils.timestamp;

public final class UnixTimestampFactory implements TimestampFactory {

  public static final UnixTimestampFactory INSTANCE = new UnixTimestampFactory();

  private UnixTimestampFactory() {}

  @Override
  public Long createValue() {
    return (System.currentTimeMillis() + org.knowm.xchange.utils.DateUtils.getOffset()) / 1000L;
  }
}
