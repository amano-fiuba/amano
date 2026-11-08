package com.amano.backend.currency;

/**
 * The BCRA catalog could not be read and there is no cached copy (027,
 * contracts/currencies-api.md). Mapped to HTTP 503 {@code ProblemDetail}.
 */
public class CurrencyCatalogUnavailableException extends RuntimeException {

    public static final String CODE = "CURRENCY_CATALOG_UNAVAILABLE";
    public static final String MESSAGE = "No se pudo obtener el catálogo de monedas; reintentá en unos minutos";

    public CurrencyCatalogUnavailableException() {
        super(MESSAGE);
    }

    public CurrencyCatalogUnavailableException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
