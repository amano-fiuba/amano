package com.amano.backend.groupexpense.exchange;

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
import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.amano.backend.user.Currency;

/**
 * 022 D8 / US9 (T050, T051, T053, T054): unit tests for
 * {@link BcraExchangeRateClient} against {@link MockRestServiceServer} (never
 * calling the real BCRA API).
 */
class BcraExchangeRateClientTest {

    private static final String BASE_URL = "https://api.bcra.gob.ar";

    private MockRestServiceServer server;
    private BcraExchangeRateClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new BcraExchangeRateClient(builder.build(), 2);
    }

    private static String urlFor(LocalDate date) {
        return BASE_URL + "/estadisticascambiarias/v1.0/Cotizaciones?fecha=" + date;
    }

    private static String bcraJson(LocalDate date, String usdRate, String eurRate) {
        return """
                {
                  "status": 200,
                  "extraTopField": "ignored",
                  "results": {
                    "fecha": "%s",
                    "detalle": [
                      {
                        "codigoMoneda": "ARS",
                        "descripcion": "PESO",
                        "tipoPase": 0,
                        "tipoCotizacion": 0,
                        "extraField": 123
                      },
                      {
                        "codigoMoneda": "USD",
                        "descripcion": "DOLAR ESTADOUNIDENSE",
                        "tipoPase": 1,
                        "tipoCotizacion": %s
                      },
                      {
                        "codigoMoneda": "EUR",
                        "descripcion": "EURO",
                        "tipoPase": 1.08,
                        "tipoCotizacion": %s
                      }
                    ]
                  }
                }
                """.formatted(date, usdRate, eurRate);
    }

    private static String emptyBcraJson(LocalDate date) {
        return """
                {
                  "status": 200,
                  "results": {
                    "fecha": "%s",
                    "detalle": []
                  }
                }
                """.formatted(date);
    }

    /** A 200 whose {@code results} has no usable date (T050 lookback by null date). */
    private static String undatedBcraJson() {
        return """
                {
                  "status": 200,
                  "results": {
                    "fecha": null,
                    "detalle": [
                      {
                        "codigoMoneda": "USD",
                        "descripcion": "DOLAR ESTADOUNIDENSE",
                        "tipoPase": 1,
                        "tipoCotizacion": 999.99
                      }
                    ]
                  }
                }
                """;
    }

    @Test
    void sameCurrencyReturnsOneWithoutCallingTheApi() {
        LocalDate date = LocalDate.of(2026, 1, 15);

        ExchangeRateQuote quote = client.getQuote(Currency.ARS, Currency.ARS, date);

        assertThat(quote.rate()).isEqualByComparingTo("1");
        assertThat(quote.rateDate()).isEqualTo(date);
        assertThat(quote.rateSource()).isEqualTo(BcraExchangeRateClient.SOURCE_SAME_CURRENCY);
        server.verify();
    }

    @Test
    void usdAndEurAgainstArsAndCrossedPairsUseBigDecimalWithScaleEight() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        String body = bcraJson(date, "1000.50", "1100.55");

        server.expect(requestTo(urlFor(date)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        ExchangeRateQuote usdToArs = client.getQuote(Currency.USD, Currency.ARS, date);
        assertThat(usdToArs.rate()).isEqualByComparingTo("1000.50000000");
        assertThat(usdToArs.rate().scale()).isEqualTo(8);
        assertThat(usdToArs.rateDate()).isEqualTo(date);
        assertThat(usdToArs.rateSource()).isEqualTo("BCRA");
        server.verify();

        server.reset();
        server.expect(requestTo(urlFor(date)))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        ExchangeRateQuote eurToArs = client.getQuote(Currency.EUR, Currency.ARS, date);
        assertThat(eurToArs.rate()).isEqualByComparingTo("1100.55000000");
        server.verify();

        server.reset();
        server.expect(requestTo(urlFor(date)))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        ExchangeRateQuote arsToUsd = client.getQuote(Currency.ARS, Currency.USD, date);
        // 1 / 1000.50 rounded HALF_UP to 8 decimals = 0.00099950
        assertThat(arsToUsd.rate()).isEqualByComparingTo("0.00099950");
        server.verify();

        server.reset();
        server.expect(requestTo(urlFor(date)))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        ExchangeRateQuote usdToEur = client.getQuote(Currency.USD, Currency.EUR, date);
        // 1000.50 / 1100.55 = 0.909090909... -> 0.90909091
        assertThat(usdToEur.rate()).isEqualByComparingTo("0.90909091");
        server.verify();

        server.reset();
        server.expect(requestTo(urlFor(date)))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        ExchangeRateQuote eurToUsd = client.getQuote(Currency.EUR, Currency.USD, date);
        // 1100.55 / 1000.50 = 1.10000000
        assertThat(eurToUsd.rate()).isEqualByComparingTo("1.10000000");
        server.verify();
    }

    /** 027 T013: currencies beyond USD/EUR, including MXN published by the BCRA as MXP. */
    @Test
    void newCatalogCurrenciesUseTheirBcraCode() {
        LocalDate date = LocalDate.of(2026, 11, 5);
        String body = """
                {
                  "status": 200,
                  "results": {
                    "fecha": "%s",
                    "detalle": [
                      { "codigoMoneda": "BRL", "descripcion": "REAL", "tipoPase": 0.18, "tipoCotizacion": 281.63495900 },
                      { "codigoMoneda": "MXP", "descripcion": "PESO MEXICANO", "tipoPase": 0.05, "tipoCotizacion": 82.45085200 },
                      { "codigoMoneda": "USD", "descripcion": "DOLAR E.E.U.U.", "tipoPase": 1, "tipoCotizacion": 1545.5 }
                    ]
                  }
                }
                """.formatted(date);

        server.expect(requestTo(urlFor(date))).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        ExchangeRateQuote brlToArs = client.getQuote(Currency.BRL, Currency.ARS, date);
        assertThat(brlToArs.rate()).isEqualByComparingTo("281.63495900");
        server.verify();

        server.reset();
        server.expect(requestTo(urlFor(date))).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        ExchangeRateQuote mxnToUsd = client.getQuote(Currency.MXN, Currency.USD, date);
        // 82.450852 / 1545.5 = 0.0533489... -> 0.05334898
        assertThat(mxnToUsd.rate()).isEqualByComparingTo("0.05334898");
        assertThat(mxnToUsd.rate().scale()).isEqualTo(8);
        server.verify();
    }

    @Test
    void weekendEmptyListWalksBackToTheLastBusinessDayAndRecordsItsDate() {
        LocalDate sunday = LocalDate.of(2026, 1, 18);
        LocalDate saturday = LocalDate.of(2026, 1, 17);
        LocalDate friday = LocalDate.of(2026, 1, 16);

        server.expect(requestTo(urlFor(sunday)))
                .andRespond(withSuccess(emptyBcraJson(sunday), MediaType.APPLICATION_JSON));
        server.expect(requestTo(urlFor(saturday)))
                .andRespond(withSuccess(emptyBcraJson(saturday), MediaType.APPLICATION_JSON));
        server.expect(requestTo(urlFor(friday)))
                .andRespond(withSuccess(bcraJson(friday, "18.50", "20.00"), MediaType.APPLICATION_JSON));

        ExchangeRateQuote quote = client.getQuote(Currency.USD, Currency.ARS, sunday);

        assertThat(quote.rate()).isEqualByComparingTo("18.50000000");
        assertThat(quote.rateDate()).isEqualTo(friday);
        assertThat(quote.rateSource()).isEqualTo("BCRA");
        server.verify();
    }

    @Test
    void responseWithoutFechaWalksBackToTheLastDatedQuote() {
        LocalDate sunday = LocalDate.of(2026, 1, 18);
        LocalDate saturday = LocalDate.of(2026, 1, 17);
        LocalDate friday = LocalDate.of(2026, 1, 16);

        server.expect(requestTo(urlFor(sunday)))
                .andRespond(withSuccess(undatedBcraJson(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(urlFor(saturday)))
                .andRespond(withSuccess(emptyBcraJson(saturday), MediaType.APPLICATION_JSON));
        server.expect(requestTo(urlFor(friday)))
                .andRespond(withSuccess(bcraJson(friday, "18.50", "20.00"), MediaType.APPLICATION_JSON));

        ExchangeRateQuote quote = client.getQuote(Currency.USD, Currency.ARS, sunday);

        assertThat(quote.rate()).isEqualByComparingTo("18.50000000");
        assertThat(quote.rateDate()).isEqualTo(friday);
        server.verify();
    }

    @Test
    void retriesTransientErrorOnTheSameDateBeforeSucceeding() {
        LocalDate date = LocalDate.of(2026, 1, 15);

        server.expect(requestTo(urlFor(date))).andRespond(withServerError());
        server.expect(requestTo(urlFor(date)))
                .andRespond(withSuccess(bcraJson(date, "950.00", "1050.00"), MediaType.APPLICATION_JSON));

        ExchangeRateQuote quote = client.getQuote(Currency.USD, Currency.ARS, date);

        assertThat(quote.rate()).isEqualByComparingTo("950.00000000");
        assertThat(quote.rateDate()).isEqualTo(date);
        server.verify();
    }

    @Test
    void noQuoteWithinSevenDaysThrowsExchangeRateUnavailable() {
        LocalDate start = LocalDate.of(2026, 1, 18);
        for (int offset = 0; offset <= 7; offset++) {
            LocalDate day = start.minusDays(offset);
            server.expect(requestTo(urlFor(day)))
                    .andRespond(withSuccess(emptyBcraJson(day), MediaType.APPLICATION_JSON));
        }

        assertThatThrownBy(() -> client.getQuote(Currency.USD, Currency.ARS, start))
                .isInstanceOf(ExchangeRateUnavailableException.class)
                .hasMessage("No se pudo obtener la cotización; reintente después o registre en moneda del grupo");
        server.verify();
    }

    @Test
    void serverErrorOnRequestedDayFailsFastWithoutQueryingOlderDates() {
        LocalDate requested = LocalDate.of(2026, 1, 18);
        // maxAttempts = 2. No expectation for older dates: a lookback would fail.
        server.expect(times(2), requestTo(urlFor(requested))).andRespond(withServerError());

        assertThatThrownBy(() -> client.getQuote(Currency.USD, Currency.ARS, requested))
                .isInstanceOf(ExchangeRateUnavailableException.class)
                .hasMessage(ExchangeRateUnavailableException.MESSAGE);
        server.verify();
    }

    @Test
    void transportErrorOnRequestedDayFailsFastAfterConfiguredAttempts() {
        LocalDate requested = LocalDate.of(2026, 1, 18);
        // Only the configured 2 attempts for this one query, never 8 lookback days.
        server.expect(times(2), requestTo(urlFor(requested)))
                .andRespond(withException(new IOException("read timed out")));

        assertThatThrownBy(() -> client.getQuote(Currency.USD, Currency.ARS, requested))
                .isInstanceOf(ExchangeRateUnavailableException.class)
                .hasMessage(ExchangeRateUnavailableException.MESSAGE);
        server.verify();
    }

    @Test
    void clientErrorIsTerminalAndDoesNotLookBack() {
        LocalDate requested = LocalDate.of(2026, 1, 18);
        // 4xx cannot be fixed by retrying or by an older date.
        server.expect(once(), requestTo(urlFor(requested)))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.getQuote(Currency.USD, Currency.ARS, requested))
                .isInstanceOf(ExchangeRateUnavailableException.class)
                .hasMessage(ExchangeRateUnavailableException.MESSAGE);
        server.verify();
    }

    @Test
    void rejectsNullArgumentsAndHandlesMissingCurrencyInResponse() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        assertThatThrownBy(() -> client.getQuote(null, Currency.ARS, date))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.getQuote(Currency.USD, null, date))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.getQuote(Currency.USD, Currency.ARS, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
