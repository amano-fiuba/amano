package com.amano.backend.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.amano.backend.config.JwtService;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;

/**
 * 022 T032/T033 (US5, plan.md D6): two administrators decide the same reported
 * payment at the same instant. The pessimistic lock on the debt (and the
 * payment) serializes both transactions, so only the first decision is applied
 * and the loser answers 409.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class PaymentDecisionConcurrencyIT {

    private static final int ROUNDS = 3;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private PaymentScenario s;

    @BeforeEach
    void buildGroup() throws Exception {
        s = new PaymentScenario(mockMvc, jdbc, jwtService, objectMapper);
        s.makeAdmin(s.carla);
    }

    private List<Integer> together(Callable<Integer> first, Callable<Integer> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> call : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }
            return statuses;
        }
        finally {
            pool.shutdownNow();
        }
    }

    private Callable<Integer> confirm(Cookie session, long paymentId) {
        return () -> s.confirmPayment(session, paymentId).andReturn().getResponse().getStatus();
    }

    private Callable<Integer> reject(Cookie session, long paymentId) {
        return () -> s.rejectPayment(session, paymentId).andReturn().getResponse().getStatus();
    }

    @Test
    void twoAdminsConfirmingAtOnceApplyOnlyOneDecision() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            if (round > 0) {
                s = new PaymentScenario(mockMvc, jdbc, jwtService, objectMapper);
                s.makeAdmin(s.carla);
            }
            s.requireValidation();
            long paymentId = s.registerReported(s.juanSession, s.juanDebt, "3333.33");

            List<Integer> statuses = together(confirm(s.anaSession, paymentId),
                    confirm(s.carlaSession, paymentId));

            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
            assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class,
                    paymentId)).isEqualTo("CONFIRMED");
            assertThat(jdbc.queryForObject("SELECT decided_by_user_id FROM payments WHERE id = ?", Long.class,
                    paymentId)).isIn(s.ana, s.carla);
            assertThat(jdbc.queryForObject("SELECT decided_at IS NOT NULL FROM payments WHERE id = ?",
                    Boolean.class, paymentId)).isTrue();
            assertThat(s.statusOf(s.juanDebt)).isEqualTo("VERIFIED");
        }
    }

    @Test
    void twoAdminsRejectingAtOnceApplyOnlyOneDecision() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            if (round > 0) {
                s = new PaymentScenario(mockMvc, jdbc, jwtService, objectMapper);
                s.makeAdmin(s.carla);
            }
            s.requireValidation();
            long paymentId = s.registerReported(s.juanSession, s.juanDebt, "3333.33");

            List<Integer> statuses = together(reject(s.anaSession, paymentId),
                    reject(s.carlaSession, paymentId));

            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
            assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class,
                    paymentId)).isEqualTo("REJECTED");
            assertThat(s.statusOf(s.juanDebt)).isEqualTo("PENDING");
        }
    }

    @Test
    void aConfirmationAndARejectionAtOnceLeaveOneConsistentDecision() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            if (round > 0) {
                s = new PaymentScenario(mockMvc, jdbc, jwtService, objectMapper);
                s.makeAdmin(s.carla);
            }
            s.requireValidation();
            long paymentId = s.registerReported(s.juanSession, s.juanDebt, "3333.33");

            List<Integer> statuses = together(confirm(s.anaSession, paymentId),
                    reject(s.carlaSession, paymentId));

            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
            String paymentStatus = jdbc.queryForObject("SELECT status FROM payments WHERE id = ?",
                    String.class, paymentId);
            if ("CONFIRMED".equals(paymentStatus)) {
                assertThat(s.statusOf(s.juanDebt)).isEqualTo("VERIFIED");
            }
            else {
                assertThat(paymentStatus).isEqualTo("REJECTED");
                assertThat(s.statusOf(s.juanDebt)).isEqualTo("PENDING");
            }
            assertThat(jdbc.queryForObject("SELECT decided_by_user_id FROM payments WHERE id = ?", Long.class,
                    paymentId)).isIn(s.ana, s.carla);
        }
    }
}
