package org.knowm.xchange.bybit.service;

import static org.knowm.xchange.utils.DigestUtils.bytesToHex;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.QueryParam;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import lombok.SneakyThrows;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.service.BaseParamsDigest;
import si.mazi.rescu.Params;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.RestInvocation;

public class BybitDigest extends BaseParamsDigest {

  public static final String X_BAPI_API_KEY = "X-BAPI-API-KEY";
  public static final String X_BAPI_SIGN = "X-BAPI-SIGN";
  public static final String X_BAPI_TIMESTAMP = "X-BAPI-TIMESTAMP";
  public static final String X_BAPI_RECV_WINDOW = "X-BAPI-RECV-WINDOW";

  private java.security.PrivateKey privateKey;
  private final boolean isRsa;

  public BybitDigest(String secretKeyBase64) {
    super(secretKeyBase64.contains("PRIVATE KEY") ? "dummy_key_for_rsa_auth_signing_dummy_key" : secretKeyBase64, HMAC_SHA_256);
    if (secretKeyBase64.contains("PRIVATE KEY")) {
        this.isRsa = true;
        try {
            this.privateKey = loadPrivateKey(secretKeyBase64);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to load RSA private key", e);
        }
    } else {
        this.isRsa = false;
        this.privateKey = null;
    }
  }

  private static java.security.PrivateKey loadPrivateKey(String pem) throws Exception {
      String privateKeyPEM = pem
          .replace("\\n", "")
          .replace("-----BEGIN PRIVATE KEY-----", "")
          .replace("-----END PRIVATE KEY-----", "")
          .replace("-----BEGIN RSA PRIVATE KEY-----", "")
          .replace("-----END RSA PRIVATE KEY-----", "")
          .replaceAll("\\s", "");
      byte[] encoded = java.util.Base64.getDecoder().decode(privateKeyPEM);
      java.security.spec.PKCS8EncodedKeySpec keySpec = new java.security.spec.PKCS8EncodedKeySpec(encoded);
      java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");
      return kf.generatePrivate(keySpec);
  }

  public static ParamsDigest createInstance(String secretKeyBase64) {
    if (secretKeyBase64 != null) {
      return new BybitDigest(secretKeyBase64);
    } else {
      return null;
    }
  }

  @SneakyThrows
  @Override
  public String digestParams(RestInvocation restInvocation) {
    Map<String, String> headers = getHeaders(restInvocation);
    Map<String, String> params = getInputParams(restInvocation);
    Map<String, String> sortedParams = new TreeMap<>(params);

    // timestamp + API key + (recv_window) + (queryString | jsonBodyString)
    String plainText = getPlainText(restInvocation, sortedParams);
    String input =
        headers.get(X_BAPI_TIMESTAMP)
            + headers.get(X_BAPI_API_KEY)
            + headers.getOrDefault(X_BAPI_RECV_WINDOW, "")
            + plainText;

    if (isRsa) {
        java.security.Signature signature = java.security.Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(input.getBytes(StandardCharsets.UTF_8));
        byte[] signed = signature.sign();
        return java.util.Base64.getEncoder().encodeToString(signed);
    } else {
        Mac mac = getMac();
        mac.update(input.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(mac.doFinal());
    }
  }

  private static String getPlainText(
      RestInvocation restInvocation, Map<String, String> sortedParams) {
    if ("GET".equals(restInvocation.getHttpMethod())) {
      Params p = Params.of();
      sortedParams.forEach(p::add);
      return p.asQueryString();
    }
    if ("POST".equals(restInvocation.getHttpMethod())) {
      return restInvocation.getRequestBody();
    }
    throw new NotYetImplementedForExchangeException(
        "Only GET and POST are supported for plain text");
  }

  private Map<String, String> getHeaders(RestInvocation restInvocation) {
    return restInvocation.getParamsMap().get(HeaderParam.class).asHttpHeaders();
  }

  private Map<String, String> getInputParams(RestInvocation restInvocation) {
    if ("GET".equals(restInvocation.getHttpMethod())) {
      return restInvocation.getParamsMap().get(QueryParam.class).asHttpHeaders();
    }
    if ("POST".equals(restInvocation.getHttpMethod())) {
      return restInvocation.getParamsMap().get(FormParam.class).asHttpHeaders();
    }
    throw new NotYetImplementedForExchangeException("Only GET and POST are supported in digest");
  }
}
