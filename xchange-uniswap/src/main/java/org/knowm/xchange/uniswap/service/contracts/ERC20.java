package org.knowm.xchange.uniswap.service.contracts;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.abi.datatypes.Utf8String;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Manual wrapper for ERC-20 standard methods.
 */
public class ERC20 {

    public static Function balanceOf(String owner) {
        return new Function(
                "balanceOf",
                Collections.singletonList(new Address(owner)),
                Collections.singletonList(new TypeReference<Uint256>() {})
        );
    }

    public static Function approve(String spender, BigInteger value) {
        return new Function(
                "approve",
                Arrays.asList(new Address(spender), new Uint256(value)),
                Collections.singletonList(new TypeReference<org.web3j.abi.datatypes.Bool>() {})
        );
    }

    public static Function allowance(String owner, String spender) {
        return new Function(
                "allowance",
                Arrays.asList(new Address(owner), new Address(spender)),
                Collections.singletonList(new TypeReference<Uint256>() {})
        );
    }
    
    public static Function decimals() {
        return new Function(
                "decimals",
                Collections.emptyList(),
                Collections.singletonList(new TypeReference<Uint8>() {})
        );
    }

    public static Function symbol() {
        return new Function(
                "symbol",
                Collections.emptyList(),
                Collections.singletonList(new TypeReference<Utf8String>() {})
        );
    }
}
