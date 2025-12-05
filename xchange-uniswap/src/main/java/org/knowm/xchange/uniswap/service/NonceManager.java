package org.knowm.xchange.uniswap.service;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages nonces locally to avoid slow RPC calls for every transaction.
 * Thread-safe.
 */
public class NonceManager {

    private final Web3j web3j;
    private final Map<String, AtomicReference<BigInteger>> nonces = new ConcurrentHashMap<>();

    public NonceManager(Web3j web3j) {
        this.web3j = web3j;
    }

    public BigInteger getNonce(String address) throws IOException {
        AtomicReference<BigInteger> nonceRef = nonces.computeIfAbsent(address, k -> new AtomicReference<>());
        
        synchronized (nonceRef) {
            if (nonceRef.get() == null) {
                // Initial fetch
                resetNonce(address);
            }
            // Return current and increment
            return nonceRef.getAndAccumulate(BigInteger.ONE, BigInteger::add);
        }
    }

    public void resetNonce(String address) throws IOException {
        EthGetTransactionCount ethGetTransactionCount = web3j.ethGetTransactionCount(
                address, DefaultBlockParameterName.PENDING).send(); // Use PENDING to account for mempool txs
        
        if (ethGetTransactionCount.hasError()) {
            throw new IOException("Failed to fetch nonce: " + ethGetTransactionCount.getError().getMessage());
        }
        
        AtomicReference<BigInteger> nonceRef = nonces.computeIfAbsent(address, k -> new AtomicReference<>());
        synchronized (nonceRef) {
            nonceRef.set(ethGetTransactionCount.getTransactionCount());
        }
    }
}
