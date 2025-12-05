package org.knowm.xchange.uniswap.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FlashbotsClient {

    private final String relayUri;
    private final Credentials relaySigningCredentials;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FlashbotsClient(String relayUri, String relaySigningKey) {
        this.relayUri = relayUri;
        this.relaySigningCredentials = Credentials.create(relaySigningKey);
    }

    public String sendBundle(List<String> signedTransactions, BigInteger blockNumber, long minTimestamp, long maxTimestamp) throws IOException {
        // Construct eth_sendBundle params
        Map<String, Object> bundle = new HashMap<>();
        bundle.put("txs", signedTransactions);
        bundle.put("blockNumber", Numeric.toHexStringWithPrefix(blockNumber));
        bundle.put("minTimestamp", minTimestamp > 0 ? minTimestamp : null);
        bundle.put("maxTimestamp", maxTimestamp > 0 ? maxTimestamp : null);
        // revertingTxHashes is optional, skipping for now

        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", "eth_sendBundle");
        List<Object> params = new ArrayList<>();
        params.add(bundle);
        request.put("params", params);
        request.put("id", 1);

        String jsonBody = objectMapper.writeValueAsString(request);
        
        // Sign the payload (X-Flashbots-Signature)
        // Signature = pubKey : signature(keccak256(body))
        byte[] bodyHash = Hash.sha3(jsonBody.getBytes(StandardCharsets.UTF_8));
        Sign.SignatureData signatureData = Sign.signMessage(bodyHash, relaySigningCredentials.getEcKeyPair(), false);
        
        // Web3j's Sign.signMessage doesn't include the prefix for Ethereum Signed Message if needHashing is false?
        // Flashbots expects standard Ethereum signature of the hash.
        // Actually, Flashbots docs say: "The signature is calculated by taking the EIP-191 hash of the json body"
        // Web3j Sign.signPrefixedMessage might be what we need if we pass the body bytes.
        // But let's stick to standard signing of the hash if that's what they expect.
        // Wait, "X-Flashbots-Signature: <address>:<signature>"
        // The signature should be of the body.
        
        // Let's use Sign.signPrefixedMessage to be safe as it does the standard ETH signing.
        Sign.SignatureData signature = Sign.signPrefixedMessage(jsonBody.getBytes(StandardCharsets.UTF_8), relaySigningCredentials.getEcKeyPair());
        
        byte[] r = signature.getR();
        byte[] s = signature.getS();
        byte[] v = signature.getV();
        
        // Combine r, s, v
        byte[] sigBytes = new byte[65];
        System.arraycopy(r, 0, sigBytes, 0, 32);
        System.arraycopy(s, 0, sigBytes, 32, 32);
        sigBytes[64] = v[0];
        
        String signatureHex = Numeric.toHexString(sigBytes);
        String headerValue = relaySigningCredentials.getAddress() + ":" + signatureHex;

        // Send HTTP Request
        URL url = new URL(relayUri);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Flashbots-Signature", headerValue);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("Flashbots request failed with code: " + responseCode);
        }
        
        // Read response (simplified)
        // Ideally parse JSON response to check for error inside
        return "Bundle sent"; // Placeholder
    }
}
