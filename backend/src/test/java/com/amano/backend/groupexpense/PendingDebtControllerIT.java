package com.amano.backend.groupexpense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.amano.backend.config.JwtService;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 022 US7 (T043-T045): HTTP contract of
 * {@code GET /api/groups/{groupId}/debts/pending}. Ana (ADMIN) pays several
 * expenses for herself and Juan (MEMBER), plus one with the simulated profile
 * Abuela, so Ana is the creditor of every debt. Only the debts in which the
 * caller participates are listed; PAID and VERIFIED are settled and never
 * appear. One expense is in USD with a stored historical conversion so the
 * response can show both the original and the converted amount.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class PendingDebtControllerIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String EXPENSE_DATE = "2026-11-05";

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

    private long ana;
    private long juan;
    private long carla;
    private long outsider;
    private Cookie anaSession;
    private Cookie juanSession;
    private Cookie carlaSession;
    private Cookie outsiderSession;
    private long groupId;
    private long abuela;

    private long pendingDebt;
    private long partialDebt;
    private long reportedDebt;
    private long overdueDebt;
    private long paidDebt;
    private long verifiedDebt;
    private long convertedDebt;
    private long convertedExpense;
    private long abuelaDebt;

    @BeforeEach
    void buildGroup() throws Exception {
        int n = SEQUENCE.incrementAndGet() + (int) (System.nanoTime() % 100000);
        ana = user("Ana", "López", "ana" + n + "@pending.dev");
        juan = user("Juan", "Pérez", "juan" + n + "@pending.dev");
        carla = user("Carla", "Gómez", "carla" + n + "@pending.dev");
        outsider = user("Otra", "Persona", "otra" + n + "@pending.dev");
        for (long friend : new long[] { juan, carla }) {
            jdbc.update("""
                    INSERT INTO friendship_requests (sender_id, recipient_id, status, responded_at)
                    VALUES (?, ?, 'ACCEPTED', now())
                    """, ana, friend);
        }
        anaSession = new Cookie("amano_auth", jwtService.issueToken(ana));
        juanSession = new Cookie("amano_auth", jwtService.issueToken(juan));
        carlaSession = new Cookie("amano_auth", jwtService.issueToken(carla));
        outsiderSession = new Cookie("amano_auth", jwtService.issueToken(outsider));

        String created = mockMvc.perform(post("/api/groups").cookie(anaSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Depto\", \"member\": {\"memberType\": \"REGISTERED\","
                        + " \"identifier\": \"juan" + n + "@pending.dev\"}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        groupId = objectMapper.readTree(created).get("id").asLong();
        mockMvc.perform(post("/api/groups/" + groupId + "/members").cookie(anaSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberType\": \"REGISTERED\", \"identifier\": \"carla" + n + "@pending.dev\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/groups/" + groupId + "/members").cookie(anaSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberType\": \"SIMULATED\", \"identifier\": \"abuela" + n + "@pending.dev\","
                        + " \"name\": \"Abuela\"}"))
                .andExpect(status().isCreated());
        abuela = jdbc.queryForObject(
                "SELECT id FROM simulated_profiles WHERE group_id = ?", Long.class, groupId);

        pendingDebt = juanExpense();
        partialDebt = juanExpense();
        reportedDebt = juanExpense();
        overdueDebt = juanExpense();
        paidDebt = juanExpense();
        verifiedDebt = juanExpense();

        jdbc.update("UPDATE debts SET status = 'PARTIALLY_PAID', paid_amount = 20.00 WHERE id = ?", partialDebt);
        jdbc.update("UPDATE debts SET status = 'PAYMENT_REPORTED', paid_amount = 0 WHERE id = ?", reportedDebt);
        jdbc.update("UPDATE debts SET status = 'OVERDUE' WHERE id = ?", overdueDebt);
        jdbc.update("UPDATE debts SET status = 'PAID', paid_amount = amount WHERE id = ?", paidDebt);
        jdbc.update("UPDATE debts SET status = 'VERIFIED', paid_amount = amount WHERE id = ?", verifiedDebt);

        convertedExpense = jdbc.queryForObject("""
                INSERT INTO group_expenses (group_id, created_by_user_id, payer_user_id, amount, currency,
                    expense_date, description, idempotency_key, converted_amount, exchange_rate, rate_date, rate_source)
                VALUES (?, ?, ?, 100.00, 'USD', ?, 'Súper', gen_random_uuid(),
                    100000.00, 1000.00000000, DATE '2026-11-05', 'BCRA')
                RETURNING id
                """, Long.class, groupId, ana, ana, java.sql.Date.valueOf(EXPENSE_DATE));
        long convertedShare = jdbc.queryForObject("""
                INSERT INTO group_expense_shares (expense_id, position, participant_type, user_id,
                    participant_name, amount)
                VALUES (?, 0, 'REGISTERED', ?, 'Juan Pérez', 50.00)
                RETURNING id
                """, Long.class, convertedExpense, juan);
        convertedDebt = jdbc.queryForObject("""
                INSERT INTO debts (group_id, expense_id, share_id, debtor_type, debtor_user_id, debtor_name,
                    creditor_user_id, currency, amount, paid_amount, status)
                VALUES (?, ?, ?, 'REGISTERED', ?, 'Juan Pérez', ?, 'USD', 50.00, 0.00, 'PENDING')
                RETURNING id
                """, Long.class, groupId, convertedExpense, convertedShare, juan, ana);

        JsonNode abuelaExpense = createExpense("100.00", "ARS", ana, participants(
                "{\"type\": \"REGISTERED\", \"userId\": %d}".formatted(ana),
                "{\"type\": \"SIMULATED\", \"simulatedProfileId\": %d}".formatted(abuela)));
        abuelaDebt = abuelaExpense.get("debts").get(0).get("id").asLong();
    }

    private long user(String first, String last, String email) {
        return jdbc.queryForObject("""
                INSERT INTO users (first_name, last_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES (?, ?, ?, 'x', now(), now()) RETURNING id
                """, Long.class, first, last, email);
    }

    private long juanExpense() throws Exception {
        JsonNode expense = createExpense("100.00", "ARS", ana, participants(
                "{\"type\": \"REGISTERED\", \"userId\": %d}".formatted(ana),
                "{\"type\": \"REGISTERED\", \"userId\": %d}".formatted(juan)));
        return expense.get("debts").get(0).get("id").asLong();
    }

    private static String participants(String... participants) {
        return "[" + String.join(",", participants) + "]";
    }

    private JsonNode createExpense(String amount, String currency, long payer, String participants)
            throws Exception {
        String body = """
                {"amount": "%s", "currency": "%s", "date": "%s", "description": "Súper",
                 "deviceTimeZone": "America/Argentina/Buenos_Aires", "payerUserId": %d,
                 "participants": %s}
                """.formatted(amount, currency, EXPENSE_DATE, payer, participants);
        String response = mockMvc.perform(post("/api/groups/" + groupId + "/expenses").cookie(anaSession)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode pending(Cookie session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/groups/" + groupId + "/debts/pending").cookie(session))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static JsonNode debt(JsonNode body, long debtId) {
        for (JsonNode node : body) {
            if (node.get("debtId").asLong() == debtId) {
                return node;
            }
        }
        throw new AssertionError("La deuda " + debtId + " no está en la respuesta: " + body);
    }

    private static boolean contains(JsonNode body, long debtId) {
        for (JsonNode node : body) {
            if (node.get("debtId").asLong() == debtId) {
                return true;
            }
        }
        return false;
    }

    @Test
    void t043ReturnsTheCallersOpenDebtsInTheFourBalanceStates() throws Exception {
        JsonNode body = pending(juanSession);

        assertThat(body).hasSize(5); // four states plus the USD expense, all of Juan
        Map<Long, String> statuses = new HashMap<>();
        for (JsonNode node : body) {
            statuses.put(node.get("debtId").asLong(), node.get("status").asString());
        }
        assertThat(statuses)
                .containsEntry(pendingDebt, "PENDING")
                .containsEntry(partialDebt, "PARTIALLY_PAID")
                .containsEntry(reportedDebt, "PAYMENT_REPORTED")
                .containsEntry(overdueDebt, "OVERDUE")
                .containsEntry(convertedDebt, "PENDING");
        assertThat(statuses).doesNotContainKeys(paidDebt, verifiedDebt);
        assertThat(statuses).doesNotContainKey(abuelaDebt);
    }

    @Test
    void t043OnlyMembersSeeTheirOwnDebts() throws Exception {
        mockMvc.perform(get("/api/groups/" + groupId + "/debts/pending").cookie(outsiderSession))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/groups/" + groupId + "/debts/pending"))
                .andExpect(status().isUnauthorized());

        assertThat(pending(carlaSession)).isEmpty();
        assertThat(contains(pending(anaSession), abuelaDebt)).isTrue();
        assertThat(contains(pending(juanSession), abuelaDebt)).isFalse();
    }

    @Test
    void t044IncludesTheExpenseOriginAndTheConvertedAmount() throws Exception {
        JsonNode node = debt(pending(juanSession), convertedDebt);

        assertThat(node.get("expenseId").asLong()).isEqualTo(convertedExpense);
        assertThat(node.get("shareId").asLong()).isPositive();
        assertThat(node.get("currency").asString()).isEqualTo("USD");
        assertThat(new BigDecimal(node.get("amount").asString())).isEqualByComparingTo("50.00");
        assertThat(new BigDecimal(node.get("paidAmount").asString())).isEqualByComparingTo("0.00");
        assertThat(new BigDecimal(node.get("outstanding").asString())).isEqualByComparingTo("50.00");
        assertThat(new BigDecimal(node.get("convertedAmount").asString())).isEqualByComparingTo("50000.00");
        assertThat(node.get("convertedCurrency").asString()).isEqualTo("ARS");

        JsonNode plain = debt(pending(juanSession), pendingDebt);
        assertThat(plain.get("expenseId").asLong()).isPositive();
        assertThat(plain.get("shareId").asLong()).isPositive();
        assertThat(plain.get("currency").asString()).isEqualTo("ARS");
        assertThat(new BigDecimal(plain.get("amount").asString())).isEqualByComparingTo("50.00");
        assertThat(new BigDecimal(plain.get("paidAmount").asString())).isEqualByComparingTo("0.00");
        assertThat(new BigDecimal(plain.get("outstanding").asString())).isEqualByComparingTo("50.00");
    }

    @Test
    void t045TheSimulatedDebtorAppearsWithItsName() throws Exception {
        JsonNode node = debt(pending(anaSession), abuelaDebt);

        assertThat(node.get("debtorName").asString()).isEqualTo("Abuela");
        assertThat(node.get("creditorName").asString()).isEqualTo("Ana López");
        assertThat(node.get("status").asString()).isEqualTo("PENDING");
    }
}
