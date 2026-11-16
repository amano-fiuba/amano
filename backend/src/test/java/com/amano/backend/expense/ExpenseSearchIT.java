package com.amano.backend.expense;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.amano.backend.config.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Contract tests of specs/031-buscar-en-actividad-personal over a real PostgreSQL:
 * {@code GET /api/expenses/search}, {@code /years} and {@code /{id}}. Expenses are
 * inserted directly so their dates and registration order are fixed. All of them
 * are in ARS, the default preferred currency, so no exchange rate is requested.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class ExpenseSearchIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    private static final OffsetDateTime BASE = OffsetDateTime.of(2026, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    private int sequence;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM expenses WHERE user_id IN "
                + "(SELECT id FROM users WHERE email LIKE '%@search031.test')");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE '%@search031.test'");
        sequence = 0;
    }

    // ----- Helpers ------------------------------------------------------------

    private static MockPart part(String name, String value) {
        return new MockPart(name, value.getBytes(StandardCharsets.UTF_8));
    }

    private long verifiedUser(String email) throws Exception {
        mockMvc.perform(multipart("/api/auth/register").part(
                        part("firstName", "Ana"), part("email", email),
                        part("password", "S3gura!2026"), part("confirmPassword", "S3gura!2026"),
                        part("acceptTerms", "true")))
                .andExpect(status().isCreated());
        jdbcTemplate.update("UPDATE users SET email_verified_at = CURRENT_TIMESTAMP WHERE email = ?", email);
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private Cookie session(long userId) {
        return new Cookie("amano_auth", jwtService.issueToken(userId));
    }

    /** Inserts a one-time ARS expense; each call is registered after the previous one. */
    private long insert(long userId, String description, String category, String method, String date) {
        sequence++;
        return jdbcTemplate.queryForObject("""
                INSERT INTO expenses (user_id, amount, currency, expense_date, category, expense_type,
                                      payment_method, description, idempotency_key, created_at)
                VALUES (?, 1000.00, 'ARS', ?, ?, 'ONE_TIME', ?, ?, gen_random_uuid(), ?)
                RETURNING id
                """, Long.class, userId, LocalDate.parse(date), category, method, description,
                BASE.plusMinutes(sequence));
    }

    // ----- Search -------------------------------------------------------------

    @Test
    void searchMatchesDescriptionCategoryAndPaymentMethodIgnoringCaseAndAccents() throws Exception {
        long user = verifiedUser("ana@search031.test");
        long pharmacy = insert(user, "Farmacia centro", "HEALTH", "CASH", "2026-10-03");
        long market = insert(user, "Supermercado", "FOOD", "DEBIT_CARD", "2026-11-10");
        long coffee = insert(user, "Café con Juan", "LEISURE", "VIRTUAL_WALLET", "2026-11-12");
        long noDescription = insert(user, null, "FOOD", "CASH", "2026-11-14");

        mockMvc.perform(get("/api/expenses/search").param("q", "farma").cookie(session(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenses[*].id").value(contains((int) pharmacy)))
                .andExpect(jsonPath("$.truncated").value(false));
        mockMvc.perform(get("/api/expenses/search").param("q", "alim").cookie(session(user)))
                .andExpect(jsonPath("$.expenses[*].id").value(contains((int) noDescription, (int) market)));
        mockMvc.perform(get("/api/expenses/search").param("q", "efectivo").cookie(session(user)))
                .andExpect(jsonPath("$.expenses[*].id").value(contains((int) noDescription, (int) pharmacy)));
        for (String term : new String[] {"CAFE", "café", "  cafe  "}) {
            mockMvc.perform(get("/api/expenses/search").param("q", term).cookie(session(user)))
                    .andExpect(jsonPath("$.expenses[*].id").value(contains((int) coffee)));
        }
        mockMvc.perform(get("/api/expenses/search").param("q", "xyz").cookie(session(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenses", empty()));
    }

    @Test
    void searchTreatsWildcardsLiterally() throws Exception {
        long user = verifiedUser("ana@search031.test");
        long promo = insert(user, "Descuento 100% _promo_", "OTHER", "TRANSFER", "2026-03-05");
        insert(user, "Descuento 1000 promo", "OTHER", "TRANSFER", "2026-03-06");

        mockMvc.perform(get("/api/expenses/search").param("q", "100%").cookie(session(user)))
                .andExpect(jsonPath("$.expenses[*].id").value(contains((int) promo)));
        mockMvc.perform(get("/api/expenses/search").param("q", "_p").cookie(session(user)))
                .andExpect(jsonPath("$.expenses[*].id").value(contains((int) promo)));
        mockMvc.perform(get("/api/expenses/search").param("q", "%%").cookie(session(user)))
                .andExpect(jsonPath("$.expenses", empty()));
    }

    @Test
    void yearFiltersByExpenseDateWithOrWithoutTerm() throws Exception {
        long user = verifiedUser("ana@search031.test");
        long old = insert(user, "Seguro auto", "TRANSPORT", "CREDIT_CARD", "2025-06-20");
        long current = insert(user, "Seguro hogar", "HOUSING", "CREDIT_CARD", "2026-02-01");
        long other = insert(user, "Cine", "LEISURE", "CASH", "2024-12-31");

        mockMvc.perform(get("/api/expenses/search").param("q", "seguro").param("year", "2025")
                        .cookie(session(user)))
                .andExpect(jsonPath("$.expenses[*].id").value(contains((int) old)));
        mockMvc.perform(get("/api/expenses/search").param("q", "seguro").cookie(session(user)))
                .andExpect(jsonPath("$.expenses[*].id").value(contains((int) current, (int) old)));
        mockMvc.perform(get("/api/expenses/search").param("year", "2024").cookie(session(user)))
                .andExpect(jsonPath("$.expenses[*].id").value(contains((int) other)));
        mockMvc.perform(get("/api/expenses/search").cookie(session(user)))
                .andExpect(jsonPath("$.expenses[*].id")
                        .value(contains((int) other, (int) current, (int) old)));
        mockMvc.perform(get("/api/expenses/search").param("year", "2023").cookie(session(user)))
                .andExpect(jsonPath("$.expenses", empty()));
    }

    @Test
    void searchNeverReturnsOtherUsersExpenses() throws Exception {
        long ana = verifiedUser("ana@search031.test");
        long bruno = verifiedUser("bruno@search031.test");
        long own = insert(ana, "Farmacia", "HEALTH", "CASH", "2026-01-10");
        insert(bruno, "Farmacia", "HEALTH", "CASH", "2026-01-10");

        mockMvc.perform(get("/api/expenses/search").param("q", "farmacia").cookie(session(ana)))
                .andExpect(jsonPath("$.expenses[*].id").value(contains((int) own)));
    }

    @Test
    void searchCapsAt200NewestAndReportsTruncation() throws Exception {
        long user = verifiedUser("ana@search031.test");
        jdbcTemplate.update("""
                INSERT INTO expenses (user_id, amount, currency, expense_date, category, expense_type,
                                      payment_method, description, idempotency_key, created_at)
                SELECT ?, 10.00, 'ARS', DATE '2026-05-01', 'FOOD', 'ONE_TIME', 'CASH',
                       'Kiosco ' || n, gen_random_uuid(), TIMESTAMPTZ '2026-01-01 00:00Z' + n * INTERVAL '1 minute'
                FROM generate_series(1, 201) AS n
                """, user);

        mockMvc.perform(get("/api/expenses/search").param("q", "kiosco").cookie(session(user)))
                .andExpect(jsonPath("$.expenses", hasSize(200)))
                .andExpect(jsonPath("$.expenses[0].description").value("Kiosco 201"))
                .andExpect(jsonPath("$.truncated").value(true));
    }

    @Test
    void searchValidatesTermAndYear() throws Exception {
        long user = verifiedUser("ana@search031.test");

        mockMvc.perform(get("/api/expenses/search").param("q", " a ").cookie(session(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.q").value(ExpenseSearchService.TERM_TOO_SHORT));
        mockMvc.perform(get("/api/expenses/search").param("q", "x".repeat(101)).cookie(session(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.q").value(ExpenseSearchService.TERM_TOO_LONG));
        mockMvc.perform(get("/api/expenses/search").param("year", "abc").cookie(session(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.year").value(ExpenseSearchService.YEAR_INVALID));
    }

    // ----- Years --------------------------------------------------------------

    @Test
    void yearsListsDistinctExpenseYearsNewestFirst() throws Exception {
        long user = verifiedUser("ana@search031.test");
        insert(user, "a", "FOOD", "CASH", "2024-03-01");
        insert(user, "b", "FOOD", "CASH", "2026-03-01");
        insert(user, "c", "FOOD", "CASH", "2026-04-01");

        mockMvc.perform(get("/api/expenses/years").cookie(session(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.years").value(contains(2026, 2024)));
        long empty = verifiedUser("bruno@search031.test");
        mockMvc.perform(get("/api/expenses/years").cookie(session(empty)))
                .andExpect(jsonPath("$.years", empty()));
    }

    // ----- Detail -------------------------------------------------------------

    @Test
    void detailReturnsOwnExpenseAndNeutral404Otherwise() throws Exception {
        long ana = verifiedUser("ana@search031.test");
        long bruno = verifiedUser("bruno@search031.test");
        long own = insert(ana, "Farmacia", "HEALTH", "CASH", "2026-01-10");
        long foreign = insert(bruno, "Privado", "HEALTH", "CASH", "2026-01-10");

        mockMvc.perform(get("/api/expenses/" + own).cookie(session(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(own))
                .andExpect(jsonPath("$.description").value("Farmacia"))
                .andExpect(jsonPath("$.paymentMethod").value("CASH"))
                .andExpect(jsonPath("$.conversions", empty()));
        for (String id : new String[] {String.valueOf(foreign), "999999999", "abc"}) {
            mockMvc.perform(get("/api/expenses/" + id).cookie(session(ana)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Gasto no encontrado"))
                    .andExpect(jsonPath("$.detail").value(ExpenseNotFoundException.MESSAGE));
        }
    }

    // ----- Security -----------------------------------------------------------

    @Test
    void everyEndpointRequiresASession() throws Exception {
        for (String path : new String[] {"/api/expenses/search?q=ab", "/api/expenses/years", "/api/expenses/1"}) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }
}
