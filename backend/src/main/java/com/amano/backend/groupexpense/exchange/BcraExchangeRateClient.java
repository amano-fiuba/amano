package com.amano.backend.groupexpense.exchange;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.amano.backend.user.Currency;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Infrastructure adapter for the public BCRA Estadísticas Cambiarias v1.0 API
 * (022 D8, T050/T053). Calls
 * {@code GET {baseUrl}/estadisticascambiarias/v1.0/Cotizaciones?fecha=YYYY-MM-DD}
 * using Spring's {@link RestClient} with retries and no external dependencies.
 *
 * <p>All rates are parsed and crossed using {@link BigDecimal} with scale 8
 * ({@link RoundingMode#HALF_UP}), never floating point.
 *
 * <p>Only a successful response with an empty {@code detalle} list (or without a
 * {@code fecha}, as happens on weekends and holidays) makes the client walk back
 * day by day up to {@link #MAX_LOOKBACK_DAYS} days to reuse the last official
 * quote. Transport failures, timeouts and HTTP errors (4xx/5xx) are terminal:
 * after the configured retries for the requested date the client throws
 * {@link ExchangeRateUnavailableException} (503) immediately, without querying
 * older dates. If no valid quote is found in {@code [date - 7, date]}, it also
 * throws {@link ExchangeRateUnavailableException} (503).
 */
public class BcraExchangeRateClient implements ExchangeRateProvider {

    public static final String SOURCE_BCRA = "BCRA";
    public static final String SOURCE_SAME_CURRENCY = "SAME_CURRENCY";
    public static final int RATE_SCALE = 8;
    public static final int MAX_LOOKBACK_DAYS = 7;

    private final RestClient restClient;
    private final int maxAttempts;

    public BcraExchangeRateClient(RestClient restClient, int maxAttempts) {
        this.restClient = restClient;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Override
    public ExchangeRateQuote getQuote(Currency from, Currency to, LocalDate date) {
        if (from == null || to == null || date == null) {
            throw new IllegalArgumentException("Monedas y fecha son obligatorias");
        }
        if (from == to) {
            return new ExchangeRateQuote(BigDecimal.ONE, date, SOURCE_SAME_CURRENCY);
        }
        for (int offset = 0; offset <= MAX_LOOKBACK_DAYS; offset++) {
            LocalDate candidate = date.minusDays(offset);
            BigDecimal rate = fetchRateForDate(from, to, candidate);
            if (rate != null) {
                return new ExchangeRateQuote(rate, candidate, SOURCE_BCRA);
            }
        }
        throw new ExchangeRateUnavailableException();
    }

    private BigDecimal fetchRateForDate(Currency from, Currency to, LocalDate date) {
        BcraResponse response = getWithRetry(date);
        // A 200 without a date or with an empty detail means "no quote that day"
        // (weekend/holiday): only this case triggers the day-by-day lookback.
        if (response == null || response.results() == null || response.results().fecha() == null
                || response.results().detalle() == null || response.results().detalle().isEmpty()) {
            return null;
        }
        Map<String, BigDecimal> arsPerUnit = new HashMap<>();
        for (BcraDetalle item : response.results().detalle()) {
            if (item == null || item.codigoMoneda() == null || item.tipoCotizacion() == null) {
                continue;
            }
            if (item.tipoCotizacion().signum() > 0) {
                arsPerUnit.put(item.codigoMoneda().trim().toUpperCase(Locale.ROOT), item.tipoCotizacion());
            }
        }
        return computeRate(from, to, arsPerUnit);
    }

    private BcraResponse getWithRetry(LocalDate date) {
        RestClientException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/estadisticascambiarias/v1.0/Cotizaciones")
                                .queryParam("fecha", date.toString())
                                .build())
                        .retrieve()
                        .body(BcraResponse.class);
            }
            catch (RestClientResponseException exception) {
                // A 4xx is a contract/client error: retrying cannot help, so fail
                // fast with 503 instead of walking back or hammering the API.
                if (exception.getStatusCode().is4xxClientError()) {
                    throw new ExchangeRateUnavailableException(exception);
                }
                lastFailure = exception;
            }
            catch (RestClientException exception) {
                lastFailure = exception;
            }
        }
        // Transport errors and 5xx are terminal: never fall back to older dates.
        throw new ExchangeRateUnavailableException(lastFailure);
    }

    static BigDecimal computeRate(Currency from, Currency to, Map<String, BigDecimal> arsPerUnit) {
        if (from == Currency.ARS) {
            BigDecimal toArs = arsPerUnit.get(to.name());
            if (toArs == null || toArs.signum() <= 0) {
                return null;
            }
            return BigDecimal.ONE.divide(toArs, RATE_SCALE, RoundingMode.HALF_UP);
        }
        if (to == Currency.ARS) {
            BigDecimal fromArs = arsPerUnit.get(from.name());
            if (fromArs == null || fromArs.signum() <= 0) {
                return null;
            }
            return fromArs.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal fromArs = arsPerUnit.get(from.name());
        BigDecimal toArs = arsPerUnit.get(to.name());
        if (fromArs == null || fromArs.signum() <= 0 || toArs == null || toArs.signum() <= 0) {
            return null;
        }
        return fromArs.divide(toArs, RATE_SCALE, RoundingMode.HALF_UP);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BcraResponse(BcraResults results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BcraResults(String fecha, List<BcraDetalle> detalle) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BcraDetalle(
            String codigoMoneda,
            String descripcion,
            BigDecimal tipoPase,
            BigDecimal tipoCotizacion) {
    }
}
