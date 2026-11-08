package com.amano.backend.currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 027 T010 (research.md R1): the BCRA Maestros/Divisas catalog is filtered to the
 * supported currencies. The fixture is the real response of 2026-11-08; the real
 * API is never called.
 */
class BcraCurrencyCatalogClientTest {

    private static final String URL = "https://api.bcra.gob.ar/estadisticascambiarias/v1.0/Maestros/Divisas";

    private MockRestServiceServer server;
    private BcraCurrencyCatalogClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.bcra.gob.ar");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new BcraCurrencyCatalogClient(builder.build(), 2);
    }

    private static String fixture() throws IOException {
        try (InputStream in = BcraCurrencyCatalogClientTest.class.getResourceAsStream("/bcra/maestros-divisas.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void keepsTheSupportedCurrenciesOrderedAndMapsMxpToMxn() throws IOException {
        server.expect(once(), requestTo(URL)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(fixture(), MediaType.APPLICATION_JSON));

        List<CurrencyCatalogEntry> catalog = client.getCatalog();

        assertThat(catalog).extracting(CurrencyCatalogEntry::code).containsExactly(
                "ARS", "AUD", "AWG", "BOB", "BRL", "CAD", "CHF", "CLP", "CNY", "COP", "CZK", "DKK", "EUR",
                "GBP", "HKD", "ILS", "INR", "JPY", "MXN", "NIO", "NOK", "NZD", "PEN", "PYG", "RSD", "RUB",
                "SEK", "SGD", "TRY", "USD", "UYU", "ZAR");
        assertThat(catalog).extracting(CurrencyCatalogEntry::code)
                .doesNotContain("ATS", "DEM", "ESP", "ITL", "VEB", "REF", "XAG", "XAU", "XDR", "CNH", "VND", "MXP");
        assertThat(catalog).filteredOn(entry -> entry.code().equals("MXN"))
                .singleElement().extracting(CurrencyCatalogEntry::bcraName).isEqualTo("PESO MEXICANO");
        // NIO is listed twice by the BCRA: the first denomination wins.
        assertThat(catalog).filteredOn(entry -> entry.code().equals("NIO"))
                .singleElement().extracting(CurrencyCatalogEntry::bcraName).isEqualTo("CORDOBA NICARAGUENSE");
        server.verify();
    }

    @Test
    void skipsNullEntriesAndTrimsNames() {
        String body = """
                { "status": 200, "results": [ null, { "codigo": "brl", "denominacion": "  REAL  " },
                  { "codigo": null, "denominacion": "SIN CODIGO" } ] }
                """;
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThat(client.getCatalog()).containsExactly(new CurrencyCatalogEntry("BRL", "REAL"));
    }

    @Test
    void responseWithoutResultsIsUnavailable() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"status\":200}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(client::getCatalog).isInstanceOf(CurrencyCatalogUnavailableException.class)
                .hasMessage(CurrencyCatalogUnavailableException.MESSAGE);
    }

    @Test
    void retriesServerErrorsAndThenFails() {
        server.expect(times(2), requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(client::getCatalog).isInstanceOf(CurrencyCatalogUnavailableException.class);
        server.verify();
    }

    @Test
    void retriesTransportErrorsAndRecovers() throws IOException {
        server.expect(once(), requestTo(URL)).andRespond(withException(new IOException("timeout")));
        server.expect(once(), requestTo(URL)).andRespond(withSuccess(fixture(), MediaType.APPLICATION_JSON));

        assertThat(client.getCatalog()).hasSize(32);
        server.verify();
    }

    @Test
    void clientErrorFailsAtOnce() {
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(client::getCatalog).isInstanceOf(CurrencyCatalogUnavailableException.class);
        server.verify();
    }
}
