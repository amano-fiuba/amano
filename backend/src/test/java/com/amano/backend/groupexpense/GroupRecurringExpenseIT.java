package com.amano.backend.groupexpense;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.amano.backend.expense.RecurrenceFrequency;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;

/** 028: listing and management of the recurring series of a group. */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@Testcontainers
@AutoConfigureMockMvc
class GroupRecurringExpenseIT {

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

    private long adminId;
    private long creatorId;
    private long otherId;
    private long outsiderId;
    private long groupId;
    private long seriesId;
    private long pausedId;

    @BeforeEach
    void setUp() {
        adminId = user("Admin");
        creatorId = user("Creador");
        otherId = user("Otro");
        outsiderId = user("Ajeno");
        groupId = jdbc.queryForObject("""
                INSERT INTO groups (name, primary_currency, debt_due_days)
                VALUES (?, 'ARS', 7) RETURNING id
                """, Long.class, "Grupo " + UUID.randomUUID());
        member(adminId, "ADMIN");
        member(creatorId, "MEMBER");
        member(otherId, "MEMBER");
        seriesId = series("ACTIVE", "WEEKLY", "2026-11-02", "Alquiler");
        pausedId = series("PAUSED", "MONTHLY", "2026-11-05", "Internet");
        jdbc.update("""
                INSERT INTO group_expenses (group_id, created_by_user_id, payer_user_id, amount, currency,
                    expense_date, description, idempotency_key, expense_type, category, payment_method)
                VALUES (?, ?, ?, 50.00, 'ARS', DATE '2026-11-01', 'Único', gen_random_uuid(),
                    'ONE_TIME', 'FOOD', 'CASH')
                """, groupId, creatorId, creatorId);
    }

    private long user(String name) {
        return jdbc.queryForObject("""
                INSERT INTO users (first_name, last_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES (?, 'Test', ?, 'x', now(), now()) RETURNING id
                """, Long.class, name, "recurring" + UUID.randomUUID() + "@example.com");
    }

    private void member(long userId, String role) {
        jdbc.update("INSERT INTO group_members (user_id, group_id, role) VALUES (?, ?, ?)",
                userId, groupId, role);
    }

    private long series(String status, String frequency, String date, String description) {
        return jdbc.queryForObject("""
                INSERT INTO group_expenses (group_id, created_by_user_id, payer_user_id, amount, currency,
                    expense_date, description, idempotency_key, expense_type, category, payment_method,
                    recurrence_frequency, recurrence_status, recurrence_next_date)
                VALUES (?, ?, ?, 100.00, 'ARS', ?, ?, gen_random_uuid(), 'RECURRING', 'FOOD', 'CASH',
                    ?, ?, ?)
                RETURNING id
                """, Long.class, groupId, creatorId, creatorId, java.sql.Date.valueOf(date), description,
                frequency, status, java.sql.Date.valueOf(date));
    }

    private Cookie session(long userId) {
        return new Cookie("amano_auth", jwtService.issueToken(userId));
    }

    private String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }

    @Test
    void listsOnlyTheSeriesOfTheGroupWithTheirState() throws Exception {
        mockMvc.perform(get("/api/groups/{id}/recurring-expenses", groupId).cookie(session(otherId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].frequency").value("MONTHLY"))
                .andExpect(jsonPath("$[0].status").value("PAUSED"))
                .andExpect(jsonPath("$[0].category").value("FOOD"))
                .andExpect(jsonPath("$[0].paymentMethod").value("CASH"))
                .andExpect(jsonPath("$[1].frequency").value("WEEKLY"))
                .andExpect(jsonPath("$[1].status").value("ACTIVE"));
    }

    @Test
    void aNonMemberCannotList() throws Exception {
        mockMvc.perform(get("/api/groups/{id}/recurring-expenses", groupId).cookie(session(outsiderId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void theCreatorCanPauseAndResumeAndTheNextDateIsRecomputed() throws Exception {
        mockMvc.perform(patch("/api/groups/{id}/recurring-expenses/{expenseId}", groupId, seriesId)
                        .cookie(session(creatorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "PAUSED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"))
                .andExpect(jsonPath("$.nextOccurrenceDate").value("2026-11-02"));

        LocalDate expected = RecurrenceFrequency.WEEKLY.firstOnOrAfter(LocalDate.of(2026, 11, 2),
                LocalDate.now(ZONE));
        mockMvc.perform(patch("/api/groups/{id}/recurring-expenses/{expenseId}", groupId, seriesId)
                        .cookie(session(creatorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.nextOccurrenceDate").value(expected.toString()));
    }

    @Test
    void anAdminCanChangeTheFrequencyOfAnActiveSeries() throws Exception {
        LocalDate expected = RecurrenceFrequency.MONTHLY.firstOnOrAfter(LocalDate.of(2026, 11, 2),
                LocalDate.now(ZONE));
        mockMvc.perform(patch("/api/groups/{id}/recurring-expenses/{expenseId}", groupId, seriesId)
                        .cookie(session(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("frequency", "MONTHLY"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frequency").value("MONTHLY"))
                .andExpect(jsonPath("$.nextOccurrenceDate").value(expected.toString()));
    }

    @Test
    void anotherMemberCannotChangeTheSeries() throws Exception {
        mockMvc.perform(patch("/api/groups/{id}/recurring-expenses/{expenseId}", groupId, seriesId)
                        .cookie(session(otherId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "PAUSED"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidStatusOrFrequencyIsRejected() throws Exception {
        mockMvc.perform(patch("/api/groups/{id}/recurring-expenses/{expenseId}", groupId, seriesId)
                        .cookie(session(creatorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "FINISHED"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.status")
                        .value(GroupRecurringExpenseService.RECURRENCE_STATUS_INVALID));

        mockMvc.perform(patch("/api/groups/{id}/recurring-expenses/{expenseId}", groupId, seriesId)
                        .cookie(session(creatorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("frequency", "DAILY"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.frequency")
                        .value(GroupRecurringExpenseService.RECURRENCE_FREQUENCY_INVALID));
    }

    @Test
    void anEmptyBodyIsRejected() throws Exception {
        mockMvc.perform(patch("/api/groups/{id}/recurring-expenses/{expenseId}", groupId, seriesId)
                        .cookie(session(creatorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.body")
                        .value(GroupRecurringExpenseService.RECURRENCE_EDIT_EMPTY));
    }

    @Test
    void anOccurrenceIsNotASeriesAndAnswersNotFound() throws Exception {
        long occurrenceId = jdbc.queryForObject("""
                INSERT INTO group_expenses (group_id, created_by_user_id, payer_user_id, amount, currency,
                    expense_date, description, idempotency_key, expense_type, category, payment_method,
                    recurrence_source_id)
                VALUES (?, ?, ?, 100.00, 'ARS', DATE '2026-11-09', 'Ocurrencia', gen_random_uuid(),
                    'RECURRING', 'FOOD', 'CASH', ?)
                RETURNING id
                """, Long.class, groupId, creatorId, creatorId, seriesId);

        mockMvc.perform(patch("/api/groups/{id}/recurring-expenses/{expenseId}", groupId, occurrenceId)
                        .cookie(session(creatorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "PAUSED"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void pausingKeepsTheStatusAndNextDateConsistentWithTheDatabaseCheck() throws Exception {
        mockMvc.perform(patch("/api/groups/{id}/recurring-expenses/{expenseId}", groupId, pausedId)
                        .cookie(session(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("frequency", "YEARLY"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frequency").value("YEARLY"))
                .andExpect(jsonPath("$.status").value("PAUSED"));
    }
}
