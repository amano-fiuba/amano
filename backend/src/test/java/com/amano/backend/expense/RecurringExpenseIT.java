package com.amano.backend.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.amano.backend.config.JwtService;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;

/**
 * Feature 009 contract tests over a real PostgreSQL: recurring registration,
 * {@code GET /api/expenses/recurring}, {@code PATCH /api/expenses/{id}/status}
 * and the daily generation (invoked directly with the service's "today"). Covers
 * FR-001..FR-016, including the V5 constraints and the untouched 007 rows.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class RecurringExpenseIT {

    private static final String ZONE = "America/Argentina/Buenos_Aires";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecurringExpenseService recurringExpenseService;

    // ----- Helpers ------------------------------------------------------------

    private static MockPart part(String name, String value) {
        return new MockPart(name, value.getBytes(StandardCharsets.UTF_8));
    }

    /** Registers an account through the real endpoint, verifies it and returns its session. */
    private Cookie verifiedSession(String email) throws Exception {
        mockMvc.perform(multipart("/api/auth/register").part(
                        part("firstName", "Ana"), part("email", email),
                        part("password", "S3gura!2026"), part("confirmPassword", "S3gura!2026"),
                        part("acceptTerms", "true")))
                .andExpect(status().isCreated());
        jdbcTemplate.update("UPDATE users SET email_verified_at = CURRENT_TIMESTAMP WHERE email = ?", email);
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        return new Cookie("amano_auth", jwtService.issueToken(userId));
    }

    private LocalDate today() {
        return recurringExpenseService.today();
    }

    private static Map<String, String> payload(String type, String frequency, LocalDate date) {
        Map<String, String> payload = new HashMap<>(Map.of(
                "amount", "250000.00", "currency", "ARS", "date", date.toString(),
                "category", "HOUSING", "type", type, "paymentMethod", "TRANSFER",
                "description", "Alquiler", "deviceTimeZone", ZONE));
        if (frequency != null) {
            payload.put("frequency", frequency);
        }
        return payload;
    }

    private ResultActions create(Cookie session, Map<String, String> payload) throws Exception {
        return mockMvc.perform(post("/api/expenses")
                .cookie(session)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)));
    }

    private long createId(Cookie session, Map<String, String> payload) throws Exception {
        String body = create(session, payload).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private ResultActions changeStatus(Cookie session, long id, String status) throws Exception {
        return mockMvc.perform(patch("/api/expenses/" + id + "/status")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}"));
    }

    private Map<String, Object> recurrenceOf(long id) {
        return jdbcTemplate.queryForMap("""
                SELECT recurrence_frequency AS frequency, recurrence_status AS status,
                       recurrence_next_date::text AS next, recurrence_source_id AS source
                FROM expenses WHERE id = ?
                """, id);
    }

    private List<String> occurrenceDatesOf(long seriesId) {
        return jdbcTemplate.queryForList(
                "SELECT expense_date::text FROM expenses WHERE recurrence_source_id = ? ORDER BY expense_date",
                String.class, seriesId);
    }

    private void makeDue(long seriesId, LocalDate next) {
        jdbcTemplate.update("UPDATE expenses SET recurrence_next_date = ? WHERE id = ?", next, seriesId);
    }

    // ----- US1: registration --------------------------------------------------

    @Test
    void recurringExpenseIsRegisteredAsAnActiveSeriesAndListed() throws Exception {
        Cookie session = verifiedSession("serie.mensual@example.com");
        LocalDate today = today();

        create(session, payload("RECURRING", "MONTHLY", today))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("RECURRING"))
                .andExpect(jsonPath("$.frequency").value("MONTHLY"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.nextOccurrenceDate").value(today.plusMonths(1).toString()));
        create(session, payload("ONE_TIME", null, today))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.frequency").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.nextOccurrenceDate").doesNotExist());

        mockMvc.perform(get("/api/expenses/recurring").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].frequency").value("MONTHLY"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].description").value("Alquiler"));
    }

    @Test
    void frequencyRulesAreEnforcedWithoutPersisting() throws Exception {
        Cookie session = verifiedSession("serie.reglas@example.com");
        LocalDate today = today();

        create(session, payload("RECURRING", null, today))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.frequency")
                        .value("La frecuencia es obligatoria para un gasto recurrente"));
        create(session, payload("RECURRING", "DAILY", today))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.frequency").value("La frecuencia no es válida"));
        create(session, payload("ONE_TIME", "MONTHLY", today))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.frequency")
                        .value("Un gasto único no puede tener frecuencia"));

        Integer rows = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM expenses e JOIN users u ON u.id = e.user_id WHERE u.email = ?
                """, Integer.class, "serie.reglas@example.com");
        assertThat(rows).isZero();
    }

    @Test
    void pastStartDateIsNotRetroactive() throws Exception {
        Cookie session = verifiedSession("serie.pasada@example.com");
        LocalDate start = today().minusMonths(9);

        long id = createId(session, payload("RECURRING", "MONTHLY", start));
        recurringExpenseService.generateDueOccurrences(today());

        assertThat(LocalDate.parse((String) recurrenceOf(id).get("next"))).isAfter(today());
        assertThat(occurrenceDatesOf(id)).isEmpty();
    }

    // ----- US2/US3: generation, pause and resume ------------------------------

    @Test
    void pauseStopsTheGenerationAndResumeSkipsThePause() throws Exception {
        Cookie session = verifiedSession("serie.pausa@example.com");
        LocalDate today = today();
        long id = createId(session, payload("RECURRING", "WEEKLY", today.minusWeeks(2)));
        makeDue(id, today.minusWeeks(1));

        changeStatus(session, id, "PAUSED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"))
                .andExpect(jsonPath("$.nextOccurrenceDate").value(today.minusWeeks(1).toString()));
        recurringExpenseService.generateDueOccurrences(today);
        assertThat(occurrenceDatesOf(id)).isEmpty(); // FR-007: paused series generate nothing

        // Resume: the week of the pause is skipped and today is a series date.
        changeStatus(session, id, "ACTIVE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.nextOccurrenceDate").value(today.toString()));

        recurringExpenseService.generateDueOccurrences(today);
        recurringExpenseService.generateDueOccurrences(today); // FR-006: second run, no duplicate

        assertThat(occurrenceDatesOf(id)).containsExactly(today.toString());
        assertThat(recurrenceOf(id).get("next")).isEqualTo(today.plusWeeks(1).toString());
        Map<String, Object> occurrence = jdbcTemplate.queryForMap("""
                SELECT user_id, amount::text AS amount, category, expense_type, payment_method,
                       description, recurrence_frequency, recurrence_status
                FROM expenses WHERE recurrence_source_id = ?
                """, id);
        assertThat(occurrence.get("amount")).isEqualTo("250000.00");
        assertThat(occurrence.get("expense_type")).isEqualTo("RECURRING");
        assertThat(occurrence.get("category")).isEqualTo("HOUSING");
        assertThat(occurrence.get("description")).isEqualTo("Alquiler");
        assertThat(occurrence.get("recurrence_frequency")).isNull();
        assertThat(occurrence.get("recurrence_status")).isNull();
    }

    @Test
    void activeSeriesCatchesUpEveryDueDate() throws Exception {
        Cookie session = verifiedSession("serie.atraso@example.com");
        LocalDate today = today();
        long id = createId(session, payload("RECURRING", "WEEKLY", today.minusWeeks(3)));
        makeDue(id, today.minusWeeks(2));

        recurringExpenseService.generateDueOccurrences(today);

        assertThat(occurrenceDatesOf(id)).containsExactly(
                today.minusWeeks(2).toString(), today.minusWeeks(1).toString(), today.toString());
    }

    // ----- US4: finish --------------------------------------------------------

    @Test
    void finishIsFinalAndStopsTheGeneration() throws Exception {
        Cookie session = verifiedSession("serie.fin@example.com");
        LocalDate today = today();
        long id = createId(session, payload("RECURRING", "MONTHLY", today.minusMonths(1)));

        changeStatus(session, id, "FINISHED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.nextOccurrenceDate").doesNotExist());
        assertThat(recurrenceOf(id).get("next")).isNull();

        changeStatus(session, id, "FINISHED").andExpect(status().isOk()); // same state: no-op
        for (String target : List.of("ACTIVE", "PAUSED")) {
            changeStatus(session, id, target)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Gasto recurrente finalizado"))
                    .andExpect(jsonPath("$.detail")
                            .value("El gasto recurrente ya está finalizado y no puede modificarse"))
                    .andExpect(jsonPath("$.properties.code").value("RECURRENCE_FINISHED"));
        }

        // Even with its old next date restored, a FINISHED series is never selected.
        jdbcTemplate.update("UPDATE expenses SET recurrence_status = 'FINISHED' WHERE id = ?", id);
        recurringExpenseService.generateDueOccurrences(today);
        assertThat(occurrenceDatesOf(id)).isEmpty();
        assertThat(recurrenceOf(id).get("status")).isEqualTo("FINISHED");
        Integer original = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM expenses WHERE id = ?",
                Integer.class, id);
        assertThat(original).isEqualTo(1); // FR-009: the original expense is kept
    }

    // ----- FR-011/FR-016: ownership, kinds and errors -------------------------

    @Test
    void onlyTheOwnerSeriesCanBeChanged() throws Exception {
        Cookie owner = verifiedSession("serie.duena@example.com");
        Cookie stranger = verifiedSession("serie.ajena@example.com");
        LocalDate today = today();
        long series = createId(owner, payload("RECURRING", "WEEKLY", today.minusWeeks(1)));
        long oneTime = createId(owner, payload("ONE_TIME", null, today));
        makeDue(series, today);
        recurringExpenseService.generateDueOccurrences(today);
        long occurrence = jdbcTemplate.queryForObject(
                "SELECT id FROM expenses WHERE recurrence_source_id = ?", Long.class, series);

        for (long id : List.of(oneTime, occurrence, 999_999L)) {
            changeStatus(owner, id, "PAUSED")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("No se encontró el gasto recurrente"));
        }
        changeStatus(stranger, series, "PAUSED")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No se encontró el gasto recurrente"));
        mockMvc.perform(get("/api/expenses/recurring").cookie(stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        assertThat(recurrenceOf(series).get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void invalidStatusIsAFieldError() throws Exception {
        Cookie session = verifiedSession("serie.estado@example.com");
        long id = createId(session, payload("RECURRING", "YEARLY", today()));

        changeStatus(session, id, "STOPPED")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.status").value("El estado no es válido"));
        mockMvc.perform(patch("/api/expenses/" + id + "/status").cookie(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.status").value("El estado es obligatorio"));
    }

    @Test
    void endpointsRequireAVerifiedSession() throws Exception {
        mockMvc.perform(get("/api/expenses/recurring")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/expenses/1/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PAUSED\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ----- Actividad personal: GET /api/expenses --------------------------------

    @Test
    void expenseHistoryReturnsEveryExpenseOfTheCallerNewestFirst() throws Exception {
        Cookie owner = verifiedSession("history-owner@example.com");
        Cookie stranger = verifiedSession("history-stranger@example.com");
        createId(stranger, payload("ONE_TIME", null, today()));
        long[] ids = new long[6];
        for (int index = 0; index < ids.length; index++) {
            ids[index] = createId(owner, payload("ONE_TIME", null, today()));
        }

        mockMvc.perform(get("/api/expenses").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].id").value(ids[5]))
                .andExpect(jsonPath("$[5].id").value(ids[0]));
    }

    @Test
    void expenseHistoryRequiresASession() throws Exception {
        mockMvc.perform(get("/api/expenses"))
                .andExpect(status().isUnauthorized());
    }

    // ----- Feature 015: GET /api/expenses/recent -------------------------------

    @Test
    void recentExpensesReturnsTheLastFiveOfTheCallerNewestFirst() throws Exception {
        Cookie owner = verifiedSession("recent-owner@example.com");
        Cookie stranger = verifiedSession("recent-stranger@example.com");
        createId(stranger, payload("ONE_TIME", null, today()));
        long[] ids = new long[6];
        for (int index = 0; index < ids.length; index++) {
            ids[index] = createId(owner, payload("ONE_TIME", null, today()));
        }

        mockMvc.perform(get("/api/expenses/recent").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].id").value(ids[5]))
                .andExpect(jsonPath("$[4].id").value(ids[1]));
    }

    @Test
    void recentExpensesIsEmptyWithoutExpensesAndRequiresASession() throws Exception {
        Cookie session = verifiedSession("recent-empty@example.com");

        mockMvc.perform(get("/api/expenses/recent").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/expenses/recent")).andExpect(status().isUnauthorized());
    }

    // ----- FR-015 and V5 constraints -----------------------------------------

    @Test
    void legacyRecurringExpensesWithoutFrequencyAreLeftAlone() throws Exception {
        Cookie session = verifiedSession("serie.legado@example.com");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class,
                "serie.legado@example.com");
        Long legacy = jdbcTemplate.queryForObject("""
                INSERT INTO expenses (user_id, amount, currency, expense_date, category, expense_type,
                                      payment_method, idempotency_key)
                VALUES (?, 10.00, 'ARS', ?, 'OTHER', 'RECURRING', 'CASH', ?)
                RETURNING id
                """, Long.class, userId, today().minusMonths(2), UUID.randomUUID());

        recurringExpenseService.generateDueOccurrences(today());

        mockMvc.perform(get("/api/expenses/recurring").cookie(session))
                .andExpect(jsonPath("$.length()").value(0));
        changeStatus(session, legacy, "PAUSED").andExpect(status().isNotFound());
        assertThat(recurrenceOf(legacy)).containsEntry("frequency", null).containsEntry("status", null);
        assertThat(occurrenceDatesOf(legacy)).isEmpty();
    }

    @Test
    void schemaRejectsInconsistentRecurrenceRows() throws Exception {
        verifiedSession("serie.esquema@example.com");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class,
                "serie.esquema@example.com");
        String insert = """
                INSERT INTO expenses (user_id, amount, currency, expense_date, category, expense_type,
                                      payment_method, idempotency_key, recurrence_frequency,
                                      recurrence_status, recurrence_next_date)
                VALUES (?, 10.00, 'ARS', CURRENT_DATE, 'OTHER', ?, 'CASH', ?, ?, ?, ?)
                """;
        LocalDate next = today().plusMonths(1);

        // ONE_TIME with frequency (FR-002)
        assertThatThrownBy(() -> jdbcTemplate.update(insert, userId, "ONE_TIME", UUID.randomUUID(),
                "MONTHLY", "ACTIVE", next)).isInstanceOf(DataIntegrityViolationException.class);
        // frequency without status
        assertThatThrownBy(() -> jdbcTemplate.update(insert, userId, "RECURRING", UUID.randomUUID(),
                "MONTHLY", null, null)).isInstanceOf(DataIntegrityViolationException.class);
        // ACTIVE without next date
        assertThatThrownBy(() -> jdbcTemplate.update(insert, userId, "RECURRING", UUID.randomUUID(),
                "MONTHLY", "ACTIVE", null)).isInstanceOf(DataIntegrityViolationException.class);
        // FINISHED with next date (FR-009)
        assertThatThrownBy(() -> jdbcTemplate.update(insert, userId, "RECURRING", UUID.randomUUID(),
                "MONTHLY", "FINISHED", next)).isInstanceOf(DataIntegrityViolationException.class);
        // unknown frequency / status
        assertThatThrownBy(() -> jdbcTemplate.update(insert, userId, "RECURRING", UUID.randomUUID(),
                "DAILY", "ACTIVE", next)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(insert, userId, "RECURRING", UUID.randomUUID(),
                "MONTHLY", "STOPPED", next)).isInstanceOf(DataIntegrityViolationException.class);

        Long series = jdbcTemplate.queryForObject(insert + " RETURNING id", Long.class, userId, "RECURRING",
                UUID.randomUUID(), "MONTHLY", "ACTIVE", next);
        String occurrence = """
                INSERT INTO expenses (user_id, amount, currency, expense_date, category, expense_type,
                                      payment_method, idempotency_key, recurrence_source_id)
                VALUES (?, 10.00, 'ARS', ?, 'OTHER', 'RECURRING', 'CASH', ?, ?)
                """;
        jdbcTemplate.update(occurrence, userId, today(), UUID.randomUUID(), series);
        // FR-006: one occurrence per series and date
        assertThatThrownBy(() -> jdbcTemplate.update(occurrence, userId, today(), UUID.randomUUID(), series))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
