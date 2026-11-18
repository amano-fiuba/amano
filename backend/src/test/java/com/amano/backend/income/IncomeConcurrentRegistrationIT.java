package com.amano.backend.income;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.amano.backend.config.JwtService;
import com.amano.backend.support.AbstractIntegrationIT;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;

/**
 * Simultaneous registrations with the same Idempotency-Key
 * (specs/033-income-management, US3): a single row is persisted.
 */
class IncomeConcurrentRegistrationIT extends AbstractIntegrationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private long newUser() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (first_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES ('concurrente', ?, 'x', now(), now()) RETURNING id
                """, Long.class, "concurrente-" + System.nanoTime() + "@amano.dev");
    }

    private static Map<String, String> payload(String amount) {
        Map<String, String> body = new HashMap<>();
        body.put("amount", amount);
        body.put("currency", "ARS");
        body.put("date", LocalDate.now(ZoneOffset.UTC).toString());
        body.put("category", "SALARY");
        body.put("paymentMethod", "TRANSFER");
        body.put("deviceTimeZone", "UTC");
        return body;
    }

    private List<Integer> fire(long userId, UUID key, List<String> amounts) throws Exception {
        Cookie session = new Cookie("amano_auth", jwtService.issueToken(userId));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(amounts.size());
        try {
            List<Future<Integer>> futures = amounts.stream().map(amount -> pool.submit((Callable<Integer>) () -> {
                String json = objectMapper.writeValueAsString(payload(amount));
                start.await();
                return mockMvc.perform(post("/api/incomes").cookie(session)
                        .header("Idempotency-Key", key.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                        .andReturn().getResponse().getStatus();
            })).toList();
            start.countDown();
            List<Integer> statuses = new java.util.ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }
            return statuses;
        }
        finally {
            pool.shutdownNow();
        }
    }

    private int rows(long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM incomes WHERE user_id = ?", Integer.class, userId);
    }

    @Test
    void simultaneousRequestsWithTheSameKeyAndBodyCreateOneRow() throws Exception {
        long userId = newUser();

        List<Integer> statuses = fire(userId, UUID.randomUUID(), List.of("100.00", "100.00", "100.00", "100.00"));

        assertThat(rows(userId)).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 201)).hasSize(1);
        assertThat(statuses.stream().filter(s -> s == 200)).hasSize(3);
    }

    @Test
    void simultaneousRequestsWithTheSameKeyAndDifferentBodiesCreateOneRowAndReject422() throws Exception {
        long userId = newUser();

        List<Integer> statuses = fire(userId, UUID.randomUUID(), List.of("100.00", "200.00"));

        assertThat(rows(userId)).isEqualTo(1);
        assertThat(statuses).containsExactlyInAnyOrder(201, 422);
    }
}
