package com.amano.backend.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.amano.backend.config.JwtService;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 018 T024/T025/T032/T036 (US1): HTTP contract of {@code POST /api/payments}
 * (plan.md §Contrato REST), the debt updated in the database by Hibernate, the
 * PAYMENT_MADE history entry, the verified-account rule and
 * {@code canRegisterPayment} in the 017 expense detail.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class PaymentControllerIT {

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

    @Test
    void aPartialPaymentAnswers201AndLeavesTheDebtPartiallyPaid() throws Exception {
        s.pay(s.juanSession, s.juanDebt, "1000.00")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/payments/\\d+")))
                .andExpect(jsonPath("$.groupId").value(s.groupId))
                .andExpect(jsonPath("$.expenseId").value(s.expenseId))
                .andExpect(jsonPath("$.debtId").value(s.juanDebt))
                .andExpect(jsonPath("$.amount").value("1000.00"))
                .andExpect(jsonPath("$.currency").value("ARS"))
                .andExpect(jsonPath("$.date").value(PaymentScenario.EXPENSE_DATE))
                .andExpect(jsonPath("$.payer.type").value("REGISTERED"))
                .andExpect(jsonPath("$.payer.userId").value(s.juan))
                .andExpect(jsonPath("$.payer.name").value("Juan Pérez"))
                .andExpect(jsonPath("$.receiver.userId").value(s.ana))
                .andExpect(jsonPath("$.receiver.name").value("Ana López"))
                .andExpect(jsonPath("$.registeredBy.userId").value(s.juan))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.debt.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.debt.paidAmount").value("1000.00"))
                .andExpect(jsonPath("$.debt.outstanding").value("2333.33"))
                .andExpect(jsonPath("$.debt.canRegisterPayment").value(true));

        var row = jdbc.queryForMap("SELECT status, paid_amount, updated_at > created_at AS touched FROM debts WHERE id = ?",
                s.juanDebt);
        assertThat(row.get("status")).isEqualTo("PARTIALLY_PAID");
        assertThat((BigDecimal) row.get("paid_amount")).isEqualByComparingTo("1000.00");
        assertThat(row.get("touched")).isEqualTo(true);
    }

    @Test
    void theSecondPaymentOfTheBalancePaysTheDebtAndBothAreInTheHistory() throws Exception {
        s.pay(s.juanSession, s.juanDebt, "1000.00").andExpect(status().isCreated());
        s.pay(s.anaSession, s.juanDebt, "2333.33")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registeredBy.userId").value(s.ana))
                .andExpect(jsonPath("$.debt.status").value("PAID"))
                .andExpect(jsonPath("$.debt.outstanding").value("0.00"))
                .andExpect(jsonPath("$.debt.canRegisterPayment").value(false));

        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM payments WHERE debt_id = ?", BigDecimal.class,
                s.juanDebt)).isEqualByComparingTo(jdbc.queryForObject(
                        "SELECT paid_amount FROM debts WHERE id = ?", BigDecimal.class, s.juanDebt));

        String history = mockMvc.perform(get("/api/groups/" + s.groupId + "/activity")
                .param("eventType", "PAYMENT_MADE").cookie(s.anaSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode entries = objectMapper.readTree(history).get("content");
        assertThat(entries).hasSize(2);
        JsonNode latest = entries.get(0);
        assertThat(latest.get("eventType").asString()).isEqualTo("PAYMENT_MADE");
        assertThat(latest.get("actor").get("id").asLong()).isEqualTo(s.ana);
        JsonNode details = latest.get("details");
        assertThat(details.get("paymentId").asLong()).isPositive();
        assertThat(details.get("amount").asString()).isEqualTo("2333.33");
        assertThat(details.get("currency").asString()).isEqualTo("ARS");
        assertThat(details.get("fromName").asString()).isEqualTo("Juan Pérez");
        assertThat(details.get("toName").asString()).isEqualTo("Ana López");
        assertThat(details.get("expenseId").asLong()).isEqualTo(s.expenseId);
        assertThat(details.get("debtId").asLong()).isEqualTo(s.juanDebt);
    }

    @Test
    void theCreditorPaysTheSimulatedProfileDebt() throws Exception {
        s.pay(s.anaSession, s.abuelaDebt, "3333.33")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payer.type").value("SIMULATED"))
                .andExpect(jsonPath("$.payer.simulatedProfileId").value(s.abuela))
                .andExpect(jsonPath("$.payer.name").value("Abuela"))
                .andExpect(jsonPath("$.debt.status").value("PAID"));
    }

    @Test
    void theSameKeyReplaysWith200AndOtherDataWith422() throws Exception {
        String key = UUID.randomUUID().toString();
        String first = s.pay(s.juanSession, key, PaymentScenario.body(s.juanDebt, "1000.00",
                PaymentScenario.EXPENSE_DATE))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        s.pay(s.juanSession, key, PaymentScenario.body(s.juanDebt, "1000.00", PaymentScenario.EXPENSE_DATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(objectMapper.readTree(first).get("id").asLong()));
        s.pay(s.juanSession, key, PaymentScenario.body(s.juanDebt, "999.00", PaymentScenario.EXPENSE_DATE))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.properties.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(s.payments(s.juanDebt)).isEqualTo(1);
        assertThat(s.paymentEvents()).isEqualTo(1);
    }

    @Test
    void invalidFieldsAnswer400WithoutWriting() throws Exception {
        s.pay(s.juanSession, s.juanDebt, "3333.34")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.amount")
                        .value("El pago no puede superar el saldo de $ 3.333,33"));
        s.pay(s.juanSession, UUID.randomUUID().toString(), PaymentScenario.body(s.juanDebt, "10.00", "2026-11-04"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.date")
                        .value("La fecha del pago no puede ser anterior a la del gasto"));
        s.pay(s.juanSession, UUID.randomUUID().toString(), PaymentScenario.body(s.juanDebt, "0", "2999-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.amount").exists())
                .andExpect(jsonPath("$.properties.fields.date").value("La fecha no puede ser posterior a hoy"));
        s.pay(s.juanSession, UUID.randomUUID().toString(), "{\"amount\": \"10.00\", \"date\": \"2026-11-05\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.debtId").value("Indicá la deuda a pagar"));
        s.pay(s.juanSession, "not-a-uuid", PaymentScenario.body(s.juanDebt, "10.00", PaymentScenario.EXPENSE_DATE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.idempotencyKey").exists());
        s.pay(s.juanSession, null, PaymentScenario.body(s.juanDebt, "10.00", PaymentScenario.EXPENSE_DATE))
                .andExpect(status().isBadRequest());

        assertThat(s.payments(s.juanDebt)).isZero();
        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PENDING");
    }

    @Test
    void aThirdMemberIs403AndOutsidersOrUnknownDebtsAreTheNeutral404() throws Exception {
        s.pay(s.carlaSession, s.juanDebt, "10.00")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail")
                        .value("Solo quien debe, quien cobra o un administrador pueden registrar este pago"));
        s.pay(s.outsiderSession, s.juanDebt, "10.00")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe la deuda solicitada o no sos miembro del grupo"));
        s.pay(s.juanSession, 999_999L, "10.00")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe la deuda solicitada o no sos miembro del grupo"));

        assertThat(s.payments(s.juanDebt)).isZero();
    }

    @Test
    void aPaidDebtAnswers409() throws Exception {
        s.pay(s.juanSession, s.juanDebt, "3333.33").andExpect(status().isCreated());

        s.pay(s.juanSession, s.juanDebt, "1.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La deuda ya está pagada"))
                .andExpect(jsonPath("$.properties.code").value("DEBT_ALREADY_PAID"));
        assertThat(s.payments(s.juanDebt)).isEqualTo(1);
    }

    @Test
    void withoutSessionIs401AndAnUnverifiedAccountIsTreatedLikeInGroups() throws Exception {
        s.pay(null, s.juanDebt, "10.00").andExpect(status().isUnauthorized());

        long pending = jdbc.queryForObject("""
                INSERT INTO users (first_name, email, password_hash, terms_accepted_at)
                VALUES ('Pendiente', ?, 'x', now()) RETURNING id
                """, Long.class, "pending" + System.nanoTime() + "@payments.dev");
        Cookie pendingSession = new Cookie("amano_auth", jwtService.issueToken(pending));
        int groupsStatus = mockMvc.perform(post("/api/groups").cookie(pendingSession)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getStatus();
        int paymentsStatus = s.pay(pendingSession, s.juanDebt, "10.00").andReturn().getResponse().getStatus();

        assertThat(groupsStatus).isBetween(401, 403);
        assertThat(paymentsStatus).isEqualTo(groupsStatus);
        assertThat(s.payments(s.juanDebt)).isZero();
    }

    @Test
    void theExpenseDetailTellsWhoCanRegisterAPayment() throws Exception {
        String detail = "/api/groups/" + s.groupId + "/expenses/" + s.expenseId;
        mockMvc.perform(get(detail).cookie(s.juanSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debts[0].canRegisterPayment").value(true))
                .andExpect(jsonPath("$.debts[1].canRegisterPayment").value(false));
        mockMvc.perform(get(detail).cookie(s.carlaSession))
                .andExpect(jsonPath("$.debts[0].canRegisterPayment").value(false))
                .andExpect(jsonPath("$.debts[1].canRegisterPayment").value(false));
        mockMvc.perform(get(detail).cookie(s.anaSession))
                .andExpect(jsonPath("$.debts[0].canRegisterPayment").value(true))
                .andExpect(jsonPath("$.debts[1].canRegisterPayment").value(true));

        s.pay(s.juanSession, s.juanDebt, "3333.33").andExpect(status().isCreated());

        mockMvc.perform(get(detail).cookie(s.anaSession))
                .andExpect(jsonPath("$.debts[0].status").value("PAID"))
                .andExpect(jsonPath("$.debts[0].canRegisterPayment").value(false));
    }
}
