package com.amano.backend.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 022 T038–T041 (US6): HTTP contract of
 * {@code POST /api/groups/{groupId}/debts/{debtId}/payments}. Only full
 * payments are accepted, the group validation flag decides
 * {@code CONFIRMED}/{@code PAID} vs {@code REPORTED}/{@code PAYMENT_REPORTED},
 * the same key replays the stored result and a different key over an already
 * reported or settled debt answers 409.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class DebtPaymentControllerIT {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

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
    void aFullPaymentWithoutValidationConfirmsItAndSettlesTheDebt() throws Exception {
        String key = UUID.randomUUID().toString();

        String body = s.registerFull(s.juanSession, s.juanDebt, "3333.33", key)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        matchesPattern("/api/groups/\\d+/payments/\\d+")))
                .andExpect(jsonPath("$.paymentId").isNumber())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.debtStatusAfter").value("PAID"))
                .andExpect(jsonPath("$.decidedByUserId").doesNotExist())
                .andExpect(jsonPath("$.decidedAt").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        long paymentId = objectMapper.readTree(body).get("paymentId").asLong();
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM payments WHERE id = ?", paymentId);
        assertThat(row.get("debt_id")).isEqualTo(s.juanDebt);
        assertThat(row.get("group_id")).isEqualTo(s.groupId);
        assertThat(row.get("status")).isEqualTo("CONFIRMED");
        assertThat(row.get("legacy")).isEqualTo(false);
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("3333.33");
        assertThat(row.get("currency")).isEqualTo("ARS");
        assertThat(row.get("payer_user_id")).isEqualTo(s.juan);
        assertThat(row.get("receiver_user_id")).isEqualTo(s.ana);
        assertThat(row.get("registered_by_user_id")).isEqualTo(s.juan);
        assertThat(row.get("idempotency_key").toString()).isEqualTo(key);
        assertThat(jdbc.queryForObject("SELECT payment_date FROM payments WHERE id = ?", LocalDate.class,
                paymentId)).isEqualTo(LocalDate.now(ZONE));

        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT paid_amount FROM debts WHERE id = ?", BigDecimal.class,
                s.juanDebt)).isEqualByComparingTo("3333.33");
        assertThat(s.activePayments(s.juanDebt)).isEqualTo(1);
        assertThat(s.paymentEvents()).isEqualTo(1);
    }

    @Test
    void withValidationThePaymentIsReportedAndTheDebtWaitsForTheAdmin() throws Exception {
        s.requireValidation();

        s.registerFull(s.juanSession, s.juanDebt, "3333.33")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REPORTED"))
                .andExpect(jsonPath("$.debtStatusAfter").value("PAYMENT_REPORTED"))
                .andExpect(jsonPath("$.decidedByUserId").doesNotExist())
                .andExpect(jsonPath("$.decidedAt").doesNotExist());

        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PAYMENT_REPORTED");
        assertThat(s.activePayments(s.juanDebt)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE debt_id = ?", String.class,
                s.juanDebt)).isEqualTo("REPORTED");
    }

    @Test
    void aPartialPaymentIsRejectedWithAClearMessage() throws Exception {
        s.registerFull(s.juanSession, s.juanDebt, "1000.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La solicitud contiene campos inválidos"))
                .andExpect(jsonPath("$.properties.fields.amount")
                        .value("El monto del pago debe ser el total de la deuda pendiente"));

        s.registerFull(s.juanSession, s.juanDebt, "3333.34")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.amount")
                        .value("El monto del pago excede la deuda pendiente"));

        s.registerFull(s.juanSession, s.juanDebt, "0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.amount").value("El monto debe ser mayor a cero"));

        assertThat(s.payments(s.juanDebt)).isZero();
        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PENDING");
        assertThat(s.paymentEvents()).isZero();
    }

    @Test
    void theSameKeyOnTheSameDebtReplaysTheStoredResultWithoutDuplicating() throws Exception {
        String key = UUID.randomUUID().toString();
        String first = s.registerFull(s.juanSession, s.juanDebt, "3333.33", key)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long paymentId = objectMapper.readTree(first).get("paymentId").asLong();

        s.registerFull(s.anaSession, s.juanDebt, "3333.33", key)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.debtStatusAfter").value("PAID"));

        s.registerFull(s.juanSession, s.juanDebt, "1000.00", key)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.properties.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(s.payments(s.juanDebt)).isEqualTo(1);
        assertThat(s.paymentEvents()).isEqualTo(1);
        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PAID");
    }

    @Test
    void aDifferentKeyOverAnAlreadyReportedDebtAnswers409() throws Exception {
        s.requireValidation();
        s.registerFull(s.juanSession, s.juanDebt, "3333.33").andExpect(status().isCreated());

        s.registerFull(s.juanSession, s.juanDebt, "3333.33")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Esta deuda ya tiene un pago registrado"))
                .andExpect(jsonPath("$.properties.code").value("DEBT_ALREADY_HAS_PAYMENT"));

        assertThat(s.activePayments(s.juanDebt)).isEqualTo(1);
        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PAYMENT_REPORTED");
    }

    @Test
    void aDifferentKeyOverAnAlreadySettledDebtAnswers409() throws Exception {
        s.registerFull(s.juanSession, s.juanDebt, "3333.33").andExpect(status().isCreated());

        s.registerFull(s.anaSession, s.juanDebt, "3333.33")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Esta deuda ya tiene un pago registrado"));

        assertThat(s.payments(s.juanDebt)).isEqualTo(1);
        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PAID");
    }

    @Test
    void theRemainingBalanceOfALegacyPartialDebtCanBeSettledByTheNewFlow() throws Exception {
        s.pay(s.juanSession, s.juanDebt, "1000.00").andExpect(status().isCreated());
        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PARTIALLY_PAID");

        s.registerFull(s.juanSession, s.juanDebt, "2333.33")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.debtStatusAfter").value("PAID"));

        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT paid_amount FROM debts WHERE id = ?", BigDecimal.class,
                s.juanDebt)).isEqualByComparingTo("3333.33");
        assertThat(s.payments(s.juanDebt)).isEqualTo(2);
        assertThat(s.activePayments(s.juanDebt)).isEqualTo(1);
    }

    @Test
    void anOverdueDebtCanStillBePaidWithTheFullBalance() throws Exception {
        jdbc.update("UPDATE debts SET status = 'OVERDUE' WHERE id = ?", s.juanDebt);

        s.registerFull(s.juanSession, s.juanDebt, "3333.33")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.debtStatusAfter").value("PAID"));

        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PAID");
    }

    @Test
    void invalidInputAnswers400WithoutWriting() throws Exception {
        s.registerFull(s.juanSession, s.juanDebt, "3333.33", "no-es-uuid")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.idempotencyKey").exists());
        s.registerFull(s.juanSession, s.juanDebt, "3333.33", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.idempotencyKey")
                        .value("Indicá la clave de idempotencia"));
        mockMvc.perform(post("/api/groups/" + s.groupId + "/debts/" + s.juanDebt + "/payments")
                .cookie(s.juanSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": \"3333.33\", \"idempotencyKey\": \"%s\", \"extra\": 1}"
                        .formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.extra").value("No se permite informar este campo"));

        assertThat(s.payments(s.juanDebt)).isZero();
        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PENDING");
    }

    @Test
    void onlyInvolvedActiveMembersCanRegisterThePayment() throws Exception {
        s.registerFull(s.carlaSession, s.juanDebt, "3333.33")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail")
                        .value("Solo quien debe, quien cobra o un administrador pueden registrar este pago"));
        s.registerFull(s.outsiderSession, s.juanDebt, "3333.33")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe la deuda solicitada o no sos miembro del grupo"));
        mockMvc.perform(post("/api/groups/" + (s.groupId + 999) + "/debts/" + s.juanDebt + "/payments")
                .cookie(s.juanSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(PaymentScenario.fullBody("3333.33", UUID.randomUUID().toString())))
                .andExpect(status().isNotFound());
        s.registerFull(null, s.juanDebt, "3333.33").andExpect(status().isUnauthorized());

        assertThat(s.payments(s.juanDebt)).isZero();
    }
}
