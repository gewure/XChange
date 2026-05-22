package org.knowm.xchange.uniswap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class UniswapTradeApiClient {

    private final String apiKey;
    private final ObjectMapper mapper = new ObjectMapper();

    public UniswapTradeApiClient(String apiKey) {
        this.apiKey = apiKey;
    }

    public static class TradePayload {
        public String to;
        public String calldata;
        public BigInteger value;
    }

    public TradePayload getQuoteAndPayload(String tokenIn, String tokenOut, BigInteger amount, String swapper, double slippage) throws IOException {
        URL url = new URL("https://trade-api.gateway.uniswap.org/v1/quote");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("x-api-key", apiKey);
        conn.setRequestProperty("x-universal-router-version", "2.0");
        conn.setDoOutput(true);

        String jsonInput = String.format(
            "{" +
            "\"generatePermitAsTransaction\": false," +
            "\"routingPreference\": \"BEST_PRICE\"," +
            "\"spreadOptimization\": \"EXECUTION\"," +
            "\"urgency\": \"urgent\"," +
            "\"type\": \"EXACT_INPUT\"," +
            "\"amount\": \"%s\"," +
            "\"tokenInChainId\": 1," +
            "\"tokenOutChainId\": 1," +
            "\"tokenIn\": \"%s\"," +
            "\"tokenOut\": \"%s\"," +
            "\"swapper\": \"%s\"," +
            "\"slippageTolerance\": %s" +
            "}", amount.toString(), tokenIn, tokenOut, swapper, slippage);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonInput.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int code = conn.getResponseCode();
        if (code != 200) {
            InputStream err = conn.getErrorStream();
            if (err != null) {
                String errStr = new String(err.readAllBytes(), StandardCharsets.UTF_8);
                throw new IOException("Uniswap Trade API error: " + code + " " + errStr);
            }
            throw new IOException("Uniswap Trade API error: " + code);
        }

        JsonNode root;
        try (InputStream is = conn.getInputStream()) {
            root = mapper.readTree(is);
        }

        JsonNode quote = root.get("quote");
        if (quote == null) {
            throw new IOException("No quote returned from Uniswap Trade API");
        }

        JsonNode methodParams = quote.get("methodParameters");
        if (methodParams == null || methodParams.isNull()) {
            throw new IOException("methodParameters missing. Likely requires Permit2 approval or unsupported route.");
        }

        TradePayload payload = new TradePayload();
        payload.to = methodParams.get("to").asText();
        payload.calldata = methodParams.get("calldata").asText();
        String valueStr = methodParams.has("value") ? methodParams.get("value").asText() : "0";
        if (valueStr.startsWith("0x")) {
            payload.value = new BigInteger(valueStr.substring(2), 16);
        } else {
            payload.value = new BigInteger(valueStr);
        }

        return payload;
    }
}
