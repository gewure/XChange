package org.knowm.xchange.uniswap.service.contracts;

import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;

import java.math.BigInteger;
import java.util.Collections;

/**
 * Manual wrapper for WETH9 (Wrapped ETH) contract.
 */
public class WETH9 {

    public static Function deposit(BigInteger amount) {
        // deposit() is payable, so amount is sent as value, not argument.
        // The function signature is just "deposit()"
        return new Function(
                "deposit",
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    public static Function withdraw(BigInteger amount) {
        return new Function(
                "withdraw",
                Collections.singletonList(new Uint256(amount)),
                Collections.emptyList()
        );
    }
}
