package com.amano.backend.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.amano.backend.config.JwtService;
import com.amano.backend.support.AbstractIntegrationIT;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 022 T032/T033/T035 (US5): HTTP contract of the administrator decision over a
 * reported payment. Only an active ADMIN of the group can confirm or reject;
 * the decision is recorded in the payment and the debt moves
 * {@code PAYMENT_REPORTED → VERIFIED} or {@code → PENDING}. Decisions outside
 * the group are neutral 404s and non-decidable payments answer 409.
 */
class PaymentDecisionControllerIT extends AbstractIntegrationIT {

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
        // Rewritten for 022 T085: the earlier 018 partial payment is made BEFORE the group asks for
        // validation (the inherited endpoint now respects the setting too).
        String legacy = s.pay(s.juanSession, s.juanDebt, "1000.00")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long legacyPayment = objectMapper.readTree(legacy).get("id").asLong();
        s.requireValidation();
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

    @Test
    void t049ActivityHistoryPreservesTheFullPathOfDebtStateTransitions() throws Exception {
        s.requireValidation();

        // 1) PENDING -> PAYMENT_REPORTED (Juan reports payment)
        long firstPayment = s.registerReported(s.juanSession, s.juanDebt, "3333.33");
        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PAYMENT_REPORTED");

        // 2) PAYMENT_REPORTED -> PENDING (Ana rejects payment)
        s.rejectPayment(s.anaSession, firstPayment).andExpect(status().isOk());
        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PENDING");

        // 3) PENDING -> PAYMENT_REPORTED -> VERIFIED (Juan reports again, Ana confirms)
        long secondPayment = s.registerReported(s.juanSession, s.juanDebt, "3333.33");
        s.confirmPayment(s.anaSession, secondPayment).andExpect(status().isOk());
        assertThat(s.statusOf(s.juanDebt)).isEqualTo("VERIFIED");

        // 4) Second debt (Abuela): disable validation and pay directly -> PAID
        jdbc.update("UPDATE group_permissions SET payments_require_validation = FALSE WHERE group_id = ?", s.groupId);
        s.registerFull(s.anaSession, s.abuelaDebt, "3333.33").andExpect(status().isCreated());
        assertThat(s.statusOf(s.abuelaDebt)).isEqualTo("PAID");

        String historyBody = mockMvc.perform(get("/api/groups/" + s.groupId + "/activity")
                .cookie(s.anaSession)
                .param("eventType", "EXPENSE_CREATED,PAYMENT_REGISTERED,PAYMENT_REJECTED,PAYMENT_CONFIRMED"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode entries = objectMapper.readTree(historyBody).get("content");

        // Descending chronological order (newest first):
        // [0] PAYMENT_REGISTERED (abuelaDebt: PENDING -> PAID)
        // [1] PAYMENT_CONFIRMED  (juanDebt: PAYMENT_REPORTED -> VERIFIED)
        // [2] PAYMENT_REGISTERED (juanDebt: PENDING -> PAYMENT_REPORTED)
        // [3] PAYMENT_REJECTED   (juanDebt: PAYMENT_REPORTED -> PENDING)
        // [4] PAYMENT_REGISTERED (juanDebt: PENDING -> PAYMENT_REPORTED)
        // [5] EXPENSE_CREATED
        assertThat(entries).hasSize(6);
        assertThat(List.of(
                entries.get(0).get("eventType").asString(),
                entries.get(1).get("eventType").asString(),
                entries.get(2).get("eventType").asString(),
                entries.get(3).get("eventType").asString(),
                entries.get(4).get("eventType").asString(),
                entries.get(5).get("eventType").asString()))
                .containsExactly(
                        "PAYMENT_REGISTERED",
                        "PAYMENT_CONFIRMED",
                        "PAYMENT_REGISTERED",
                        "PAYMENT_REJECTED",
                        "PAYMENT_REGISTERED",
                        "EXPENSE_CREATED");

        assertThat(entries.get(4).get("details").get("debtStatusBefore").asString()).isEqualTo("PENDING");
        assertThat(entries.get(4).get("details").get("debtStatusAfter").asString()).isEqualTo("PAYMENT_REPORTED");
        assertThat(entries.get(3).get("details").get("debtStatusBefore").asString()).isEqualTo("PAYMENT_REPORTED");
        assertThat(entries.get(3).get("details").get("debtStatusAfter").asString()).isEqualTo("PENDING");
        assertThat(entries.get(2).get("details").get("debtStatusBefore").asString()).isEqualTo("PENDING");
        assertThat(entries.get(2).get("details").get("debtStatusAfter").asString()).isEqualTo("PAYMENT_REPORTED");
        assertThat(entries.get(1).get("details").get("debtStatusBefore").asString()).isEqualTo("PAYMENT_REPORTED");
        assertThat(entries.get(1).get("details").get("debtStatusAfter").asString()).isEqualTo("VERIFIED");
        assertThat(entries.get(0).get("details").get("debtStatusBefore").asString()).isEqualTo("PENDING");
        assertThat(entries.get(0).get("details").get("debtStatusAfter").asString()).isEqualTo("PAID");
    }

    /** A second admin is needed twice; promote Carla through the real membership row. */
    private jakarta.servlet.http.Cookie carlaAdminSession() {
        s.makeAdmin(s.carla);
        return s.carlaSession;
    }

    // ---- T075 (022): GET /api/groups/{id}/payments/pending-validation ----

    private org.springframework.test.web.servlet.ResultActions pendingValidation(
            jakarta.servlet.http.Cookie session) throws Exception {
        var request = get("/api/groups/" + s.groupId + "/payments/pending-validation");
        if (session != null) {
            request.cookie(session);
        }
        return mockMvc.perform(request);
    }

    @Test
    void t075AdminsListOnlyTheReportedPaymentsWithTheirDetails() throws Exception {
        long paymentId = reportedPayment();

        pendingValidation(s.anaSession)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].paymentId").value(paymentId))
                .andExpect(jsonPath("$[0].debtId").value(s.juanDebt))
                .andExpect(jsonPath("$[0].expenseId").value(s.expenseId))
                .andExpect(jsonPath("$[0].payerUserId").value(s.juan))
                .andExpect(jsonPath("$[0].payerName").value("Juan Pérez"))
                .andExpect(jsonPath("$[0].receiverName").value("Ana López"))
                .andExpect(jsonPath("$[0].amount").value(3333.33))
                .andExpect(jsonPath("$[0].currency").value("ARS"))
                .andExpect(jsonPath("$[0].registeredAt").isNotEmpty())
                .andExpect(jsonPath("$[0].daysPending").value(0));
    }

    @Test
    void t075DecidedPaymentsLeaveTheQueue() throws Exception {
        long paymentId = reportedPayment();
        s.confirmPayment(s.anaSession, paymentId).andExpect(status().isOk());

        pendingValidation(s.anaSession)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void t075OnlyAdminsSeeTheQueueNeutral404ForOthersAnd401WithoutSession() throws Exception {
        reportedPayment();

        pendingValidation(s.carlaSession)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Solo los administradores pueden ver los pagos por validar"));
        pendingValidation(s.outsiderSession)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(PaymentNotFoundException.MESSAGE));
        pendingValidation(null).andExpect(status().isUnauthorized());
    }

    @Test
    void t075TheQueueDoesNotLeakPaymentsOfOtherGroups() throws Exception {
        PaymentScenario other = new PaymentScenario(mockMvc, jdbc, jwtService, objectMapper);
        other.requireValidation();
        other.registerReported(other.juanSession, other.juanDebt, "3333.33");

        pendingValidation(s.anaSession)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---- T056 (022): a reported payment is NOT settled (plan.md D4) ----

    private java.math.BigDecimal juanBalance() throws Exception {
        String body = mockMvc.perform(get("/api/groups/" + s.groupId + "/balances").cookie(s.anaSession))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (var member : objectMapper.readTree(body).get("currencies").get(0).get("balances")) {
            if (member.get("memberId").asLong() == s.juan && "REGISTERED".equals(member.get("memberType").asString())) {
                return new java.math.BigDecimal(member.get("netBalance").asString());
            }
        }
        throw new AssertionError("Juan is not in the balances");
    }

    @Test
    void t056AReportedPaymentKeepsCountingInTheBalancesAndTheProposalUntilItIsConfirmed() throws Exception {
        assertThat(juanBalance()).isEqualByComparingTo("-3333.33");
        long paymentId = reportedPayment();

        // Reported: still owed, shown with its full outstanding amount and in the proposal.
        assertThat(juanBalance()).isEqualByComparingTo("-3333.33");
        assertThat(jdbc.queryForObject("SELECT paid_amount FROM debts WHERE id = ?", BigDecimal.class,
                s.juanDebt)).isEqualByComparingTo("0.00");
        mockMvc.perform(get("/api/groups/" + s.groupId + "/debts/pending").cookie(s.juanSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.debtId == %d)].status".formatted(s.juanDebt)).value("PAYMENT_REPORTED"))
                .andExpect(jsonPath("$[?(@.debtId == %d)].outstanding".formatted(s.juanDebt)).value(3333.33));
        mockMvc.perform(get("/api/groups/" + s.groupId + "/settlement-proposal").cookie(s.anaSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposals[0].summary.totalTransferred").value(6666.66));

        // Confirmed (VERIFIED): settled, Juan is at zero.
        s.confirmPayment(s.anaSession, paymentId).andExpect(status().isOk());
        assertThat(juanBalance()).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForObject("SELECT paid_amount FROM debts WHERE id = ?", BigDecimal.class,
                s.juanDebt)).isEqualByComparingTo("3333.33");
    }

    @Test
    void t056ARejectedReportedPaymentLeavesTheBalanceUnchanged() throws Exception {
        long paymentId = reportedPayment();
        assertThat(juanBalance()).isEqualByComparingTo("-3333.33");

        s.rejectPayment(s.anaSession, paymentId).andExpect(status().isOk());

        assertThat(juanBalance()).isEqualByComparingTo("-3333.33");
        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PENDING");
    }

    // ---- Partial payments (022 T080): confirming ADDS the amount; rejecting restores the state ----

    @Test
    void confirmingPartialReportedPaymentsAddsThemAndEndsVerified() throws Exception {
        s.requireValidation();
        long first = s.registerReported(s.juanSession, s.juanDebt, "1000.00");

        s.confirmPayment(s.anaSession, first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.debtStatusAfter").value("PARTIALLY_PAID"));
        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PARTIALLY_PAID");
        assertThat(s.paidAmount(s.juanDebt)).isEqualByComparingTo("1000.00");
        assertThat(s.netBalanceOf(s.juan)).isEqualByComparingTo("-2333.33");

        long second = s.registerReported(s.juanSession, s.juanDebt, "2333.33");
        // While it waits, nothing else is accepted and nothing is paid yet.
        s.registerFull(s.juanSession, s.juanDebt, "1.00").andExpect(status().isConflict());
        assertThat(s.paidAmount(s.juanDebt)).isEqualByComparingTo("1000.00");
        assertThat(s.netBalanceOf(s.juan)).isEqualByComparingTo("-2333.33");

        s.confirmPayment(s.anaSession, second)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debtStatusAfter").value("VERIFIED"));
        assertThat(s.statusOf(s.juanDebt)).isEqualTo("VERIFIED");
        assertThat(s.paidAmount(s.juanDebt)).isEqualByComparingTo("3333.33");
        assertThat(s.netBalanceOf(s.juan)).isEqualByComparingTo("0.00");

        // The history keeps the amount of each decided payment.
        mockMvc.perform(get("/api/groups/" + s.groupId + "/activity").cookie(s.anaSession)
                .param("eventType", "PAYMENT_CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].details.amount").value("2333.33"))
                .andExpect(jsonPath("$.content[0].details.debtStatusAfter").value("VERIFIED"))
                .andExpect(jsonPath("$.content[1].details.amount").value("1000.00"))
                .andExpect(jsonPath("$.content[1].details.debtStatusAfter").value("PARTIALLY_PAID"));
    }

    @Test
    void rejectingAPartialReportedPaymentReturnsTheDebtToItsPreviousState() throws Exception {
        s.requireValidation();
        // Nothing paid before: back to PENDING.
        long first = s.registerReported(s.juanSession, s.juanDebt, "1000.00");
        s.rejectPayment(s.anaSession, first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debtStatusAfter").value("PENDING"));
        assertThat(s.paidAmount(s.juanDebt)).isEqualByComparingTo("0.00");

        // Something paid before: back to PARTIALLY_PAID with the same paid amount.
        long second = s.registerReported(s.juanSession, s.juanDebt, "1000.00");
        s.confirmPayment(s.anaSession, second).andExpect(status().isOk());
        long third = s.registerReported(s.juanSession, s.juanDebt, "500.00");
        s.rejectPayment(s.anaSession, third)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debtStatusAfter").value("PARTIALLY_PAID"));

        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PARTIALLY_PAID");
        assertThat(s.paidAmount(s.juanDebt)).isEqualByComparingTo("1000.00");
        assertThat(s.netBalanceOf(s.juan)).isEqualByComparingTo("-2333.33");
    }

    @Test
    void aDecisionOnAPaymentWrittenByTheInheritedFlowWorksLikeAnyOtherReportedPayment() throws Exception {
        // legacy is only an origin marker: it must not make a REPORTED payment undecidable.
        s.requireValidation();
        long paymentId = s.registerReported(s.juanSession, s.juanDebt, "1000.00");
        jdbc.update("UPDATE payments SET legacy = TRUE WHERE id = ?", paymentId);

        pendingValidation(s.anaSession)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paymentId").value(paymentId))
                .andExpect(jsonPath("$[0].amount").value(1000.0));
        s.confirmPayment(s.anaSession, paymentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debtStatusAfter").value("PARTIALLY_PAID"));
    }
}
