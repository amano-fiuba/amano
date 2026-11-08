package com.amano.backend.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/** 027 T009 (FR-014, research.md R1): catalog of accepted currencies and BCRA codes. */
class CurrencyTest {

    @Test
    void acceptsExactlyTheCurrentQuotedBcraCurrencies() {
        assertThat(Arrays.stream(Currency.values()).map(Enum::name))
                .containsExactlyInAnyOrder("ARS", "AUD", "AWG", "BOB", "BRL", "CAD", "CHF", "CLP", "CNY",
                        "COP", "CZK", "DKK", "EUR", "GBP", "HKD", "ILS", "INR", "JPY", "MXN", "NIO", "NOK",
                        "NZD", "PEN", "PYG", "RSD", "RUB", "SEK", "SGD", "TRY", "USD", "UYU", "ZAR");
    }

    @Test
    void bcraCodeIsTheNameExceptForTheMexicanPeso() {
        assertThat(Currency.BRL.bcraCode()).isEqualTo("BRL");
        assertThat(Currency.MXN.bcraCode()).isEqualTo("MXP");
        assertThat(Arrays.stream(Currency.values()).filter(c -> !c.bcraCode().equals(c.name())))
                .containsExactly(Currency.MXN);
    }

    @Test
    void fromBcraCodeResolvesSupportedCodesOnly() {
        assertThat(Currency.fromBcraCode("MXP")).contains(Currency.MXN);
        assertThat(Currency.fromBcraCode(" brl ")).contains(Currency.BRL);
        assertThat(Currency.fromBcraCode("MXN")).isEmpty();
        assertThat(Currency.fromBcraCode("VND")).isEmpty();
        assertThat(Currency.fromBcraCode("XAU")).isEmpty();
        assertThat(Currency.fromBcraCode("REF")).isEmpty();
        assertThat(Currency.fromBcraCode("CNH")).isEmpty();
        assertThat(Currency.fromBcraCode(null)).isEmpty();
        assertThat(Currency.fromBcraCode(" ")).isEmpty();
    }

    @Test
    void groupPrimaryKeepsTheThreeOriginalCurrencies() {
        assertThat(Currency.GROUP_PRIMARY).containsExactlyInAnyOrder(Currency.ARS, Currency.USD, Currency.EUR);
    }
}
