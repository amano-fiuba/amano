package com.amano.backend.currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.amano.backend.config.JwtService;
import com.amano.backend.groupexpense.exchange.CachingExchangeRateProvider;
import com.amano.backend.groupexpense.exchange.ExchangeRateProvider;
import com.amano.backend.groupexpense.exchange.ExchangeRateQuote;
import com.amano.backend.groupexpense.exchange.ExchangeRateUnavailableException;
import com.amano.backend.support.AbstractIntegrationIT;
import com.amano.backend.user.Currency;

import jakarta.servlet.http.Cookie;

/** 027 T012 (contracts/currencies-api.md): GET /api/currencies with the provider replaced by a double. */
class CurrencyControllerIT extends AbstractIntegrationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @MockitoBean(name = "currencyCatalogProvider")
    private CurrencyCatalogProvider catalogProvider;

    @Autowired
    private ExchangeRateProvider exchangeRateProvider;

    private Cookie verifiedSession(String name) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO users (first_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES (?, ?, 'x', now(), now()) RETURNING id
                """, Long.class, name, name + "-" + System.nanoTime() + "@amano.dev");
        return new Cookie("amano_auth", jwtService.issueToken(id));
    }

    @Test
    void withoutSessionReturnsTheCatalog() throws Exception {
        when(catalogProvider.getCatalog()).thenReturn(List.of(new CurrencyCatalogEntry("ARS", "PESO")));

        mockMvc.perform(get("/api/currencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencies[0].code").value("ARS"));
    }

    @Test
    void returnsTheCatalog() throws Exception {
        when(catalogProvider.getCatalog()).thenReturn(List.of(
                new CurrencyCatalogEntry("ARS", "PESO"), new CurrencyCatalogEntry("BRL", "REAL")));

        mockMvc.perform(get("/api/currencies").cookie(verifiedSession("ana")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencies.length()").value(2))
                .andExpect(jsonPath("$.currencies[1].code").value("BRL"))
                .andExpect(jsonPath("$.currencies[1].bcraName").value("REAL"));
    }

    @Test
    void unavailableCatalogIsA503WithItsCode() throws Exception {
        when(catalogProvider.getCatalog()).thenThrow(new CurrencyCatalogUnavailableException());

        String body = mockMvc.perform(get("/api/currencies").cookie(verifiedSession("juan")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(CurrencyCatalogUnavailableException.MESSAGE))
                .andExpect(jsonPath("$.properties.code").value("CURRENCY_CATALOG_UNAVAILABLE"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("reintentá");
    }

    // ---- FR-035: GET /api/currencies/quote ----

    @Test
    void quoteWithoutSessionIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/currencies/quote?currency=BRL&date=2026-11-05")).andExpect(status().isUnauthorized());
    }

    @Test
    void quoteConvertsToPesosWithTheBcraRate() throws Exception {
        if (exchangeRateProvider instanceof CachingExchangeRateProvider caching) {
            caching.clear();
        }
        LocalDate date = LocalDate.of(2026, 11, 5);
        when(bcraExchangeRateClient.getQuote(Currency.BRL, Currency.ARS, date))
                .thenReturn(new ExchangeRateQuote(new BigDecimal("281.63495900"), date, "BCRA"));

        mockMvc.perform(get("/api/currencies/quote?currency=BRL&date=2026-11-05").cookie(verifiedSession("lia")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(281.634959))
                .andExpect(jsonPath("$.rateDate").value("2026-11-05"))
                .andExpect(jsonPath("$.rateSource").value("BCRA"))
                .andExpect(jsonPath("$.groupCurrency").value("ARS"));
    }

    @Test
    void quoteInPesosDoesNotCallTheBcra() throws Exception {
        mockMvc.perform(get("/api/currencies/quote?currency=ARS&date=2026-11-05").cookie(verifiedSession("leo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateSource").value("SAME_CURRENCY"));
        verify(bcraExchangeRateClient, never()).getQuote(any(), any(), any());
    }

    @Test
    void quoteValidatesCurrencyAndDateAndAnswers503WithoutRate() throws Exception {
        Cookie session = verifiedSession("luz");
        mockMvc.perform(get("/api/currencies/quote?currency=XAU&date=2026-11-05").cookie(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.currency").value("La moneda no es válida"));
        mockMvc.perform(get("/api/currencies/quote?currency=BRL&date=" + LocalDate.now().plusDays(5)).cookie(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.date").exists());

        if (exchangeRateProvider instanceof CachingExchangeRateProvider caching) {
            caching.clear();
        }
        when(bcraExchangeRateClient.getQuote(any(), any(), any())).thenThrow(new ExchangeRateUnavailableException());
        mockMvc.perform(get("/api/currencies/quote?currency=USD&date=2026-11-04").cookie(session))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.properties.code").value("EXCHANGE_RATE_UNAVAILABLE"));
    }
}
