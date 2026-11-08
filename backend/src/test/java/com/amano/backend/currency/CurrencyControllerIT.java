package com.amano.backend.currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.amano.backend.config.JwtService;

import jakarta.servlet.http.Cookie;

/** 027 T012 (contracts/currencies-api.md): GET /api/currencies with the provider replaced by a double. */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class CurrencyControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @MockitoBean(name = "currencyCatalogProvider")
    private CurrencyCatalogProvider catalogProvider;

    private Cookie verifiedSession(String name) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO users (first_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES (?, ?, 'x', now(), now()) RETURNING id
                """, Long.class, name, name + "-" + System.nanoTime() + "@amano.dev");
        return new Cookie("amano_auth", jwtService.issueToken(id));
    }

    @Test
    void withoutSessionIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/currencies")).andExpect(status().isUnauthorized());
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
}
