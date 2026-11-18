package com.amano.backend.payment;

import static org.assertj.core.api.Assertions.assertThat;

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
 * 022 T042 (US6, plan.md D6): two full payments with different idempotency keys
 * race for the same debt. The {@code PESSIMISTIC_WRITE} lock serializes them,
 * so exactly one payment is written and the other request gets 409.
 */
class DebtPaymentConcurrencyIT extends AbstractIntegrationIT {

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

    /** Runs both requests at the same instant and returns their HTTP statuses. */
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

    private Callable<Integer> fullPayment(Cookie session, String amount) {
        return () -> s.registerFull(session, s.juanDebt, amount, UUID.randomUUID().toString())
                .andReturn().getResponse().getStatus();
    }

    @Test
    void twoConcurrentFullPaymentsWithDifferentKeysApplyOnlyOne() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            if (round > 0) {
                s = new PaymentScenario(mockMvc, jdbc, jwtService, objectMapper);
            }
            List<Integer> statuses = together(
                    fullPayment(s.juanSession, "3333.33"),
                    fullPayment(s.anaSession, "3333.33"));

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(s.payments(s.juanDebt)).isEqualTo(1);
            assertThat(s.activePayments(s.juanDebt)).isEqualTo(1);
            assertThat(s.paymentEvents()).isEqualTo(1);
            assertThat(s.statusOf(s.juanDebt)).isEqualTo("PAID");
        }
    }

    @Test
    void twoConcurrentFullPaymentsWithValidationLeaveOneReportedPayment() throws Exception {
        s.requireValidation();

        List<Integer> statuses = together(
                fullPayment(s.juanSession, "3333.33"),
                fullPayment(s.anaSession, "3333.33"));

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(s.payments(s.juanDebt)).isEqualTo(1);
        assertThat(s.paymentEvents()).isEqualTo(1);
        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PAYMENT_REPORTED");
    }

    @Test
    void theSameKeyTwiceAtOnceCreatesOnePayment() throws Exception {
        String key = UUID.randomUUID().toString();
        Callable<Integer> payment = () -> s.registerFull(s.juanSession, s.juanDebt, "3333.33", key)
                .andReturn().getResponse().getStatus();

        List<Integer> statuses = together(payment, payment);

        assertThat(statuses).containsExactlyInAnyOrder(201, 200);
        assertThat(s.payments(s.juanDebt)).isEqualTo(1);
        assertThat(s.paymentEvents()).isEqualTo(1);
        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PAID");
    }

    /** Partial payments (022 T042): payments are applied one after the other and never pass the balance. */
    @Test
    void twoConcurrentPartialPaymentsThatDoNotBothFitApplyOnlyOneAndNeverExceedTheBalance() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            if (round > 0) {
                s = new PaymentScenario(mockMvc, jdbc, jwtService, objectMapper);
            }
            List<Integer> statuses = together(
                    fullPayment(s.juanSession, "2000.00"),
                    fullPayment(s.anaSession, "2000.00"));

            // 2.000 + 2.000 > 3.333,33: the one that arrives second finds only 1.333,33 left.
            assertThat(statuses).containsExactlyInAnyOrder(201, 400);
            assertThat(s.payments(s.juanDebt)).isEqualTo(1);
            assertThat(s.paidAmount(s.juanDebt)).isEqualByComparingTo("2000.00");
            assertThat(s.paidAmount(s.juanDebt)).isLessThanOrEqualTo(new java.math.BigDecimal("3333.33"));
            assertThat(s.statusOf(s.juanDebt)).isEqualTo("PARTIALLY_PAID");
        }
    }

    @Test
    void twoConcurrentPartialPaymentsThatBothFitAreAppliedInSeries() throws Exception {
        List<Integer> statuses = together(
                fullPayment(s.juanSession, "1000.00"),
                fullPayment(s.anaSession, "1000.00"));

        assertThat(statuses).containsExactly(201, 201);
        assertThat(s.payments(s.juanDebt)).isEqualTo(2);
        assertThat(s.paidAmount(s.juanDebt)).isEqualByComparingTo("2000.00");
        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PARTIALLY_PAID");
    }

    @Test
    void twoConcurrentPartialPaymentsWithValidationLeaveOneReportedAndNothingPaid() throws Exception {
        s.requireValidation();

        List<Integer> statuses = together(
                fullPayment(s.juanSession, "1000.00"),
                fullPayment(s.anaSession, "1000.00"));

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(s.payments(s.juanDebt)).isEqualTo(1);
        assertThat(s.paidAmount(s.juanDebt)).isEqualByComparingTo("0.00");
        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PAYMENT_REPORTED");
    }
}
