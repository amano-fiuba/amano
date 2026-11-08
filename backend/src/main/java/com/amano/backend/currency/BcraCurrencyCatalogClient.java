package com.amano.backend.currency;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.amano.backend.user.Currency;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Reads {@code GET {baseUrl}/estadisticascambiarias/v1.0/Maestros/Divisas} from
 * the public BCRA API (027, research.md R1) and keeps the currencies that
 * {@link Currency} supports: extinct codes, metals, REF, XDR, CNH and VND are
 * dropped by {@link Currency#fromBcraCode}, duplicates (NIO is listed twice) are
 * collapsed and MXP becomes MXN. Retries transport errors and 5xx like
 * {@code BcraExchangeRateClient}; a 4xx fails at once.
 */
public class BcraCurrencyCatalogClient implements CurrencyCatalogProvider {

    static final String PATH = "/estadisticascambiarias/v1.0/Maestros/Divisas";

    private final RestClient restClient;
    private final int maxAttempts;

    public BcraCurrencyCatalogClient(RestClient restClient, int maxAttempts) {
        this.restClient = restClient;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Override
    public List<CurrencyCatalogEntry> getCatalog() {
        DivisasResponse response = fetch();
        if (response == null || response.results() == null) {
            throw new CurrencyCatalogUnavailableException();
        }
        Map<Currency, CurrencyCatalogEntry> byCurrency = new LinkedHashMap<>();
        for (Divisa divisa : response.results()) {
            if (divisa == null) {
                continue;
            }
            Currency.fromBcraCode(divisa.codigo()).ifPresent(currency -> byCurrency.putIfAbsent(currency,
                    new CurrencyCatalogEntry(currency.name(), trimmed(divisa.denominacion()))));
        }
        return byCurrency.values().stream()
                .sorted(Comparator.comparing(CurrencyCatalogEntry::code))
                .toList();
    }

    private DivisasResponse fetch() {
        RestClientException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return restClient.get().uri(PATH).retrieve().body(DivisasResponse.class);
            }
            catch (RestClientResponseException exception) {
                if (exception.getStatusCode().is4xxClientError()) {
                    throw new CurrencyCatalogUnavailableException(exception);
                }
                lastFailure = exception;
            }
            catch (RestClientException exception) {
                lastFailure = exception;
            }
        }
        throw new CurrencyCatalogUnavailableException(lastFailure);
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DivisasResponse(List<Divisa> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Divisa(String codigo, String denominacion) {
    }
}
