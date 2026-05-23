# Polymarket V3 XChange Implementation TODOs

## Critical for Production
- [ ] **Gas Estimation**: Currently, a fixed gas limit (300,000) is used. Implement `eth_estimateGas` to calculate dynamic gas limits.
- [ ] **Slippage Protection**: `amountOutMinimum` is currently set to 0. Implement slippage calculation based on user tolerance (e.g., 0.5%).
- [ ] **Allowance Check**: `PolymarketTradeService` approves the router every time. Implement `allowance` check to only approve when necessary.
- [ ] **ETH Handling**: Swaps currently assume ERC-20 tokens. Add logic to handle ETH (wrap to WETH) for swaps involving the native asset.

## Features
- [ ] **ERC-20 Balances**: `PolymarketAccountService` only fetches ETH balance. Add `ERC20.balanceOf` support.
- [ ] **Order Book**: Implement `getOrderBook` by fetching liquidity ticks from the subgraph (complex but possible).
- [ ] **Limit Orders**: Polymarket V3 doesn't support traditional limit orders, but "Range Orders" can be simulated by providing liquidity in a specific tick range.

## Improvements
- [ ] **Contract Wrappers**: Generate Web3j wrappers for Polymarket V3 contracts (Factory, Router, Pool) to avoid manual ABI encoding/decoding.
- [ ] **Error Handling**: Enhance error messages for on-chain failures (e.g., revert reasons).
- [ ] **Solana Support**: Create a separate `xchange-polymarket-solana` (or similar) module if Polymarket expands to non-EVM chains or for other DEXs.
