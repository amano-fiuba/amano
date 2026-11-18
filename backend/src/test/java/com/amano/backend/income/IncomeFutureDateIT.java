package com.amano.backend.income;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.amano.backend.config.JwtService;
import com.amano.backend.support.AbstractIntegrationIT;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;

/**
 * Future-dated incomes (specs/033-income-management, US4): up to one year ahead
 * is accepted and flagged as projected; one day further is rejected.
 */
class IncomeFutureDateIT extends AbstractIntegrationIT {

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private long newUser(String name) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (first_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES (?, ?, 'x', now(), now()) RETURNING id
                """, Long.class, name, name + "-" + System.nanoTime() + "@amano.dev");
    }

    private ResultActions register(long userId, LocalDate date) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("amount", "1000.00");
        body.put("currency", "ARS");
        body.put("date", date.toString());
        body.put("category", "SALARY");
        body.put("paymentMethod", "TRANSFER");
        body.put("deviceTimeZone", "UTC");
        return mockMvc.perform(post("/api/incomes")
                .cookie(new Cookie("amano_auth", jwtService.issueToken(userId)))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private int incomesOf(long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM incomes WHERE user_id = ?", Integer.class, userId);
    }

    @Test
    void todayPlusOneYearIsAcceptedAndProjected() throws Exception {
        long userId = newUser("limite");

        register(userId, TODAY.plusYears(1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projected").value(true));
        assertThat(incomesOf(userId)).isEqualTo(1);
    }

    @Test
    void oneDayBeyondTheLimitIsRejectedOnTheDateField() throws Exception {
        long userId = newUser("pasado-limite");

        register(userId, TODAY.plusYears(1).plusDays(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.date").exists());
        assertThat(incomesOf(userId)).isZero();
    }

    @Test
    void projectedIsTrueOnlyForFutureDates() throws Exception {
        long userId = newUser("proyectado-flag");

        register(userId, TODAY).andExpect(status().isCreated())
                .andExpect(jsonPath("$.projected").value(false));
        register(userId, TODAY.plusDays(1)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.projected").value(true));
        register(userId, TODAY.minusDays(5)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.projected").value(false));
    }
}
