package com.amano.backend.groupexpense.exchange;

/**
 * Raised when no official exchange rate is available for the requested pair and
 * date within the 7-day lookback window, or when the BCRA API is unreachable
 * after retries (022 D8, T050/T053). Mapped to HTTP 503 {@code ProblemDetail}.
 */
public class ExchangeRateUnavailableException extends RuntimeException {

    public static final String CODE = "EXCHANGE_RATE_UNAVAILABLE";
    public static final String MESSAGE =
            "No se pudo obtener la cotización; reintente después o registre en moneda del grupo";

    public ExchangeRateUnavailableException() {
        super(MESSAGE);
    }

    public ExchangeRateUnavailableException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
