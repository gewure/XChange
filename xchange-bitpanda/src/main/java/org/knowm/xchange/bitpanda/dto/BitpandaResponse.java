package org.knowm.xchange.bitpanda.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BitpandaResponse<T> {

  @JsonProperty("data")
  private T data;

  @JsonProperty("meta")
  private Object meta;

  @JsonProperty("links")
  private Object links;

  public T getData() {
    return data;
  }

  public void setData(T data) {
    this.data = data;
  }

  public Object getMeta() {
    return meta;
  }

  public void setMeta(Object meta) {
    this.meta = meta;
  }

  public Object getLinks() {
    return links;
  }

  public void setLinks(Object links) {
    this.links = links;
  }
}
