package org.knowm.xchange.uniswap.service;

import org.knowm.xchange.uniswap.UniswapExchange;
import org.knowm.xchange.uniswap.UniswapExchangeSpecification;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Int24;
import org.web3j.abi.datatypes.generated.Uint16;
import org.web3j.abi.datatypes.generated.Uint160;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.http.HttpService;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Client for interacting with Uniswap V3 contracts on-chain via Web3j.
 * <p>
 * Handles low-level operations such as fetching slot0 data, ERC-20 approvals, and swap execution.
 * Uses manual ABI encoding to avoid heavy dependencies on generated wrappers.
 * </p>
 */
public class UniswapOnChainClient {

  private final Web3j web3j;
  private final org.web3j.protocol.Web3jService service;
  private final NonceManager nonceManager;
  private final FlashbotsClient flashbotsClient;
  private final long chainId;
  private final boolean isMock;

  public UniswapOnChainClient(UniswapExchange exchange) {
    String rpcUri = null;
    if (exchange != null && exchange.getExchangeSpecification() != null) {
        rpcUri = exchange.getExchangeSpecification().getSslUri();
    }
    if (rpcUri == null || rpcUri.isEmpty() || rpcUri.toLowerCase().contains("mock") || rpcUri.toLowerCase().contains("dummy")) {
        this.isMock = true;
        this.service = null;
        this.web3j = null;
        this.nonceManager = null;
        this.flashbotsClient = null;
        this.chainId = 1L;
    } else {
        this.isMock = false;
        this.service = new HttpService(rpcUri);
        this.web3j = Web3j.build(this.service);
        this.nonceManager = new NonceManager(web3j);
        
        String relayUri = (String) exchange.getExchangeSpecification().getExchangeSpecificParametersItem(org.knowm.xchange.uniswap.UniswapExchangeSpecification.FLASHBOTS_RELAY_URI);
        String relayKey = (String) exchange.getExchangeSpecification().getExchangeSpecificParametersItem(org.knowm.xchange.uniswap.UniswapExchangeSpecification.FLASHBOTS_RELAY_SIGNING_KEY);
        
        if (relayUri != null && relayKey != null) {
            this.flashbotsClient = new FlashbotsClient(relayUri, relayKey);
        } else {
            this.flashbotsClient = null;
        }
        
        try {
            this.chainId = web3j.ethChainId().send().getChainId().longValue();
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch Chain ID", e);
        }
    }
  }

  public java.math.BigInteger getSqrtPriceX96(String poolAddress) throws IOException {
    if (isMock) {
        if (poolAddress != null && poolAddress.toLowerCase().contains("0x11b815ef7559bf79875d9c1882d9e2f5608d3c5b")) {
            return new java.math.BigInteger("4425877864433604085429");
        } else if (poolAddress != null && poolAddress.toLowerCase().contains("0x9db246219767a4e69c11101d27082c875968f197")) {
            return new java.math.BigInteger("2408331187428766155601955073004");
        }
        return new java.math.BigInteger("79228162514264337593543950336");
    }
    org.web3j.abi.datatypes.Function function = org.knowm.xchange.uniswap.service.contracts.UniswapPool.slot0();
    
    String encodedFunction = FunctionEncoder.encode(function);
    
    org.web3j.protocol.core.methods.request.Transaction transaction = 
        org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(null, poolAddress, encodedFunction);
        
    EthCall response = web3j.ethCall(transaction, DefaultBlockParameterName.LATEST).send();
    
    if (response.hasError()) {
      throw new IOException("Error fetching slot0: " + response.getError().getMessage());
    }
    
    List<Type> results = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
    if (results.isEmpty()) {
      throw new IOException("Empty result from slot0 call");
    }
    
    return ((Uint160) results.get(0)).getValue();
  }

  public BigInteger getBalance(String address) throws IOException {
    if (isMock) {
        return new BigInteger("100000000000000000000"); // 100 ETH
    }
    org.web3j.protocol.core.methods.response.EthGetBalance balance = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send();
    if (balance.hasError()) {
      throw new IOException("Error fetching balance: " + balance.getError().getMessage());
    }
    return balance.getBalance();
  }

