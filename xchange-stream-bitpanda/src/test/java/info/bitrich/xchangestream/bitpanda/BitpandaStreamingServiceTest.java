package info.bitrich.xchangestream.bitpanda;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class BitpandaStreamingServiceTest {

  @Test
  public void shouldInitializeService() {
    BitpandaStreamingService streamingService = new BitpandaStreamingService("wss://api.bitpanda.com/v1/ws",
        "dummy-api-key");
    assertThat(streamingService).isNotNull();
  }
}
