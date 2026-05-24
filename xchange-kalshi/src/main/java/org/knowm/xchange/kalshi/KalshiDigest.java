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

public class KalshiDigest extends BaseParamsDigest {

    private final PrivateKey privateKey;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private KalshiDigest(String privateKeyPem) {
        super(privateKeyPem, "RSA");
        try {
            // Remove PEM headers, footers, and newlines. Try to be robust for different formats
            String privateKeyContent = privateKeyPem
                    .replaceAll("-----BEGIN.*?-----", "")
                    .replaceAll("-----END.*?-----", "")
                    .replaceAll("\\s+", "");

            byte[] keyBytes = Base64.getDecoder().decode(privateKeyContent);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            // Use BouncyCastle provider to ensure support for both standard and alternative formats
            KeyFactory kf = KeyFactory.getInstance("RSA", "BC");
            this.privateKey = kf.generatePrivate(spec);
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

    @Override
    public String digestParams(RestInvocation restInvocation) {
        return sign(
                restInvocation.getParamValue(HeaderParam.class, "KALSHI-ACCESS-TIMESTAMP").toString(),
                restInvocation.getHttpMethod(),
                restInvocation.getPath()
        );
    }
}
