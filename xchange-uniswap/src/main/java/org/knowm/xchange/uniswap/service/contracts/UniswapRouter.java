package org.knowm.xchange.uniswap.service.contracts;

import org.web3j.abi.TypeEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Uint160;
import org.web3j.abi.datatypes.generated.Uint24;
import org.web3j.abi.datatypes.generated.Uint256;

import java.math.BigInteger;

/**
 * Manual wrapper for Uniswap V3 SwapRouter.
 */
public class UniswapRouter {

    public static String encodeExactInputSingle(
            String tokenIn,
            String tokenOut,
            BigInteger fee,
            String recipient,
            BigInteger deadline,
            BigInteger amountIn,
            BigInteger amountOutMinimum,
            BigInteger sqrtPriceLimitX96) {

        // Manual encoding of ExactInputSingleParams struct
        // Selector: 0x414bf389
        String selector = "0x414bf389";

        StringBuilder encodedData = new StringBuilder();
        encodedData.append(TypeEncoder.encode(new Address(tokenIn)));
        encodedData.append(TypeEncoder.encode(new Address(tokenOut)));
        encodedData.append(TypeEncoder.encode(new Uint24(fee)));
        encodedData.append(TypeEncoder.encode(new Address(recipient)));
        encodedData.append(TypeEncoder.encode(new Uint256(deadline)));
        encodedData.append(TypeEncoder.encode(new Uint256(amountIn)));
        encodedData.append(TypeEncoder.encode(new Uint256(amountOutMinimum)));
        encodedData.append(TypeEncoder.encode(new Uint160(sqrtPriceLimitX96)));

        return selector + encodedData.toString();
    }
}
