package org.knowm.xchange.bitpanda.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public class BitpandaTradeAttributes {
    @JsonProperty("status")
    private String status;

    @JsonProperty("type")
    private String type;

    @JsonProperty("cryptocoin_id")
    private String cryptocoinId;

    @JsonProperty("fiat_id")
    private String fiatId;

    @JsonProperty("amount_fiat")
    private BigDecimal amountFiat;

    @JsonProperty("amount_cryptocoin")
    private BigDecimal amountCryptocoin;

    @JsonProperty("fiat_to_eur_rate")
    private BigDecimal fiatToEurRate;

    @JsonProperty("wallet_id")
    private String walletId;

    @JsonProperty("fiat_wallet_id")
    private String fiatWalletId;

    @JsonProperty("payment_option_id")
    private String paymentOptionId;

    @JsonProperty("time")
    private BitpandaTime time;

    @JsonProperty("price")
    private BigDecimal price;

    @JsonProperty("is_swap")
    private boolean isSwap;

    public String getStatus() {
        return status;
    }

    public String getType() {
        return type;
    }

    public String getCryptocoinId() {
        return cryptocoinId;
    }

    public String getFiatId() {
        return fiatId;
    }

    public BigDecimal getAmountFiat() {
        return amountFiat;
    }

    public BigDecimal getAmountCryptocoin() {
        return amountCryptocoin;
    }

    public BigDecimal getFiatToEurRate() {
        return fiatToEurRate;
    }

    public String getWalletId() {
        return walletId;
    }

    public String getFiatWalletId() {
        return fiatWalletId;
    }

    public String getPaymentOptionId() {
        return paymentOptionId;
    }

    public BitpandaTime getTime() {
        return time;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public boolean isSwap() {
        return isSwap;
    }
}
