package org.knowm.xchange.uniswap.service.contracts;

import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint24;

import java.util.Arrays;
import java.util.Collections;

/**
 * Manual wrapper for Uniswap V3 Factory.
 */
public class UniswapFactory {

    public static Function getPool(String tokenA, String tokenB, int fee) {
        return new Function(
                "getPool",
                Arrays.asList(new Address(tokenA), new Address(tokenB), new Uint24(fee)),
                Collections.singletonList(new TypeReference<Address>() {})
        );
    }
}
