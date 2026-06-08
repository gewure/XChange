package org.knowm.xchange.kalshi;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.PSSParameterSpec;
import java.util.Base64;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.knowm.xchange.service.BaseParamsDigest;
import si.mazi.rescu.RestInvocation;
import jakarta.ws.rs.HeaderParam;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.security.Security;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class KalshiDigest extends BaseParamsDigest {

    private final PrivateKey privateKey;
    private long clockOffsetMs = 0;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private KalshiDigest(String privateKeyPem) {
        super("dummykey", "HmacSHA256");
        calibrateClockOffset();
        try {
            // Remove PEM headers, footers, and newlines. Try to be robust for different formats
            String privateKeyContent = privateKeyPem
                    .replaceAll("-----BEGIN.*?-----", "")
                    .replaceAll("-----END.*?-----", "")
                    .replaceAll("\\s+", "");

            byte[] keyBytes = Base64.getDecoder().decode(privateKeyContent);
            
            KeyFactory kf = KeyFactory.getInstance("RSA", "BC");
            if (privateKeyPem.contains("RSA PRIVATE KEY")) {
                org.bouncycastle.asn1.pkcs.RSAPrivateKey rsaPrivKey = org.bouncycastle.asn1.pkcs.RSAPrivateKey.getInstance(keyBytes);
                java.security.spec.RSAPrivateCrtKeySpec keySpec = new java.security.spec.RSAPrivateCrtKeySpec(
                    rsaPrivKey.getModulus(),
                    rsaPrivKey.getPublicExponent(),
                    rsaPrivKey.getPrivateExponent(),
                    rsaPrivKey.getPrime1(),
                    rsaPrivKey.getPrime2(),
                    rsaPrivKey.getExponent1(),
                    rsaPrivKey.getExponent2(),
                    rsaPrivKey.getCoefficient()
                );
                this.privateKey = kf.generatePrivate(keySpec);
            } else {
                PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
                this.privateKey = kf.generatePrivate(spec);
            }
        } catch (Exception e) {
            throw new ExchangeSecurityException("Cannot parse Kalshi private key", e);
        }
    }

    public static KalshiDigest createInstance(String secretKeyBase64) {
        return secretKeyBase64 == null ? null : new KalshiDigest(secretKeyBase64);
    }

    public String sign(String timestamp, String method, String requestPath) {
        try {
            String message = timestamp + method + requestPath;

            Signature signature = Signature.getInstance("SHA256withRSA/PSS", "BC");

            // Kalshi uses SHA256 with RSA-PSS
            PSSParameterSpec pssSpec = new PSSParameterSpec(
                    "SHA-256",
                    "MGF1",
                    MGF1ParameterSpec.SHA256,
                    32, // Salt length for SHA-256
                    1   // Trailer field
            );

            signature.setParameter(pssSpec);
            signature.initSign(this.privateKey);
            signature.update(message.getBytes(StandardCharsets.UTF_8));

            byte[] signed = signature.sign();
            return Base64.getEncoder().encodeToString(signed);
        } catch (Exception e) {
            throw new ExchangeSecurityException("Cannot sign Kalshi request", e);
        }
    }

    private void calibrateClockOffset() {
        if ("true".equalsIgnoreCase(System.getProperty("kalshi.skip.clock.calibration"))) {
            System.out.println("Skipping Kalshi clock offset calibration (skip system property set)");
            return;
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://external-api.kalshi.com/trade-api/v2/markets"))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            Optional<String> dateHeader = response.headers().firstValue("date");
            if (dateHeader.isPresent()) {
                ZonedDateTime serverTime = ZonedDateTime.parse(dateHeader.get(), DateTimeFormatter.RFC_1123_DATE_TIME);
                this.clockOffsetMs = serverTime.toInstant().toEpochMilli() - System.currentTimeMillis();
                System.out.println("Kalshi Clock Offset auto-calibrated: " + this.clockOffsetMs + " ms");
            }
        } catch (Exception e) {
            System.err.println("Kalshi clock offset calibration failed: " + e.getMessage());
        }
    }

    public long getClockOffsetMs() {
        return clockOffsetMs;
    }

    public static String getCalibratedTimestamp(KalshiDigest digest) {
        return String.valueOf(System.currentTimeMillis() + (digest != null ? digest.getClockOffsetMs() : 0));
    }

    @Override
    public String digestParams(RestInvocation restInvocation) {
        return sign(
                restInvocation.getParamValue(HeaderParam.class, "KALSHI-ACCESS-TIMESTAMP").toString(),
                restInvocation.getHttpMethod(),
                restInvocation.getPath()
        );
    }
}
