package com.amano.backend.currency;

import java.util.List;

/**
 * Source of the currency catalog (027, research.md R1). Implementations return
 * only currencies that are also {@link com.amano.backend.user.Currency}
 * constants, ordered by code, or throw {@link CurrencyCatalogUnavailableException}.
 */
public interface CurrencyCatalogProvider {

    List<CurrencyCatalogEntry> getCatalog();
}