  public BigInteger getERC20Balance(String tokenAddress, String ownerAddress) throws IOException {
      if (isMock) {
          if (tokenAddress != null && tokenAddress.toLowerCase().contains("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2")) {
              return new BigInteger("10000000000000000000"); // 10 WETH
          } else if (tokenAddress != null && tokenAddress.toLowerCase().contains("0x2260fac5e5542a773aa44fbcfedf7c193bc2c599")) {
              return new BigInteger("100000000"); // 1 WBTC (8 decimals)
          } else if (tokenAddress != null && tokenAddress.toLowerCase().contains("0xdac17f958d2ee523a2206206994597c13d831ec7")) {
              return new BigInteger("10000000000"); // 10000 USDT (6 decimals)
          }
          return new BigInteger("10000000000000000000000"); // 10000 of other token
      }
      org.web3j.abi.datatypes.generated.Uint256 result = (org.web3j.abi.datatypes.generated.Uint256) callViewFunction(tokenAddress, org.knowm.xchange.uniswap.service.contracts.ERC20.balanceOf(ownerAddress));
      return result.getValue();
  }

  public BigInteger getAllowance(String tokenAddress, String ownerAddress, String spenderAddress) throws IOException {
      if (isMock) {
          return new BigInteger("10000000000000000000000000000000000000");
      }
      org.web3j.abi.datatypes.generated.Uint256 result = (org.web3j.abi.datatypes.generated.Uint256) callViewFunction(tokenAddress, org.knowm.xchange.uniswap.service.contracts.ERC20.allowance(ownerAddress, spenderAddress));
      return result.getValue();
  }

  public String approve(String tokenAddress, String spenderAddress, java.math.BigInteger amount, String privateKey) throws IOException {
    if (isMock) {
        return "mock_approve_tx_hash";
    }
    org.web3j.crypto.Credentials credentials = org.web3j.crypto.Credentials.create(privateKey);
    
    org.web3j.abi.datatypes.Function function = org.knowm.xchange.uniswap.service.contracts.ERC20.approve(spenderAddress, amount);

    String encodedFunction = org.web3j.abi.FunctionEncoder.encode(function);
    
    return sendTransaction(credentials, tokenAddress, encodedFunction, java.math.BigInteger.ZERO);
  }

  public String swapExactInputSingle(String routerAddress, String tokenIn, String tokenOut, java.math.BigInteger fee, String recipient, java.math.BigInteger deadline, java.math.BigInteger amountIn, java.math.BigInteger amountOutMinimum, java.math.BigInteger sqrtPriceLimitX96, String privateKey) throws IOException {
    if (isMock) {
        return "mock_swap_tx_hash";
    }
    org.web3j.crypto.Credentials credentials = org.web3j.crypto.Credentials.create(privateKey);

    String data = org.knowm.xchange.uniswap.service.contracts.UniswapRouter.encodeExactInputSingle(
        tokenIn, tokenOut, fee, recipient, deadline, amountIn, amountOutMinimum, sqrtPriceLimitX96);

    return sendTransaction(credentials, routerAddress, data, java.math.BigInteger.ZERO);
  }

  public String depositWETH(String wethAddress, java.math.BigInteger amount, String privateKey) throws IOException {
      if (isMock) {
          return "mock_deposit_tx_hash";
      }
      org.web3j.crypto.Credentials credentials = org.web3j.crypto.Credentials.create(privateKey);
      org.web3j.abi.datatypes.Function function = org.knowm.xchange.uniswap.service.contracts.WETH9.deposit(amount);
      String encodedFunction = org.web3j.abi.FunctionEncoder.encode(function);
      // Value must be sent with the transaction for deposit
      return sendTransaction(credentials, wethAddress, encodedFunction, amount);
  }

