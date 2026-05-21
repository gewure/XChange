package org.knowm.xchange.huobi.service;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.QueryParam;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.crypto.Mac;
import org.knowm.xchange.service.BaseParamsDigest;
import si.mazi.rescu.Params;
import si.mazi.rescu.RestInvocation;

public class HuobiDigest extends BaseParamsDigest {

  private final boolean isEd25519;
  private PrivateKey ed25519PrivateKey;

  private HuobiDigest(String secretKey) {
    super(secretKey.startsWith("-----BEGIN") ? "dummy" : secretKey, HMAC_SHA_256);
    if (secretKey.startsWith("-----BEGIN")) {
      this.isEd25519 = true;
      try {
        String privateKeyPEM = secretKey
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("\\n", "")
            .replaceAll("\n", "")
            .replaceAll("\r", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        byte[] encoded = Base64.getDecoder().decode(privateKeyPEM);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
        KeyFactory kf = KeyFactory.getInstance("Ed25519");
        this.ed25519PrivateKey = kf.generatePrivate(keySpec);
      } catch (Exception e) {
        throw new IllegalArgumentException("Failed to load Ed25519 private key: " + e.getMessage(), e);
      }
    } else {
      this.isEd25519 = false;
      this.ed25519PrivateKey = null;
    }
  }

  static HuobiDigest createInstance(String secretKey) {
    return secretKey == null ? null : new HuobiDigest(secretKey);
  }

  @Override
  public String digestParams(RestInvocation restInvocation) {
    if (isEd25519) {
      Params queryParams = restInvocation.getParamsMap().get(QueryParam.class);
      if (queryParams != null) {
        try {
          java.lang.reflect.Field dataField = Params.class.getDeclaredField("data");
          dataField.setAccessible(true);
          Map<String, Object> data = (Map<String, Object>) dataField.get(queryParams);
          if (data.containsKey("SignatureMethod")) {
            data.put("SignatureMethod", "Ed25519");
          }
        } catch (Exception e) {
          // ignore
        }
      }
    }

    String httpMethod = restInvocation.getHttpMethod();
    String host = getHost(restInvocation.getBaseUrl());
    String method = "/" + restInvocation.getMethodPath();
    String query =
        Stream.of(
                restInvocation.getParamsMap().get(FormParam.class),
                restInvocation.getParamsMap().get(QueryParam.class))
            .map(Params::asHttpHeaders)
            .map(Map::entrySet)
            .flatMap(Collection::stream)
            .filter(e -> !"Signature".equals(e.getKey()))
            .sorted(Map.Entry.comparingByKey())
            .map(e -> e.getKey() + "=" + encodeValue(e.getValue()))
            .collect(Collectors.joining("&"));
    String toSign = String.format("%s\n%s\n%s\n%s", httpMethod, host, method, query);
    System.out.println("HuobiDigest - toSign:\n" + toSign);

    if (isEd25519) {
      try {
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(ed25519PrivateKey);
        sig.update(toSign.getBytes(StandardCharsets.UTF_8));
        byte[] signatureBytes = sig.sign();
        String signature = Base64.getEncoder().encodeToString(signatureBytes).trim();
        System.out.println("HuobiDigest (Ed25519) - signature: " + signature);
        return signature;
      } catch (Exception e) {
        throw new IllegalStateException("Failed to sign with Ed25519: " + e.getMessage(), e);
      }
    } else {
      Mac mac = getMac();
      String signature =
          Base64.getEncoder()
              .encodeToString(mac.doFinal(toSign.getBytes(StandardCharsets.UTF_8)))
              .trim();
      System.out.println("HuobiDigest - signature: " + signature);
      return signature;
    }
  }

  private String getHost(String url) {
    URI uri;
    try {
      uri = new URI(url);
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e.getMessage());
    }
    return uri.getHost();
  }

  private String encodeValue(String value) {
    String ret;
    try {
      ret = URLEncoder.encode(value, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException(e.getMessage());
    }
    return ret;
  }
}
