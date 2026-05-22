package org.knowm.xchange.uniswap.service;

import org.junit.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.uniswap.UniswapExchange;
import org.knowm.xchange.uniswap.dto.UniswapInstrument;

import java.io.IOException;

import static org.junit.Assert.assertTrue;

public class UniswapMarketDataServiceTest {

    @Test
    public void testExplicitPoolInjection() throws IOException {
        UniswapExchange exchange = new UniswapExchange();
        ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
        spec.setExchangeSpecificParametersItem("subgraphUrl", "http://mock"); // Trigger mock to skip real subgraph
        exchange.applySpecification(spec);

        UniswapMarketDataService marketDataService = (UniswapMarketDataService) exchange.getMarketDataService();
        marketDataService.loadMetadata();

        System.out.println("Loaded instruments: " + exchange.getExchangeMetaData().getInstruments().keySet());

        boolean ethUstcFound = false;
        boolean usdcUstcFound = false;
        
        for (org.knowm.xchange.instrument.Instrument instr : exchange.getExchangeMetaData().getInstruments().keySet()) {
            if (instr instanceof UniswapInstrument) {
                UniswapInstrument uInstr = (UniswapInstrument) instr;
                if ("0x3cf3d5b9061fac75fc66bc33035803fb067cb4f8".equalsIgnoreCase(uInstr.getPoolAddress())) {
                    ethUstcFound = true;
                }
                if ("0x59d8e2fd24b56a31eb6ac4b5ba749d120a7d1480".equalsIgnoreCase(uInstr.getPoolAddress())) {
                    usdcUstcFound = true;
                }
            }
        }
        
        assertTrue("ETH/USTC pool should be injected", ethUstcFound);
        assertTrue("USDC/USTC pool should be injected", usdcUstcFound);
    }
}
