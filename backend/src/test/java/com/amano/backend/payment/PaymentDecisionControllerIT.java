package com.amano.backend.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Map;

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

import tools.jackson.databind.ObjectMapper;

/**
 * 022 T032/T033/T035 (US5): HTTP contract of the administrator decision over a
 * reported payment. Only an active ADMIN of the group can confirm or reject;
 * the decision is recorded in the payment and the debt moves
 * {@code PAYMENT_REPORTED → VERIFIED} or {@code → PENDING}. Decisions outside
 * the group are neutral 404s and non-decidable payments answer 409.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class PaymentDecisionControllerIT {

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
    }

    private long reportedPayment() throws Exception {
        s.requireValidation();
        return s.registerReported(s.juanSession, s.juanDebt, "3333.33");
    }

    @Test
    void anAdminConfirmationMarksThePaymentConfirmedAndTheDebtVerified() throws Exception {
        long paymentId = reportedPayment();

        s.confirmPayment(s.anaSession, paymentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.debtStatusAfter").value("VERIFIED"))
                .andExpect(jsonPath("$.confirmedByUserId").value(s.ana));

        Map<String, Object> payment = jdbc.queryForMap("SELECT * FROM payments WHERE id = ?", paymentId);
        assertThat(payment.get("status")).isEqualTo("CONFIRMED");
        assertThat(payment.get("legacy")).isEqualTo(false);
        assertThat(payment.get("decided_by_user_id")).isEqualTo(s.ana);
        assertThat(payment.get("decided_at")).isNotNull();
        assertThat(s.statusOf(s.juanDebt)).isEqualTo("VERIFIED");
        assertThat(jdbc.queryForObject("SELECT paid_amount FROM debts WHERE id = ?", BigDecimal.class,
                s.juanDebt)).isEqualByComparingTo("3333.33");
        assertThat(s.activePayments(s.juanDebt)).isEqualTo(1);
    }

    @Test
    void anAdminRejectionMarksThePaymentRejectedAndReturnsTheDebtToPending() throws Exception {
        long paymentId = reportedPayment();

        s.rejectPayment(s.anaSession, paymentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.debtStatusAfter").value("PENDING"))
                .andExpect(jsonPath("$.rejectedByUserId").value(s.ana));

        Map<String, Object> payment = jdbc.queryForMap("SELECT * FROM payments WHERE id = ?", paymentId);
        assertThat(payment.get("status")).isEqualTo("REJECTED");
        assertThat(payment.get("decided_by_user_id")).isEqualTo(s.ana);
        assertThat(payment.get("decided_at")).isNotNull();
        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT paid_amount FROM debts WHERE id = ?", BigDecimal.class,
                s.juanDebt)).isEqualByComparingTo("0.00");

        // REJECTED leaves the partial unique index: the debt accepts a new payment.
        long newPayment = s.registerReported(s.juanSession, s.juanDebt, "3333.33");
        assertThat(newPayment).isNotEqualTo(paymentId);
        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PAYMENT_REPORTED");
        assertThat(s.payments(s.juanDebt)).isEqualTo(2);
    }

    @Test
    void rejectingTheRemainingPaymentOfALegacyPartialDebtRestoresThePreviousAmount() throws Exception {
        s.requireValidation();
        String legacy = s.pay(s.juanSession, s.juanDebt, "1000.00")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long legacyPayment = objectMapper.readTree(legacy).get("id").asLong();
        long reported = s.registerReported(s.juanSession, s.juanDebt, "2333.33");

        s.rejectPayment(s.anaSession, reported)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.debtStatusAfter").value("PARTIALLY_PAID"));

        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PARTIALLY_PAID");
        assertThat(jdbc.queryForObject("SELECT paid_amount FROM debts WHERE id = ?", BigDecimal.class,
                s.juanDebt)).isEqualByComparingTo("1000.00");
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class,
                legacyPayment)).isEqualTo("CONFIRMED");
    }

    @Test
    void onlyAdminsOfTheGroupCanDecide() throws Exception {
        long paymentId = reportedPayment();

        s.confirmPayment(s.carlaSession, paymentId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Solo los administradores pueden confirmar pagos"));
        s.rejectPayment(s.carlaSession, paymentId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Solo los administradores pueden rechazar pagos"));
        s.confirmPayment(s.outsiderSession, paymentId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(PaymentNotFoundException.MESSAGE));

        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PAYMENT_REPORTED");
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class,
                paymentId)).isEqualTo("REPORTED");
    }

    @Test
    void aPaymentOfAnotherGroupOrAnUnknownPaymentIsANeutral404() throws Exception {
        long paymentId = reportedPayment();
        PaymentScenario other = new PaymentScenario(mockMvc, jdbc, jwtService, objectMapper);
        other.requireValidation();
        long otherPayment = other.registerReported(other.juanSession, other.juanDebt, "3333.33");

        s.confirmPayment(s.anaSession, otherPayment)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(PaymentNotFoundException.MESSAGE));
        mockMvc.perform(post("/api/groups/" + (s.groupId + 999) + "/payments/" + paymentId + "/confirm")
                .cookie(s.anaSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(PaymentNotFoundException.MESSAGE));
        s.confirmPayment(s.anaSession, paymentId + 999)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(PaymentNotFoundException.MESSAGE));

        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PAYMENT_REPORTED");
        assertThat(other.statusOf(other.juanDebt)).isEqualTo("PAYMENT_REPORTED");
    }

    @Test
    void anAlreadyDecidedPaymentCannotBeDecidedAgain() throws Exception {
        long paymentId = reportedPayment();
        s.confirmPayment(s.anaSession, paymentId).andExpect(status().isOk());

        s.confirmPayment(s.anaSession, paymentId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(PaymentNotDecidableException.MESSAGE))
                .andExpect(jsonPath("$.properties.code").value(PaymentNotDecidableException.CODE));
        s.rejectPayment(carlaAdminSession(), paymentId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.properties.code").value(PaymentNotDecidableException.CODE));

        assertThat(s.statusOf(s.juanDebt)).isEqualTo("VERIFIED");
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class,
                paymentId)).isEqualTo("CONFIRMED");
    }

    @Test
    void aRejectedPaymentCannotBeConfirmed() throws Exception {
        long paymentId = reportedPayment();
        s.rejectPayment(s.anaSession, paymentId).andExpect(status().isOk());

        s.confirmPayment(s.anaSession, paymentId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(PaymentNotDecidableException.MESSAGE));

        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class,
                paymentId)).isEqualTo("REJECTED");
    }

    @Test
    void legacyAndAlreadyConfirmedNewPaymentsCannotBeDecided() throws Exception {
        String legacyBody = s.pay(s.juanSession, s.juanDebt, "1000.00")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long legacyPayment = objectMapper.readTree(legacyBody).get("id").asLong();
        s.confirmPayment(s.anaSession, legacyPayment)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.properties.code").value(PaymentNotDecidableException.CODE));

        PaymentScenario other = new PaymentScenario(mockMvc, jdbc, jwtService, objectMapper);
        String confirmedBody = other.registerFull(other.juanSession, other.juanDebt, "3333.33")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long confirmedPayment = objectMapper.readTree(confirmedBody).get("paymentId").asLong();
        other.rejectPayment(other.anaSession, confirmedPayment)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.properties.code").value(PaymentNotDecidableException.CODE));

        assertThat(other.statusOf(other.juanDebt)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class,
                confirmedPayment)).isEqualTo("CONFIRMED");
    }

    @Test
    void anAnonymousCallerIsUnauthorized() throws Exception {
        long paymentId = reportedPayment();

        s.confirmPayment(null, paymentId).andExpect(status().isUnauthorized());
        s.rejectPayment(null, paymentId).andExpect(status().isUnauthorized());

        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PAYMENT_REPORTED");
    }

    /** A second admin is needed twice; promote Carla through the real membership row. */
    private jakarta.servlet.http.Cookie carlaAdminSession() {
        s.makeAdmin(s.carla);
        return s.carlaSession;
    }
}
