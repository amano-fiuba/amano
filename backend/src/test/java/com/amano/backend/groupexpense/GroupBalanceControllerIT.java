package com.amano.backend.groupexpense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.amano.backend.config.JwtService;
import com.amano.backend.support.AbstractIntegrationIT;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 022 T012-T016 (US3): HTTP contract of {@code GET /api/groups/{id}/balances}.
 * Ana (ADMIN) pays $ 10.000 for herself, Juan (MEMBER) and the simulated
 * profile Abuela, so each of them owes 3.333,33. Balances are derived from the
 * debts, grouped by debt currency and sum to zero per currency.
 */
class GroupBalanceControllerIT extends AbstractIntegrationIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String EXPENSE_DATE = "2026-11-05";

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
    private long outsider;
    private Cookie anaSession;
    private Cookie juanSession;
    private Cookie outsiderSession;
    private long groupId;
    private long abuela;
    private long juanDebt;
    private long abuelaDebt;

    @BeforeEach
    void buildGroup() throws Exception {
        int n = SEQUENCE.incrementAndGet() + (int) (System.nanoTime() % 100000);
        ana = user("Ana", "López", "ana" + n + "@balances.dev");
        juan = user("Juan", "Pérez", "juan" + n + "@balances.dev");
        outsider = user("Otra", "Persona", "otra" + n + "@balances.dev");
        jdbc.update("""
                INSERT INTO friendship_requests (sender_id, recipient_id, status, responded_at)
                VALUES (?, ?, 'ACCEPTED', now())
                """, ana, juan);
        anaSession = new Cookie("amano_auth", jwtService.issueToken(ana));
        juanSession = new Cookie("amano_auth", jwtService.issueToken(juan));
        outsiderSession = new Cookie("amano_auth", jwtService.issueToken(outsider));

        String created = mockMvc.perform(post("/api/groups").cookie(anaSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Depto\", \"member\": {\"memberType\": \"REGISTERED\","
                        + " \"identifier\": \"juan" + n + "@balances.dev\"}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        groupId = objectMapper.readTree(created).get("id").asLong();
        mockMvc.perform(post("/api/groups/" + groupId + "/members").cookie(anaSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberType\": \"SIMULATED\", \"identifier\": \"abuela" + n + "@balances.dev\","
                        + " \"name\": \"Abuela\"}"))
                .andExpect(status().isCreated());
        abuela = jdbc.queryForObject(
                "SELECT id FROM simulated_profiles WHERE group_id = ?", Long.class, groupId);

        String expense = mockMvc.perform(post("/api/groups/" + groupId + "/expenses")
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
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode expenseJson = objectMapper.readTree(expense);
        assertThat(expenseJson.get("debts")).hasSize(2);
        juanDebt = jdbc.queryForObject(
                "SELECT id FROM debts WHERE group_id = ? AND debtor_user_id = ?", Long.class, groupId, juan);
        abuelaDebt = jdbc.queryForObject(
                "SELECT id FROM debts WHERE group_id = ? AND debtor_simulated_profile_id = ?",
                Long.class, groupId, abuela);
    }

    private long user(String first, String last, String email) {
        return jdbc.queryForObject("""
                INSERT INTO users (first_name, last_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES (?, ?, ?, 'x', now(), now()) RETURNING id
                """, Long.class, first, last, email);
    }

    private void pay(Cookie session, long debtId, String amount) throws Exception {
        mockMvc.perform(post("/api/payments").cookie(session)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"debtId": %d, "amount": "%s", "date": "%s",
                         "deviceTimeZone": "America/Argentina/Buenos_Aires"}
                        """.formatted(debtId, amount, EXPENSE_DATE)))
                .andExpect(status().isCreated());
    }

    private JsonNode balances(int expectedStatus, Cookie session) throws Exception {
        var request = get("/api/groups/" + groupId + "/balances");
        if (session != null) {
            request.cookie(session);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().is(expectedStatus)).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static JsonNode currency(JsonNode body, String currency) {
        for (JsonNode node : body.get("currencies")) {
            if (currency.equals(node.get("currency").asString())) {
                return node;
            }
        }
        throw new AssertionError("La moneda " + currency + " no está en la respuesta: " + body);
    }

    private static JsonNode member(JsonNode currency, long memberId, String type) {
        for (JsonNode node : currency.get("balances")) {
            if (node.get("memberId").asLong() == memberId && type.equals(node.get("memberType").asString())) {
                return node;
            }
        }
        throw new AssertionError("El miembro " + memberId + " (" + type + ") no está en la respuesta: " + currency);
    }

    private static BigDecimal total(JsonNode currency) {
        return new BigDecimal(currency.get("total").asString());
    }

    @Test
    void theEndpointsReturnsTheNetBalanceOfEveryMemberIncludingTheSimulatedOne() throws Exception {
        JsonNode body = balances(200, anaSession);

        assertThat(body.get("groupId").asLong()).isEqualTo(groupId);
        assertThat(body.get("primaryCurrency").asString()).isEqualTo("ARS");
        assertThat(body.get("currencies")).hasSize(1);
        JsonNode ars = currency(body, "ARS");
        assertThat(total(ars)).isEqualByComparingTo("0.00");
        assertThat(ars.get("balances")).hasSize(3);

        JsonNode anaBalance = member(ars, ana, "REGISTERED");
        assertThat(anaBalance.get("memberName").asString()).isEqualTo("Ana López");
        assertThat(new BigDecimal(anaBalance.get("netBalance").asString())).isEqualByComparingTo("6666.66");

        JsonNode juanBalance = member(ars, juan, "REGISTERED");
        assertThat(juanBalance.get("memberName").asString()).isEqualTo("Juan Pérez");
        assertThat(new BigDecimal(juanBalance.get("netBalance").asString())).isEqualByComparingTo("-3333.33");

        JsonNode abuelaBalance = member(ars, abuela, "SIMULATED");
        assertThat(abuelaBalance.get("memberName").asString()).isEqualTo("Abuela");
        assertThat(new BigDecimal(abuelaBalance.get("netBalance").asString())).isEqualByComparingTo("-3333.33");

        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode node : ars.get("balances")) {
            sum = sum.add(new BigDecimal(node.get("netBalance").asString()));
        }
        assertThat(sum).isEqualByComparingTo("0.00");
    }

    @Test
    void balancesStillSumToZeroAfterAPaymentWithoutValidation() throws Exception {
        pay(juanSession, juanDebt, "1000.00");

        JsonNode ars = currency(balances(200, anaSession), "ARS");
        assertThat(total(ars)).isEqualByComparingTo("0.00");
        assertThat(new BigDecimal(member(ars, juan, "REGISTERED").get("netBalance").asString()))
                .isEqualByComparingTo("-2333.33");
        assertThat(new BigDecimal(member(ars, ana, "REGISTERED").get("netBalance").asString()))
                .isEqualByComparingTo("5666.66");

        pay(juanSession, juanDebt, "2333.33");

        ars = currency(balances(200, anaSession), "ARS");
        assertThat(total(ars)).isEqualByComparingTo("0.00");
        assertThat(new BigDecimal(member(ars, juan, "REGISTERED").get("netBalance").asString()))
                .isEqualByComparingTo("0.00");
        assertThat(new BigDecimal(member(ars, ana, "REGISTERED").get("netBalance").asString()))
                .isEqualByComparingTo("3333.33");
        assertThat(new BigDecimal(member(ars, abuela, "SIMULATED").get("netBalance").asString()))
                .isEqualByComparingTo("-3333.33");
    }

    /**
     * T014: with "Pagos requiere validación" the balances must also stay
     * zero-sum after a payment. The 018 flow used here still marks the debt
     * PAID because the 022 report/verify flow (US5/US6) is not implemented yet;
     * the checked invariant is the zero-sum balance, which holds either way.
     */
    @Test
    void balancesStillSumToZeroAfterAPaymentWithValidation() throws Exception {
        mockMvc.perform(put("/api/groups/" + groupId).cookie(anaSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissions\": {\"paymentsRequireValidation\": true}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.paymentsRequireValidation").value(true));

        pay(juanSession, juanDebt, "1000.00");

        // Rewritten for 022 T085: the inherited endpoint now respects the validation setting, so the
        // payment is only REPORTED and the balances do not move until an administrator confirms it.
        JsonNode ars = currency(balances(200, anaSession), "ARS");
        assertThat(total(ars)).isEqualByComparingTo("0.00");
        assertThat(new BigDecimal(member(ars, juan, "REGISTERED").get("netBalance").asString()))
                .isEqualByComparingTo("-3333.33");

        long paymentId = jdbc.queryForObject("SELECT id FROM payments WHERE debt_id = ?", Long.class, juanDebt);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/groups/" + groupId + "/payments/" + paymentId + "/confirm").cookie(anaSession))
                .andExpect(status().isOk());

        ars = currency(balances(200, anaSession), "ARS");
        assertThat(total(ars)).isEqualByComparingTo("0.00");
        assertThat(new BigDecimal(member(ars, juan, "REGISTERED").get("netBalance").asString()))
                .isEqualByComparingTo("-2333.33");
        assertThat(new BigDecimal(member(ars, ana, "REGISTERED").get("netBalance").asString()))
                .isEqualByComparingTo("5666.66");
        assertThat(new BigDecimal(member(ars, abuela, "SIMULATED").get("netBalance").asString()))
                .isEqualByComparingTo("-3333.33");
    }

    @Test
    void aReportedPaymentStillCountsAsBalanceUntilItIsVerified() throws Exception {
        // The 022 payment endpoint that leaves the debt PAYMENT_REPORTED belongs to
        // US6; this test prepares that state directly to check the balance rule.
        jdbc.update("UPDATE debts SET status = 'PAYMENT_REPORTED', paid_amount = 0 WHERE id = ?", juanDebt);

        JsonNode ars = currency(balances(200, anaSession), "ARS");
        assertThat(total(ars)).isEqualByComparingTo("0.00");
        assertThat(new BigDecimal(member(ars, juan, "REGISTERED").get("netBalance").asString()))
                .isEqualByComparingTo("-3333.33");

        jdbc.update("UPDATE debts SET status = 'VERIFIED', paid_amount = amount WHERE id = ?", juanDebt);

        ars = currency(balances(200, anaSession), "ARS");
        assertThat(total(ars)).isEqualByComparingTo("0.00");
        assertThat(new BigDecimal(member(ars, juan, "REGISTERED").get("netBalance").asString()))
                .isEqualByComparingTo("0.00");
        assertThat(new BigDecimal(member(ars, ana, "REGISTERED").get("netBalance").asString()))
                .isEqualByComparingTo("3333.33");
    }

    @Test
    void debtsInDifferentCurrenciesAreReportedInSeparateGroups() throws Exception {
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

        JsonNode body = balances(200, anaSession);

        assertThat(body.get("currencies")).hasSize(2);
        JsonNode ars = currency(body, "ARS");
        JsonNode usd = currency(body, "USD");
        assertThat(total(ars)).isEqualByComparingTo("0.00");
        assertThat(total(usd)).isEqualByComparingTo("0.00");
        assertThat(new BigDecimal(member(ars, juan, "REGISTERED").get("netBalance").asString()))
                .isEqualByComparingTo("-3333.33");
        assertThat(new BigDecimal(member(usd, juan, "REGISTERED").get("netBalance").asString()))
                .isEqualByComparingTo("-10.00");
        assertThat(new BigDecimal(member(usd, ana, "REGISTERED").get("netBalance").asString()))
                .isEqualByComparingTo("10.00");
    }

    @Test
    void aNonMemberGetsTheNeutral404() throws Exception {
        balances(404, outsiderSession);
    }

    @Test
    void anUnauthenticatedCallerGets401() throws Exception {
        mockMvc.perform(get("/api/groups/" + groupId + "/balances"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Necesitas iniciar sesión para continuar"));
    }
}
