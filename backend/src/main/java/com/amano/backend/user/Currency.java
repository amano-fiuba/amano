package com.amano.backend.user;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Currencies accepted by expenses, debts, payments and user preferences.
 *
 * <p>Since 027 (FR-014, research.md R1) this is the BCRA catalog filtered to
 * current currencies that the BCRA quotes: extinct ones (ATS, DEM, ESP, ITL,
 * VEB), non-payment units (REF, XAU, XAG, XDR), the offshore yuan CNH and VND
 * (quoted per 1000 units) are left out. ARS, USD and EUR stay first so that
 * ordinal-ordered views (balances per currency) keep their previous order.
 *
 * <p>Persisted by name. The BCRA still publishes the Mexican peso under its
 * pre-1993 code, so {@link #bcraCode()} maps {@code MXN} to {@code MXP}.
 * 0 persisted preference rows still means ARS by default at read time (002, FR-011).
 */
public enum Currency {
    ARS,
    USD,
    EUR,
    AUD,
    AWG,
    BOB,
    BRL,
    CAD,
    CHF,
    CLP,
    CNY,
    COP,
    CZK,
    DKK,
    GBP,
    HKD,
    ILS,
    INR,
    JPY,
    MXN("MXP"),
    NIO,
    NOK,
    NZD,
    PEN,
    PYG,
    RSD,
    RUB,
    SEK,
    SGD,
    TRY,
    UYU,
    ZAR;

    /** 027 R3: the group primary currency keeps the 022 catalog. */
    public static final Set<Currency> GROUP_PRIMARY = Collections.unmodifiableSet(EnumSet.of(ARS, USD, EUR));

    private final String bcraCode;

    Currency() {
        this.bcraCode = null;
    }

    Currency(String bcraCode) {
        this.bcraCode = bcraCode;
    }

    /** Code the BCRA uses for this currency in Maestros/Divisas and Cotizaciones. */
    public String bcraCode() {
        return bcraCode == null ? name() : bcraCode;
    }

    /** The currency the BCRA publishes as {@code code}, if it is a supported one. */
    public static Optional<Currency> fromBcraCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        for (Currency currency : values()) {
            if (currency.bcraCode().equals(normalized)) {
                return Optional.of(currency);
            }
        }
        return Optional.empty();
    }
}
