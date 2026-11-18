package com.amano.backend.income;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.amano.backend.config.JwtService;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.amano.backend.support.AbstractIntegrationIT;

/**
 * Personal incomes never touch the groups (specs/033-income-management, FR-014, SC-006):
 * the balances, the settlement proposal and the pending debts of a group answer exactly
 * the same before and after its members register incomes, and no group or debt row
 * changes.
 */
class IncomeGroupIsolationIT extends AbstractIntegrationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private long user(String first, String email) {
        return jdbc.queryForObject("""
                INSERT INTO users (first_name, last_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES (?, 'Prueba', ?, 'x', now(), now()) RETURNING id
                """, Long.class, first, email);
    }

    private JsonNode read(Cookie session, String path) throws Exception {
        String body = mockMvc.perform(get(path).cookie(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private void registerIncome(Cookie session, String amount, String currency) throws Exception {
        mockMvc.perform(post("/api/incomes").cookie(session)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", amount,
                                "currency", currency,
                                "date", LocalDate.now(ZoneOffset.UTC).minusDays(1).toString(),
                                "category", "SALARY",
                                "paymentMethod", "TRANSFER"))))
                .andExpect(status().isCreated());
    }

    private List<Map<String, Object>> groupTables() {
        return jdbc.queryForList("""
                SELECT (SELECT COUNT(*) FROM groups) AS groups,
                       (SELECT COUNT(*) FROM debts) AS debts,
                       (SELECT COUNT(*) FROM group_expenses) AS expenses,
                       (SELECT COUNT(*) FROM expenses) AS personal_expenses,
                       (SELECT COALESCE(SUM(amount), 0) FROM debts) AS debt_total
                """);
    }

    @Test
    void groupBalancesSettlementAndDebtsDoNotChangeWhenMembersRegisterIncomes() throws Exception {
        int n = (int) (System.nanoTime() % 1_000_000);
        long ana = user("Ana", "ana" + n + "@aislamiento.dev");
        long juan = user("Juan", "juan" + n + "@aislamiento.dev");
        jdbc.update("""
                INSERT INTO friendship_requests (sender_id, recipient_id, status, responded_at)
                VALUES (?, ?, 'ACCEPTED', now())
                """, ana, juan);
        Cookie anaSession = new Cookie("amano_auth", jwtService.issueToken(ana));
        Cookie juanSession = new Cookie("amano_auth", jwtService.issueToken(juan));

        String created = mockMvc.perform(post("/api/groups").cookie(anaSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Depto\", \"member\": {\"memberType\": \"REGISTERED\","
                                + " \"identifier\": \"juan" + n + "@aislamiento.dev\"}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long groupId = objectMapper.readTree(created).get("id").asLong();
        mockMvc.perform(post("/api/groups/" + groupId + "/expenses").cookie(anaSession)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": "10000.00", "currency": "ARS", "date": "2026-11-05",
                                 "description": "Súper", "deviceTimeZone": "America/Argentina/Buenos_Aires",
                                 "category": "FOOD", "type": "ONE_TIME", "paymentMethod": "CASH",
                                 "payerUserId": %d,
                                 "participants": [{"type": "REGISTERED", "userId": %d},
                                                  {"type": "REGISTERED", "userId": %d}]}
                                """.formatted(ana, ana, juan)))
                .andExpect(status().isCreated());

        String balances = "/api/groups/" + groupId + "/balances";
        String settlement = "/api/groups/" + groupId + "/settlement-proposal";
        String pending = "/api/groups/" + groupId + "/debts/pending";
        JsonNode balancesBefore = read(anaSession, balances);
        JsonNode settlementBefore = read(anaSession, settlement);
        JsonNode pendingBefore = read(juanSession, pending);
        JsonNode juanBalancesBefore = read(juanSession, balances);
        List<Map<String, Object>> tablesBefore = groupTables();

        registerIncome(anaSession, "850000.00", "ARS");
        registerIncome(anaSession, "100.00", "USD");
        registerIncome(juanSession, "1500000.00", "ARS");

        assertThat(read(anaSession, balances)).isEqualTo(balancesBefore);
        assertThat(read(anaSession, settlement)).isEqualTo(settlementBefore);
        assertThat(read(juanSession, pending)).isEqualTo(pendingBefore);
        assertThat(read(juanSession, balances)).isEqualTo(juanBalancesBefore);
        assertThat(groupTables()).isEqualTo(tablesBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM incomes WHERE user_id IN (?, ?)",
                Integer.class, ana, juan)).isEqualTo(3);
    }
}