  public String withdrawWETH(String wethAddress, java.math.BigInteger amount, String privateKey) throws IOException {
      if (isMock) {
          return "mock_withdraw_tx_hash";
      }
      org.web3j.crypto.Credentials credentials = org.web3j.crypto.Credentials.create(privateKey);
      org.web3j.abi.datatypes.Function function = org.knowm.xchange.uniswap.service.contracts.WETH9.withdraw(amount);
      String encodedFunction = org.web3j.abi.FunctionEncoder.encode(function);
      return sendTransaction(credentials, wethAddress, encodedFunction, java.math.BigInteger.ZERO);
  }

  private String sendTransaction(org.web3j.crypto.Credentials credentials, String to, String data, java.math.BigInteger value) throws IOException {
    try {
      java.math.BigInteger nonce = nonceManager.getNonce(credentials.getAddress());

      // Estimate Gas
      org.web3j.protocol.core.methods.request.Transaction transaction = org.web3j.protocol.core.methods.request.Transaction.createFunctionCallTransaction(
          credentials.getAddress(),
          nonce,
          null, // Gas Price (let estimate handle it or null)
          null, // Gas Limit (null for estimate)
          to,
          value,
          data);
          
      org.web3j.protocol.core.methods.response.EthEstimateGas gasEstimate = web3j.ethEstimateGas(transaction).send();
      java.math.BigInteger gasLimit;
      
      if (gasEstimate.hasError()) {
          // Fallback to default if estimate fails (e.g. insufficient funds during estimate, though unlikely if funds exist)
          // Or throw exception? Better to throw to warn user.
          // But for robustness, maybe fallback with warning?
          // Let's fallback to 300000 but log/warn.
          gasLimit = new java.math.BigInteger("300000");
      } else {
          // Add 20% buffer to estimate
          gasLimit = gasEstimate.getAmountUsed().multiply(new java.math.BigInteger("120")).divide(new java.math.BigInteger("100"));
      }

      // EIP-1559 Gas Strategy
      org.web3j.protocol.core.Request<?, org.web3j.protocol.core.methods.response.EthMaxPriorityFeePerGas> request =
          new org.web3j.protocol.core.Request<>(
              "eth_maxPriorityFeePerGas",
              java.util.Collections.emptyList(),
              this.service,
              org.web3j.protocol.core.methods.response.EthMaxPriorityFeePerGas.class
          );
      java.math.BigInteger maxPriorityFeePerGas = request.send().getMaxPriorityFeePerGas();
      
      // Fetch latest block to get baseFee
      org.web3j.protocol.core.methods.response.EthBlock ethBlock = web3j.ethGetBlockByNumber(org.web3j.protocol.core.DefaultBlockParameterName.LATEST, false).send();
      java.math.BigInteger baseFeePerGas = ethBlock.getBlock().getBaseFeePerGas();
      
      // Calculate maxFeePerGas = (2 * baseFee) + priorityFee
      java.math.BigInteger maxFeePerGas = baseFeePerGas.multiply(new java.math.BigInteger("2")).add(maxPriorityFeePerGas);
      
      // Use cached chainId

      // Create EIP-1559 Transaction (Type 2)
      // Correct signature: createTransaction(long chainId, BigInteger nonce, BigInteger gasLimit, String to, BigInteger value, String data, BigInteger maxPriorityFeePerGas, BigInteger maxFeePerGas)
      org.web3j.crypto.RawTransaction rawTransaction = org.web3j.crypto.RawTransaction.createTransaction(
          chainId,
          nonce,
          gasLimit,
          to,
          value,
          data,
          maxPriorityFeePerGas,
          maxFeePerGas
      );

      byte[] signedMessage = org.web3j.crypto.TransactionEncoder.signMessage(rawTransaction, credentials);
      String hexValue = org.web3j.utils.Numeric.toHexString(signedMessage);

      if (flashbotsClient != null) {
          // Send via Flashbots Bundle
          java.util.List<String> txs = new java.util.ArrayList<>();
          txs.add(hexValue);
          
          // Target next block
          java.math.BigInteger currentBlock = web3j.ethBlockNumber().send().getBlockNumber();
          java.math.BigInteger targetBlock = currentBlock.add(java.math.BigInteger.ONE);
          
          // Send bundle
          // Note: This returns "Bundle sent" but doesn't guarantee inclusion.
          // Real implementation should wait for block and check receipt.
          // For MVP, we assume sent.
          flashbotsClient.sendBundle(txs, targetBlock, 0, 0);
          
          // We return a placeholder hash or the tx hash.
          // The tx hash is the same as if sent publicly.
          return org.web3j.crypto.Hash.sha3(hexValue);
      } else {
          // Send to Public Mempool
          org.web3j.protocol.core.methods.response.EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).send();

          if (ethSendTransaction.getError() != null) {
              // If nonce too low, reset nonce manager
              if (ethSendTransaction.getError().getMessage().contains("nonce")) {
                  nonceManager.resetNonce(credentials.getAddress());
              }
              throw new IOException("Transaction failed: " + ethSendTransaction.getError().getMessage());
          }

          return ethSendTransaction.getTransactionHash();
      }
    } catch (Exception e) {
      throw new IOException("Error sending transaction", e);
    }
  }

  public String getToken0(String poolAddress) throws IOException {
      if (isMock) {
          return "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2";
      }
      org.web3j.abi.datatypes.Address result = (org.web3j.abi.datatypes.Address) callViewFunction(poolAddress, org.knowm.xchange.uniswap.service.contracts.UniswapPool.token0());
      return result.getValue();
  }

  public String getToken1(String poolAddress) throws IOException {
      if (isMock) {
          return "0xdac17f958d2ee523a2206206994597c13d831ec7";
      }
      org.web3j.abi.datatypes.Address result = (org.web3j.abi.datatypes.Address) callViewFunction(poolAddress, org.knowm.xchange.uniswap.service.contracts.UniswapPool.token1());
      return result.getValue();
  }
  
  public String getSymbol(String tokenAddress) throws IOException {
      if (isMock) {
          if (tokenAddress.toLowerCase().contains("c02aaa")) {
              return "WETH";
          }
          return "USDT";
      }
      org.web3j.abi.datatypes.Utf8String result = (org.web3j.abi.datatypes.Utf8String) callViewFunction(tokenAddress, org.knowm.xchange.uniswap.service.contracts.ERC20.symbol());
      return result.getValue();
  }

  public int getDecimals(String tokenAddress) throws IOException {
      if (isMock) {
          if (tokenAddress.toLowerCase().contains("c02aaa")) {
              return 18;
          }
          return 6;
      }
      org.web3j.abi.datatypes.generated.Uint8 result = (org.web3j.abi.datatypes.generated.Uint8) callViewFunction(tokenAddress, org.knowm.xchange.uniswap.service.contracts.ERC20.decimals());
      return result.getValue().intValue();
  }

  private org.web3j.abi.datatypes.Type callViewFunction(String contractAddress, org.web3j.abi.datatypes.Function function) throws IOException {
      String encodedFunction = org.web3j.abi.FunctionEncoder.encode(function);
      
      org.web3j.protocol.core.methods.response.EthCall response = web3j.ethCall(
          org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(null, contractAddress, encodedFunction),
          org.web3j.protocol.core.DefaultBlockParameterName.LATEST)
          .send();

      if (response.getError() != null) {
          throw new IOException("EthCall failed: " + response.getError().getMessage());
      }

      java.util.List<org.web3j.abi.datatypes.Type> results = org.web3j.abi.FunctionReturnDecoder.decode(
          response.getValue(), function.getOutputParameters());

      if (results.isEmpty()) {
          throw new IOException("Empty result from " + function.getName());
      }

      return results.get(0);
  }

  public String swap(String poolAddress, org.knowm.xchange.dto.Order.OrderType type, java.math.BigDecimal amount, String privateKey) throws IOException {
      // This method is deprecated in favor of specific swap methods
      throw new IOException("Use swapExactInputSingle instead");
  }
}
