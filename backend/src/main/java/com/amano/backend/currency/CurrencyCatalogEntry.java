package com.amano.backend.currency;

/**
 * One currency of the "Ver más" catalog (027, contracts/currencies-api.md):
 * {@code code} is the {@link com.amano.backend.user.Currency} name the rest of
 * the API accepts and {@code bcraName} the BCRA {@code denominacion}, as received.
 */
public record CurrencyCatalogEntry(String code, String bcraName) {
}
