package com.amano.backend.groupexpense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
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
 * 022 T027-T031 (US4): HTTP contract of
 * {@code GET /api/groups/{id}/settlement-proposal}. The fixture has an ARS
 * debt (Ana paid for Ana, Juan and the simulated profile Abuela) and a legacy
 * USD debt, so the proposal must come back separately per currency. The
 * endpoint is advisory and read-only: it never creates payments or changes
 * expenses, debts or the activity history (T048 is out of scope).
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class GroupSettlementControllerIT {

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
    private Cookie anaSession;
    private Cookie outsiderSession;
    private long groupId;
    private long abuela;

    @BeforeEach
    void buildGroup() throws Exception {
        int n = SEQUENCE.incrementAndGet() + (int) (System.nanoTime() % 100000);
        ana = user("Ana", "López", "ana" + n + "@settlement.dev");
        juan = user("Juan", "Pérez", "juan" + n + "@settlement.dev");
        long outsider = user("Otra", "Persona", "otra" + n + "@settlement.dev");
        jdbc.update("""
                INSERT INTO friendship_requests (sender_id, recipient_id, status, responded_at)
                VALUES (?, ?, 'ACCEPTED', now())
                """, ana, juan);
        anaSession = new Cookie("amano_auth", jwtService.issueToken(ana));
        outsiderSession = new Cookie("amano_auth", jwtService.issueToken(outsider));

        String created = mockMvc.perform(post("/api/groups").cookie(anaSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Depto\", \"member\": {\"memberType\": \"REGISTERED\","
                        + " \"identifier\": \"juan" + n + "@settlement.dev\"}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        groupId = objectMapper.readTree(created).get("id").asLong();
        mockMvc.perform(post("/api/groups/" + groupId + "/members").cookie(anaSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberType\": \"SIMULATED\", \"identifier\": \"abuela" + n + "@settlement.dev\","
                        + " \"name\": \"Abuela\"}"))
                .andExpect(status().isCreated());
        abuela = jdbc.queryForObject(
                "SELECT id FROM simulated_profiles WHERE group_id = ?", Long.class, groupId);

        mockMvc.perform(post("/api/groups/" + groupId + "/expenses")
                .cookie(anaSession)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount": "10000.00", "currency": "ARS", "date": "%s", "description": "Súper",
                         "deviceTimeZone": "America/Argentina/Buenos_Aires", "payerUserId": %d,
                         "participants": [
                           {"type": "REGISTERED", "userId": %d},
                           {"type": "REGISTERED", "userId": %d},
                           {"type": "SIMULATED", "simulatedProfileId": %d}]}
                        """.formatted(EXPENSE_DATE, ana, ana, juan, abuela)))
                .andExpect(status().isCreated());

        long expenseId = jdbc.queryForObject("""
                INSERT INTO group_expenses (group_id, created_by_user_id, payer_user_id, amount, currency,
                    expense_date, description, idempotency_key)
                VALUES (?, ?, ?, 10.00, 'USD', ?, 'Legacy USD', gen_random_uuid())
                RETURNING id
                """, Long.class, groupId, ana, ana, java.sql.Date.valueOf(EXPENSE_DATE));
        long shareId = jdbc.queryForObject("""
                INSERT INTO group_expense_shares (expense_id, position, participant_type, user_id,
                    participant_name, amount)
                VALUES (?, 99, 'REGISTERED', ?, 'Juan Pérez', 10.00)
                RETURNING id
                """, Long.class, expenseId, juan);
        jdbc.update("""
                INSERT INTO debts (group_id, expense_id, share_id, debtor_type, debtor_user_id, debtor_name,
                    creditor_user_id, currency, amount, paid_amount, status)
                VALUES (?, ?, ?, 'REGISTERED', ?, 'Juan Pérez', ?, 'USD', 10.00, 0, 'PENDING')
                """, groupId, expenseId, shareId, juan, ana);
    }

    private long user(String first, String last, String email) {
        return jdbc.queryForObject("""
                INSERT INTO users (first_name, last_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES (?, ?, ?, 'x', now(), now()) RETURNING id
                """, Long.class, first, last, email);
    }

    private JsonNode proposal(int expectedStatus, Cookie session) throws Exception {
        var request = get("/api/groups/" + groupId + "/settlement-proposal");
        if (session != null) {
            request.cookie(session);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().is(expectedStatus)).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static JsonNode currency(JsonNode body, String currency) {
        for (JsonNode node : body.get("proposals")) {
            if (currency.equals(node.get("currency").asString())) {
                return node;
            }
        }
        throw new AssertionError("La moneda " + currency + " no está en la propuesta: " + body);
    }

    private static JsonNode transfer(JsonNode proposal, long fromId, String fromType) {
        for (JsonNode node : proposal.get("transfers")) {
            if (node.get("fromMemberId").asLong() == fromId
                    && fromType.equals(node.get("fromMemberType").asString())) {
                return node;
            }
        }
        throw new AssertionError("No hay transferencia desde " + fromId + " (" + fromType + "): " + proposal);
    }

    private static BigDecimal amount(JsonNode node, String field) {
        return new BigDecimal(node.get(field).asString());
    }

    @Test
    void theProposalSettlesEachCurrencySeparatelyAndNeverSumsDifferentCurrencies() throws Exception {
        JsonNode body = proposal(200, anaSession);

        assertThat(body.get("groupId").asLong()).isEqualTo(groupId);
        assertThat(body.get("primaryCurrency").asString()).isEqualTo("ARS");
        assertThat(body.get("proposals")).hasSize(2);

        JsonNode ars = currency(body, "ARS");
        JsonNode arsSummary = ars.get("summary");
        assertThat(ars.get("transfers")).hasSize(2);
        assertThat(amount(arsSummary, "totalTransferred")).isEqualByComparingTo("6666.66");
        assertThat(amount(arsSummary, "totalNetBalances")).isEqualByComparingTo("6666.66");
        assertThat(arsSummary.get("membersSettled").asInt()).isEqualTo(3);
        assertThat(arsSummary.get("transferCount").asInt()).isEqualTo(2);

        JsonNode juanToAna = transfer(ars, juan, "REGISTERED");
        assertThat(juanToAna.get("fromMemberName").asString()).isEqualTo("Juan Pérez");
        assertThat(juanToAna.get("toMemberId").asLong()).isEqualTo(ana);
        assertThat(juanToAna.get("toMemberType").asString()).isEqualTo("REGISTERED");
        assertThat(juanToAna.get("toMemberName").asString()).isEqualTo("Ana López");
        assertThat(amount(juanToAna, "amount")).isEqualByComparingTo("3333.33");
        assertThat(juanToAna.get("informative").asBoolean()).isFalse();

        JsonNode abuelaToAna = transfer(ars, abuela, "SIMULATED");
        assertThat(abuelaToAna.get("fromMemberName").asString()).isEqualTo("Abuela");
        assertThat(abuelaToAna.get("toMemberId").asLong()).isEqualTo(ana);
        assertThat(amount(abuelaToAna, "amount")).isEqualByComparingTo("3333.33");
        assertThat(abuelaToAna.get("informative").asBoolean()).isTrue();

        JsonNode usd = currency(body, "USD");
        JsonNode usdSummary = usd.get("summary");
        assertThat(usd.get("transfers")).hasSize(1);
        assertThat(amount(usdSummary, "totalTransferred")).isEqualByComparingTo("10.00");
        assertThat(amount(usdSummary, "totalNetBalances")).isEqualByComparingTo("10.00");
        assertThat(usdSummary.get("membersSettled").asInt()).isEqualTo(2);
        assertThat(usdSummary.get("transferCount").asInt()).isEqualTo(1);
        assertThat(amount(transfer(usd, juan, "REGISTERED"), "amount")).isEqualByComparingTo("10.00");
    }

    @Test
    void theProposalIsReadOnlyAndDoesNotTouchExpensesDebtsPaymentsOrHistory() throws Exception {
        Long expenses = scalar("SELECT count(*) FROM group_expenses WHERE group_id = ?");
        Long debts = scalar("SELECT count(*) FROM debts WHERE group_id = ?");
        Long payments = scalar("""
                SELECT count(*) FROM payments p
                JOIN debts d ON d.id = p.debt_id
                WHERE d.group_id = ?
                """);
        Long activity = scalar("SELECT count(*) FROM group_activity WHERE group_id = ?");
        List<Map<String, Object>> debtRows = jdbc.queryForList(
                "SELECT id, status, paid_amount FROM debts WHERE group_id = ? ORDER BY id", groupId);

        proposal(200, anaSession);

        assertThat(scalar("SELECT count(*) FROM group_expenses WHERE group_id = ?")).isEqualTo(expenses);
        assertThat(scalar("SELECT count(*) FROM debts WHERE group_id = ?")).isEqualTo(debts);
        assertThat(scalar("""
                SELECT count(*) FROM payments p
                JOIN debts d ON d.id = p.debt_id
                WHERE d.group_id = ?
                """)).isEqualTo(payments);
        assertThat(scalar("SELECT count(*) FROM group_activity WHERE group_id = ?")).isEqualTo(activity);
        assertThat(jdbc.queryForList(
                "SELECT id, status, paid_amount FROM debts WHERE group_id = ? ORDER BY id", groupId))
                .isEqualTo(debtRows);
        assertThat(scalar("""
                SELECT count(*) FROM group_activity
                WHERE group_id = ? AND event_type = 'LIQUIDATION_PROPOSED'
                """)).isZero();
    }

    private Long scalar(String sql) {
        return jdbc.queryForObject(sql, Long.class, groupId);
    }

    @Test
    void aNonMemberGetsTheNeutral404() throws Exception {
        proposal(404, outsiderSession);
    }

    @Test
    void anUnauthenticatedCallerGets401() throws Exception {
        mockMvc.perform(get("/api/groups/" + groupId + "/settlement-proposal"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Necesitas iniciar sesión para continuar"));
    }
}
