package com.amano.backend.groups;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.amano.backend.config.JwtService;
import com.amano.backend.support.AbstractIntegrationIT;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;

/**
 * 025 T023: contract tests of {@code GET/PUT /api/groups/{id}/reminder-settings}.
 * Covers the defaults, the partial update, the immediate reschedule of
 * unclaimed rows, the validation messages and the admin-only 403/404.
 */
class ReminderSettingsIT extends AbstractIntegrationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private long adminId;
    private long memberId;
    private long outsiderId;
    private long groupId;
    private long expenseId;
    private long debtId;

    @BeforeEach
    void setUp() {
        adminId = user("Admin", "Grupo");
        memberId = user("Miembro", "Común");
        outsiderId = user("Ajeno", "Total");
        groupId = jdbc.queryForObject("""
                INSERT INTO groups (name, primary_currency, debt_due_days)
                VALUES (?, 'ARS', 30) RETURNING id
                """, Long.class, "Grupo " + UUID.randomUUID());
        member(adminId, groupId, "ADMIN");
        member(memberId, groupId, "MEMBER");
        expenseId = expense(groupId, adminId, "2026-10-21", "Súper");
        debtId = debt(groupId, expenseId, memberId, adminId, "PENDING");
        scheduledReminder(groupId, debtId, "2026-11-15");
    }

    @Test
    void getReturnsTheDefaults() throws Exception {
        mockMvc.perform(get("/api/groups/{id}/reminder-settings", groupId).cookie(session(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sendDays").value(5))
                .andExpect(jsonPath("$.message").value(nullValue()))
                .andExpect(jsonPath("$.defaultSendDays").value(5))
                .andExpect(jsonPath("$.allowedSendDays[0]").value(7))
                .andExpect(jsonPath("$.allowedSendDays[1]").value(5))
                .andExpect(jsonPath("$.allowedSendDays[2]").value(3))
                .andExpect(jsonPath("$.allowedSendDays[3]").value(2))
                .andExpect(jsonPath("$.allowedSendDays[4]").value(1));
    }

    @Test
    void putSendDaysPersistsAndReschedulesUnclaimedRowsOnly() throws Exception {
        long otherExpense = expense(groupId, adminId, "2026-10-21", "Taxi");
        long sentDebt = debt(groupId, otherExpense, memberId, adminId, "PENDING");
        jdbc.update("""
                INSERT INTO reminders (group_id, kind, debt_id, send_date, status, sent_at, subject, body)
                VALUES (?, 'DEBT', ?, DATE '2026-11-15', 'SENT', now(), 'x', 'y')
                """, groupId, sentDebt);

        mockMvc.perform(put("/api/groups/{id}/reminder-settings", groupId)
                        .cookie(session(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sendDays", 7))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sendDays").value(7));

        assertThatSendDate(debtId, "2026-11-13");
        assertThatSendDate(sentDebt, "2026-11-15");
    }

    @Test
    void twoDaysBeforeIsAcceptedForEveryGroupAndReschedules() throws Exception {
        mockMvc.perform(put("/api/groups/{id}/reminder-settings", groupId)
                        .cookie(session(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sendDays", 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sendDays").value(2));

        assertThatSendDate(debtId, "2026-11-18");
    }

    @Test
    void changingTheGroupPaymentDaysDoesNotMoveExistingDeadlines() throws Exception {
        jdbc.update("UPDATE groups SET debt_due_days = 10 WHERE id = ?", groupId);

        mockMvc.perform(put("/api/groups/{id}/reminder-settings", groupId)
                        .cookie(session(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sendDays", 7))))
                .andExpect(status().isOk());

        // due_date de la fila sigue siendo 2026-11-20 (30 días al crear), no 2026-10-31.
        assertThatSendDate(debtId, "2026-11-13");
    }

    @Test
    void anEmptyBodyChangesNothing() throws Exception {
        mockMvc.perform(put("/api/groups/{id}/reminder-settings", groupId)
                        .cookie(session(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sendDays").value(5));

        assertThatSendDate(debtId, "2026-11-15");
    }

    @Test
    void aCustomMessageIsStoredTrimmedAndABlankOneBecomesNull() throws Exception {
        mockMvc.perform(put("/api/groups/{id}/reminder-settings", groupId)
                        .cookie(session(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("message", "  Pagá cuando puedas  "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Pagá cuando puedas"));

        mockMvc.perform(put("/api/groups/{id}/reminder-settings", groupId)
                        .cookie(session(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("message", "   "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(nullValue()));

        mockMvc.perform(get("/api/groups/{id}/reminder-settings", groupId).cookie(session(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(nullValue()));
    }

    @Test
    void aMessageLongerThan500CharactersIsRejected() throws Exception {
        mockMvc.perform(put("/api/groups/{id}/reminder-settings", groupId)
                        .cookie(session(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("message", "x".repeat(501)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.message")
                        .value("El mensaje no puede superar los 500 caracteres"));
    }

    @Test
    void anInvalidSendDaysIsRejected() throws Exception {
        mockMvc.perform(put("/api/groups/{id}/reminder-settings", groupId)
                        .cookie(session(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sendDays", 4))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.sendDays")
                        .value("El tiempo de envío debe ser 7, 5, 3, 2 o 1 días antes"));
    }

    @Test
    void aNonAdminMemberGetsForbidden() throws Exception {
        mockMvc.perform(get("/api/groups/{id}/reminder-settings", groupId).cookie(session(memberId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/groups/{id}/reminder-settings", groupId)
                        .cookie(session(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sendDays", 7))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aNonMemberOrUnknownGroupGetsNeutralNotFound() throws Exception {
        mockMvc.perform(get("/api/groups/{id}/reminder-settings", groupId).cookie(session(outsiderId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe el grupo solicitado o no sos miembro"));
        mockMvc.perform(get("/api/groups/{id}/reminder-settings", 999_999L).cookie(session(adminId)))
                .andExpect(status().isNotFound());
    }

    private Cookie session(long userId) {
        return new Cookie("amano_auth", jwtService.issueToken(userId));
    }

    private String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }

    private void assertThatSendDate(long debtId, String expected) {
        String actual = jdbc.queryForObject(
                "SELECT send_date FROM reminders WHERE debt_id = ? AND kind = 'DEBT'",
                java.sql.Date.class, debtId).toLocalDate().toString();
        org.assertj.core.api.Assertions.assertThat(actual).isEqualTo(expected);
    }

    private long user(String first, String last) {
        return jdbc.queryForObject("""
                INSERT INTO users (first_name, last_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES (?, ?, ?, 'x', now(), now()) RETURNING id
                """, Long.class, first, last, "settings" + UUID.randomUUID() + "@example.com");
    }

    private void member(long userId, long groupId, String role) {
        jdbc.update("INSERT INTO group_members (user_id, group_id, role) VALUES (?, ?, ?)",
                userId, groupId, role);
    }

    private long expense(long groupId, long payerId, String date, String description) {
        long expenseId = jdbc.queryForObject("""
                INSERT INTO group_expenses (group_id, created_by_user_id, payer_user_id, amount, currency,
                    expense_date, description, idempotency_key)
                VALUES (?, ?, ?, 100.00, 'ARS', ?, ?, gen_random_uuid()) RETURNING id
                """, Long.class, groupId, payerId, payerId, java.sql.Date.valueOf(date), description);
        jdbc.update("""
                UPDATE group_expenses e
                SET due_date = e.expense_date + g.debt_due_days
                FROM groups g
                WHERE g.id = e.group_id AND e.id = ? AND g.debt_due_days IS NOT NULL
                """, expenseId);
        return expenseId;
    }

    private long debt(long groupId, long expenseId, long debtorId, long creditorId, String status) {
        long shareId = jdbc.queryForObject("""
                INSERT INTO group_expense_shares (expense_id, position, participant_type, user_id,
                    participant_name, amount)
                VALUES (?, 0, 'REGISTERED', ?, 'Deudor', 100.00) RETURNING id
                """, Long.class, expenseId, debtorId);
        return jdbc.queryForObject("""
                INSERT INTO debts (group_id, expense_id, share_id, debtor_type, debtor_user_id, debtor_name,
                    creditor_user_id, currency, amount, paid_amount, status)
                VALUES (?, ?, ?, 'REGISTERED', ?, 'Deudor', ?, 'ARS', 100.00, 0.00, ?) RETURNING id
                """, Long.class, groupId, expenseId, shareId, debtorId, creditorId, status);
    }

    private void scheduledReminder(long groupId, long debtId, String sendDate) {
        jdbc.update("""
                INSERT INTO reminders (group_id, kind, debt_id, send_date, status)
                VALUES (?, 'DEBT', ?, ?, 'SCHEDULED')
                """, groupId, debtId, java.sql.Date.valueOf(sendDate));
    }
}
