package org.knowm.xchange.uniswap.service.contracts;

import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Int24;
import org.web3j.abi.datatypes.generated.Uint16;
import org.web3j.abi.datatypes.generated.Uint160;
import org.web3j.abi.datatypes.generated.Uint24;
import org.web3j.abi.datatypes.generated.Uint8;

import java.util.Arrays;
import java.util.Collections;

/**
 * Manual wrapper for Uniswap V3 Pool.
 */
public class UniswapPool {

    public static Function slot0() {
        return new Function(
                "slot0",
                Collections.emptyList(),
                Arrays.asList(
                        new TypeReference<Uint160>() {}, // sqrtPriceX96
                        new TypeReference<Int24>() {},   // tick
                        new TypeReference<Uint16>() {},  // observationIndex
                        new TypeReference<Uint16>() {},  // observationCardinality
                        new TypeReference<Uint16>() {},  // observationCardinalityNext
                        new TypeReference<Uint8>() {},   // feeProtocol
                        new TypeReference<Bool>() {}     // unlocked
                )
        );
    }

    public static Function token0() {
        return new Function(
                "token0",
                Collections.emptyList(),
                Collections.singletonList(new TypeReference<Address>() {})
        );
    }

    public static Function token1() {
        return new Function(
                "token1",
                Collections.emptyList(),
                Collections.singletonList(new TypeReference<Address>() {})
        );
    }
    
    public static Function fee() {
        return new Function(
                "fee",
                Collections.emptyList(),
                Collections.singletonList(new TypeReference<Uint24>() {}) // fee is uint24
        );
    }
}
