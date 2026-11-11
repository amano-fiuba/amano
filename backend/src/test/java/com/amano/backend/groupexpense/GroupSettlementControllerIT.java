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
                         "deviceTimeZone": "America/Argentina/Buenos_Aires", "category": "FOOD", "type": "ONE_TIME", "paymentMethod": "CASH", "payerUserId": %d,
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
    void theProposalIsReadOnlyForExpensesDebtsAndPaymentsAndRecordsLiquidationProposed() throws Exception {
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
        proposal(200, anaSession); // second call on the same day must not create duplicate noise

        assertThat(scalar("SELECT count(*) FROM group_expenses WHERE group_id = ?")).isEqualTo(expenses);
        assertThat(scalar("SELECT count(*) FROM debts WHERE group_id = ?")).isEqualTo(debts);
        assertThat(scalar("""
                SELECT count(*) FROM payments p
                JOIN debts d ON d.id = p.debt_id
                WHERE d.group_id = ?
                """)).isEqualTo(payments);
        assertThat(scalar("SELECT count(*) FROM group_activity WHERE group_id = ?")).isEqualTo(activity + 1);
        assertThat(jdbc.queryForList(
                "SELECT id, status, paid_amount FROM debts WHERE group_id = ? ORDER BY id", groupId))
                .isEqualTo(debtRows);
        assertThat(scalar("""
                SELECT count(*) FROM group_activity
                WHERE group_id = ? AND event_type = 'LIQUIDATION_PROPOSED'
                """)).isEqualTo(1);

        mockMvc.perform(get("/api/groups/" + groupId + "/activity").cookie(anaSession)
                .param("eventType", "LIQUIDATION_PROPOSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].eventType").value("LIQUIDATION_PROPOSED"))
                .andExpect(jsonPath("$.content[0].actor.id").value(ana))
                .andExpect(jsonPath("$.content[0].details.transferCount").value(3))
                .andExpect(jsonPath("$.content[0].details.currency").value("ARS"));

        // Move the previous event to yesterday: a new call on the next day records a new event
        jdbc.update("""
                UPDATE group_activity
                SET created_at = created_at - INTERVAL '2 days'
                WHERE group_id = ? AND event_type = 'LIQUIDATION_PROPOSED'
                """, groupId);
        proposal(200, anaSession);
        assertThat(scalar("""
                SELECT count(*) FROM group_activity
                WHERE group_id = ? AND event_type = 'LIQUIDATION_PROPOSED'
                """)).isEqualTo(2);
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

    private long createGroupWithMembers(String name, String secondEmail, String thirdEmail) throws Exception {
        String created = mockMvc.perform(post("/api/groups").cookie(anaSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"" + name + "\", \"member\": {\"memberType\": \"REGISTERED\","
                        + " \"identifier\": \"" + secondEmail + "\"}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long gid = objectMapper.readTree(created).get("id").asLong();
        if (thirdEmail != null) {
            mockMvc.perform(post("/api/groups/" + gid + "/members").cookie(anaSession)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"memberType\": \"REGISTERED\", \"identifier\": \"" + thirdEmail + "\"}"))
                    .andExpect(status().isCreated());
        }
        return gid;
    }

    private void insertDirectDebt(long gid, long debtorId, String debtorName, long creditorId,
            String amount, String paidAmount, String status, String currency) {
        long expenseId = jdbc.queryForObject("""
                INSERT INTO group_expenses (group_id, created_by_user_id, payer_user_id, amount, currency,
                    expense_date, description, idempotency_key)
                VALUES (?, ?, ?, ?::numeric, ?, ?, 'Gasto directo', gen_random_uuid())
                RETURNING id
                """, Long.class, gid, creditorId, creditorId, amount, currency,
                java.sql.Date.valueOf(EXPENSE_DATE));
        long shareId = jdbc.queryForObject("""
                INSERT INTO group_expense_shares (expense_id, position, participant_type, user_id,
                    participant_name, amount)
                VALUES (?, 0, 'REGISTERED', ?, ?, ?::numeric)
                RETURNING id
                """, Long.class, expenseId, debtorId, debtorName, amount);
        jdbc.update("""
                INSERT INTO debts (group_id, expense_id, share_id, debtor_type, debtor_user_id, debtor_name,
                    creditor_user_id, currency, amount, paid_amount, status)
                VALUES (?, ?, ?, 'REGISTERED', ?, ?, ?, ?, ?::numeric, ?::numeric, ?)
                """, gid, expenseId, shareId, debtorId, debtorName, creditorId, currency,
                amount, paidAmount, status);
    }

    private JsonNode proposalForGroup(long gid, Cookie session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/groups/" + gid + "/settlement-proposal").cookie(session))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode pendingDebtsForGroup(long gid, Cookie session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/groups/" + gid + "/debts/pending").cookie(session))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /**
     * T086 (a), (c), (e), (h): cross-debts between two members (multiple debts in
     * both directions and a confirmed partial payment) are compensated in the
     * proposal using the outstanding balance, while the original debts remain
     * untouched and {@code GET /debts/pending} still lists them gross.
     */
    @Test
    void t086CrossDebtsBetweenTwoMembersCompensateInProposalWhilePendingDebtsStayGross() throws Exception {
        int n = SEQUENCE.incrementAndGet() + (int) (System.nanoTime() % 100000);
        String juanEmail = jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, juan);
        long pairGroup = createGroupWithMembers("Par " + n, juanEmail, null);

        // Ana owes Juan 60 + 40 (= 100), Juan owes Ana 30 + 20 (= 50)
        insertDirectDebt(pairGroup, ana, "Ana López", juan, "60.00", "0.00", "PENDING", "ARS");
        insertDirectDebt(pairGroup, ana, "Ana López", juan, "40.00", "0.00", "PENDING", "ARS");
        insertDirectDebt(pairGroup, juan, "Juan Pérez", ana, "30.00", "0.00", "PENDING", "ARS");
        insertDirectDebt(pairGroup, juan, "Juan Pérez", ana, "20.00", "0.00", "PENDING", "ARS");

        JsonNode body = proposalForGroup(pairGroup, anaSession);
        JsonNode ars = currency(body, "ARS");
        assertThat(ars.get("transfers")).hasSize(1);
        JsonNode onlyTransfer = ars.get("transfers").get(0);
        assertThat(onlyTransfer.get("fromMemberId").asLong()).isEqualTo(ana);
        assertThat(onlyTransfer.get("toMemberId").asLong()).isEqualTo(juan);
        assertThat(amount(onlyTransfer, "amount")).isEqualByComparingTo("50.00");
        assertThat(ars.get("summary").get("transferCount").asInt()).isEqualTo(1);
        assertThat(amount(ars.get("summary"), "totalTransferred")).isEqualByComparingTo("50.00");
        assertThat(amount(ars.get("summary"), "totalNetBalances")).isEqualByComparingTo("50.00");

        // (h) Original debts are unchanged and GET /debts/pending still returns all 4 gross debts
        JsonNode pending = pendingDebtsForGroup(pairGroup, anaSession);
        assertThat(pending).hasSize(4);
        BigDecimal grossOutstandingSum = BigDecimal.ZERO;
        for (JsonNode debt : pending) {
            grossOutstandingSum = grossOutstandingSum.add(amount(debt, "outstanding"));
        }
        assertThat(grossOutstandingSum).isEqualByComparingTo("150.00");

        // (e) Confirmed partial payment of 15.00 on Ana's 60.00 debt reduces outstanding to 45.00,
        // so Ana now owes 45 + 40 = 85 vs Juan's 50 → compensated transfer becomes 35.00
        jdbc.update("""
                UPDATE debts SET paid_amount = 15.00, status = 'PARTIALLY_PAID'
                WHERE group_id = ? AND debtor_user_id = ? AND amount = 60.00
                """, pairGroup, ana);
        JsonNode afterPartial = currency(proposalForGroup(pairGroup, anaSession), "ARS");
        assertThat(afterPartial.get("transfers")).hasSize(1);
        assertThat(amount(afterPartial.get("transfers").get(0), "amount")).isEqualByComparingTo("35.00");
    }

    /**
     * T086 (b): when cross-debts between two members cancel out exactly (100 vs 100),
     * the proposal emits no transfers for that currency.
     */
    @Test
    void t086EqualCrossDebtsBetweenTwoMembersProduceEmptyProposal() throws Exception {
        int n = SEQUENCE.incrementAndGet() + (int) (System.nanoTime() % 100000);
        String juanEmail = jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, juan);
        long pairGroup = createGroupWithMembers("Empate " + n, juanEmail, null);

        insertDirectDebt(pairGroup, ana, "Ana López", juan, "100.00", "0.00", "PENDING", "ARS");
        insertDirectDebt(pairGroup, juan, "Juan Pérez", ana, "100.00", "0.00", "PENDING", "ARS");

        JsonNode body = proposalForGroup(pairGroup, anaSession);
        assertThat(body.get("proposals")).isEmpty();
        assertThat(pendingDebtsForGroup(pairGroup, anaSession)).hasSize(2);
    }

    /**
     * T086 (g): two groups with the same members (Ana = A, Juan = B, Carla = C).
     * Group 1: A owes B 100, B owes A 50 → proposal is A → B 50.00.
     * Group 2: C owes A 40, B owes A 25, A owes C 20 → proposal is B → A 25.00 and C → A 20.00.
     * Debts are never compensated across groups.
     */
    @Test
    void t086TwoGroupsWithTheSameMembersNeverCompensateDebtsAcrossGroups() throws Exception {
        int n = SEQUENCE.incrementAndGet() + (int) (System.nanoTime() % 100000);
        String carlaEmail = "carla" + n + "@settlement.dev";
        long carla = user("Carla", "Gómez", carlaEmail);
        jdbc.update("""
                INSERT INTO friendship_requests (sender_id, recipient_id, status, responded_at)
                VALUES (?, ?, 'ACCEPTED', now())
                """, ana, carla);
        String juanEmail = jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, juan);

        long group1 = createGroupWithMembers("Grupo 1 " + n, juanEmail, carlaEmail);
        long group2 = createGroupWithMembers("Grupo 2 " + n, juanEmail, carlaEmail);

        // Group 1: A (ana) owes B (juan) 100, B owes A 50
        insertDirectDebt(group1, ana, "Ana López", juan, "100.00", "0.00", "PENDING", "ARS");
        insertDirectDebt(group1, juan, "Juan Pérez", ana, "50.00", "0.00", "PENDING", "ARS");

        // Group 2: C (carla) owes A (ana) 40, B (juan) owes A 25, A owes C 20
        insertDirectDebt(group2, carla, "Carla Gómez", ana, "40.00", "0.00", "PENDING", "ARS");
        insertDirectDebt(group2, juan, "Juan Pérez", ana, "25.00", "0.00", "PENDING", "ARS");
        insertDirectDebt(group2, ana, "Ana López", carla, "20.00", "0.00", "PENDING", "ARS");

        JsonNode g1Ars = currency(proposalForGroup(group1, anaSession), "ARS");
        assertThat(g1Ars.get("transfers")).hasSize(1);
        JsonNode aToB = transfer(g1Ars, ana, "REGISTERED");
        assertThat(aToB.get("toMemberId").asLong()).isEqualTo(juan);
        assertThat(amount(aToB, "amount")).isEqualByComparingTo("50.00");
        assertThat(g1Ars.get("summary").get("transferCount").asInt()).isEqualTo(1);
        assertThat(amount(g1Ars.get("summary"), "totalTransferred")).isEqualByComparingTo("50.00");

        JsonNode g2Ars = currency(proposalForGroup(group2, anaSession), "ARS");
        assertThat(g2Ars.get("transfers")).hasSize(2);
        JsonNode bToA = transfer(g2Ars, juan, "REGISTERED");
        assertThat(bToA.get("toMemberId").asLong()).isEqualTo(ana);
        assertThat(amount(bToA, "amount")).isEqualByComparingTo("25.00");
        JsonNode cToA = transfer(g2Ars, carla, "REGISTERED");
        assertThat(cToA.get("toMemberId").asLong()).isEqualTo(ana);
        assertThat(amount(cToA, "amount")).isEqualByComparingTo("20.00");
        assertThat(g2Ars.get("summary").get("transferCount").asInt()).isEqualTo(2);
        assertThat(amount(g2Ars.get("summary"), "totalTransferred")).isEqualByComparingTo("45.00");
        assertThat(amount(g2Ars.get("summary"), "totalNetBalances")).isEqualByComparingTo("45.00");
    }
}
