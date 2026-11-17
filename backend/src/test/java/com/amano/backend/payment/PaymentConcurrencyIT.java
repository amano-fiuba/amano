package com.amano.backend.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.amano.backend.config.JwtService;
import com.amano.backend.support.AbstractIntegrationIT;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;

/**
 * 018 T026/T033 (FR-011, plan.md D5): concurrent payments of the same debt are
 * serialized by the pessimistic lock, so the debt is never paid beyond its
 * amount, and two requests with the same Idempotency-Key create one payment.
 */
class PaymentConcurrencyIT extends AbstractIntegrationIT {

    private static final int ROUNDS = 3;

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
    }

    /** Runs the two calls at the same instant and returns their HTTP statuses. */
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

    private Callable<Integer> payment(Cookie session, String key, String amount) {
        return () -> s.pay(session, key, PaymentScenario.body(s.juanDebt, amount, PaymentScenario.EXPENSE_DATE))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void twoPaymentsThatTogetherExceedTheBalanceNeverOverpayTheDebt() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            if (round > 0) {
                s = new PaymentScenario(mockMvc, jdbc, jwtService, objectMapper);
            }
            List<Integer> statuses = together(
                    payment(s.juanSession, UUID.randomUUID().toString(), "2000.00"),
                    payment(s.anaSession, UUID.randomUUID().toString(), "2000.00"));

            assertThat(statuses).containsOnlyOnce(201);
            assertThat(statuses).anyMatch(status -> status == 400);
            BigDecimal paid = jdbc.queryForObject("SELECT paid_amount FROM debts WHERE id = ?", BigDecimal.class,
                    s.juanDebt);
            assertThat(paid).isEqualByComparingTo("2000.00");
            assertThat(s.payments(s.juanDebt)).isEqualTo(1);
            assertThat(s.statusOf(s.juanDebt)).isEqualTo("PARTIALLY_PAID");
        }
    }

    @Test
    void twoFullPaymentsAtOnceLeaveOnePaymentAndA409() throws Exception {
        List<Integer> statuses = together(
                payment(s.juanSession, UUID.randomUUID().toString(), "3333.33"),
                payment(s.anaSession, UUID.randomUUID().toString(), "3333.33"));

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(s.payments(s.juanDebt)).isEqualTo(1);
        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PAID");
    }

    @Test
    void theSameKeyTwiceAtOnceCreatesOnePaymentAndOneEvent() throws Exception {
        String key = UUID.randomUUID().toString();
        List<Integer> statuses = together(
                payment(s.juanSession, key, "1000.00"),
                payment(s.juanSession, key, "1000.00"));

        assertThat(statuses).containsExactlyInAnyOrder(201, 200);
        assertThat(s.payments(s.juanDebt)).isEqualTo(1);
        assertThat(s.paymentEvents()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT paid_amount FROM debts WHERE id = ?", BigDecimal.class, s.juanDebt))
                .isEqualByComparingTo("1000.00");
    }
}
