package com.amano.backend.expense;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockPart;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.amano.backend.config.JwtService;
import com.amano.backend.groupexpense.exchange.ExchangeRateProvider;
import com.amano.backend.groupexpense.exchange.ExchangeRateQuote;
import com.amano.backend.groupexpense.exchange.ExchangeRateUnavailableException;
import com.amano.backend.user.Currency;

import jakarta.servlet.http.Cookie;

/**
 * Contract tests of specs/032-estadisticas-gastos over a real PostgreSQL:
 * {@code GET /api/expenses/statistics}. Expenses, group expenses and shares are
 * inserted directly so dates, currencies and splits are fixed; the exchange rate
 * provider is replaced so no external quote is requested.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class ExpenseStatisticsIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    private static final String DOMAIN = "@stats032.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private ExchangeRateProvider exchangeRateProvider;

    @BeforeEach
    void cleanUp() {
        String users = "(SELECT id FROM users WHERE email LIKE '%" + DOMAIN + "')";
        jdbcTemplate.update("DELETE FROM group_expense_shares WHERE expense_id IN "
                + "(SELECT id FROM group_expenses WHERE created_by_user_id IN " + users + ")");
        jdbcTemplate.update("DELETE FROM group_expenses WHERE created_by_user_id IN " + users);
        jdbcTemplate.update("DELETE FROM groups WHERE name LIKE 'Stats032%'");
        jdbcTemplate.update("UPDATE expenses SET recurrence_source_id = NULL WHERE user_id IN " + users);
        jdbcTemplate.update("DELETE FROM expenses WHERE user_id IN " + users);
        jdbcTemplate.update("DELETE FROM user_currencies WHERE user_id IN " + users);
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE '%" + DOMAIN + "'");
    }

    // ----- Helpers ------------------------------------------------------------

    private static MockPart part(String name, String value) {
        return new MockPart(name, value.getBytes(StandardCharsets.UTF_8));
    }

    private long verifiedUser(String name) throws Exception {
        String email = name.toLowerCase() + DOMAIN;
        mockMvc.perform(multipart("/api/auth/register").part(
                        part("firstName", name), part("email", email),
                        part("password", "S3gura!2026"), part("confirmPassword", "S3gura!2026"),
                        part("acceptTerms", "true")))
                .andExpect(status().isCreated());
        jdbcTemplate.update("UPDATE users SET email_verified_at = CURRENT_TIMESTAMP WHERE email = ?", email);
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private ResultActions statistics(long userId, String year, String month) throws Exception {
        return mockMvc.perform(get("/api/expenses/statistics").param("year", year).param("month", month)
                .cookie(new Cookie("amano_auth", jwtService.issueToken(userId))));
    }

    private long personal(long userId, String amount, String currency, String date, String category) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO expenses (user_id, amount, currency, expense_date, category, expense_type,
                                      payment_method, idempotency_key, created_at)
                VALUES (?, ?, ?, ?, ?, 'ONE_TIME', 'CASH', gen_random_uuid(), now())
                RETURNING id
                """, Long.class, userId, new BigDecimal(amount), currency, LocalDate.parse(date), category);
    }

    private long group(String currency) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO groups (name, primary_currency) VALUES ('Stats032 ' || gen_random_uuid(), ?) RETURNING id",
                Long.class, currency);
    }

    /**
     * A group (or friend, when {@code groupId} is null) expense; {@code convertedAmount}
     * null mimics a pre-022 expense in another currency.
     */
    private long groupExpense(Long groupId, Long friendId, long payer, String amount, String currency,
            String convertedAmount, String date, String category) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO group_expenses (group_id, friend_user_id, created_by_user_id, payer_user_id,
                                            amount, currency, expense_date, idempotency_key,
                                            converted_amount, category)
                VALUES (?, ?, ?, ?, ?, ?, ?, gen_random_uuid(), ?, ?)
                RETURNING id
                """, Long.class, groupId, friendId, payer, payer, new BigDecimal(amount), currency,
                LocalDate.parse(date), convertedAmount == null ? null : new BigDecimal(convertedAmount),
                category);
    }

    private void share(long expenseId, int position, long userId, String amount) {
        jdbcTemplate.update("""
                INSERT INTO group_expense_shares (expense_id, position, participant_type, user_id,
                                                  participant_name, amount)
                VALUES (?, ?, 'REGISTERED', ?, 'Persona', ?)
                """, expenseId, position, userId, new BigDecimal(amount));
    }

    private void simulatedShare(long expenseId, int position, String amount) {
        jdbcTemplate.update("""
                INSERT INTO group_expense_shares (expense_id, position, participant_type, participant_name, amount)
                VALUES (?, ?, 'SIMULATED', 'Perfil', ?)
                """, expenseId, position, new BigDecimal(amount));
    }

    private void quote(Currency from, Currency to, String rate) {
        when(exchangeRateProvider.getQuote(eq(from), eq(to), any()))
                .thenReturn(new ExchangeRateQuote(new BigDecimal(rate), LocalDate.parse("2026-11-01"), "BCRA"));
    }

    // ----- Personal -----------------------------------------------------------

    @Test
    void personalExpensesOfTheMonthByCategory() throws Exception {
        long ana = verifiedUser("Ana");
        long bruno = verifiedUser("Bruno");
        personal(ana, "20000", "ARS", "2026-11-01", "FOOD");
        personal(ana, "45000", "ARS", "2026-11-02", "HOUSING");
        personal(ana, "15000", "ARS", "2026-11-30", "FOOD");
        personal(ana, "20000", "ARS", "2026-11-15", "TRANSPORT");
        personal(ana, "999", "ARS", "2026-10-31", "FOOD");
        personal(ana, "999", "ARS", "2026-12-01", "FOOD");
        personal(bruno, "777", "ARS", "2026-11-02", "FOOD");

        statistics(ana, "2026", "11")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.month").value(11))
                .andExpect(jsonPath("$.currency").value("ARS"))
                .andExpect(jsonPath("$.total").value("100000.00"))
                .andExpect(jsonPath("$.excludedCount").value(0))
                .andExpect(jsonPath("$.personal.total").value("100000.00"))
                .andExpect(jsonPath("$.personal.categories[*].category")
                        .value(contains("HOUSING", "FOOD", "TRANSPORT")))
                .andExpect(jsonPath("$.personal.categories[*].amount")
                        .value(contains("45000.00", "35000.00", "20000.00")))
                .andExpect(jsonPath("$.personal.categories[*].percentage")
                        .value(contains("45.0", "35.0", "20.0")))
                .andExpect(jsonPath("$.group.total").value("0.00"))
                .andExpect(jsonPath("$.group.categories", empty()))
                .andExpect(jsonPath("$.years").value(contains(2026)));
    }

    @Test
    void recurringSeriesAndOccurrencesCountOncePerDate() throws Exception {
        long ana = verifiedUser("Ana");
        long series = jdbcTemplate.queryForObject("""
                INSERT INTO expenses (user_id, amount, currency, expense_date, category, expense_type,
                                      payment_method, idempotency_key, created_at, recurrence_frequency,
                                      recurrence_status, recurrence_next_date)
                VALUES (?, 1000, 'ARS', DATE '2026-10-05', 'SERVICES', 'RECURRING', 'TRANSFER',
                        gen_random_uuid(), now(), 'MONTHLY', 'ACTIVE', DATE '2026-12-05')
                RETURNING id
                """, Long.class, ana);
        jdbcTemplate.update("""
                INSERT INTO expenses (user_id, amount, currency, expense_date, category, expense_type,
                                      payment_method, idempotency_key, created_at, recurrence_source_id)
                VALUES (?, 1000, 'ARS', DATE '2026-11-05', 'SERVICES', 'RECURRING', 'TRANSFER',
                        gen_random_uuid(), now(), ?)
                """, ana, series);

        statistics(ana, "2026", "11")
                .andExpect(jsonPath("$.personal.categories[*].amount").value(contains("1000.00")));
        statistics(ana, "2026", "10")
                .andExpect(jsonPath("$.personal.categories[*].amount").value(contains("1000.00")));
    }

    // ----- Group and friend shares --------------------------------------------

    @Test
    void groupAndFriendExpensesCountOnlyThePersonsShare() throws Exception {
        long ana = verifiedUser("Ana");
        long bruno = verifiedUser("Bruno");
        long carla = verifiedUser("Carla");
        long trip = group("ARS");
        // Paid by Bruno: Ana's share counts.
        long dinner = groupExpense(trip, null, bruno, "90000", "ARS", "90000", "2026-11-10", "FOOD");
        share(dinner, 0, ana, "30000");
        share(dinner, 1, bruno, "30000");
        simulatedShare(dinner, 2, "30000");
        // Paid by Ana: only her share counts, not the whole amount.
        long museum = groupExpense(trip, null, ana, "30000", "ARS", "30000", "2026-11-11", "LEISURE");
        share(museum, 0, ana, "10000");
        share(museum, 1, bruno, "20000");
        // Friend expense (no group).
        long cinema = groupExpense(null, carla, ana, "40000", "ARS", "40000", "2026-11-12", "LEISURE");
        share(cinema, 0, ana, "20000");
        share(cinema, 1, carla, "20000");
        // Pre-027 expense without category -> OTHER.
        long old = groupExpense(trip, null, bruno, "10000", "ARS", "10000", "2026-11-13", null);
        share(old, 0, ana, "5000");
        share(old, 1, bruno, "5000");
        // A group expense where Ana has no share.
        long foreign = groupExpense(trip, null, ana, "50000", "ARS", "50000", "2026-11-14", "HEALTH");
        share(foreign, 0, bruno, "50000");

        statistics(ana, "2026", "11")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.group.total").value("65000.00"))
                .andExpect(jsonPath("$.group.categories[*].category").value(contains("FOOD", "LEISURE", "OTHER")))
                .andExpect(jsonPath("$.group.categories[*].amount")
                        .value(contains("30000.00", "30000.00", "5000.00")))
                .andExpect(jsonPath("$.group.categories[*].percentage").value(contains("46.2", "46.2", "7.7")))
                .andExpect(jsonPath("$.personal.categories", empty()))
                .andExpect(jsonPath("$.total").value("65000.00"));
        // Bruno sees his own shares, never Ana's.
        statistics(bruno, "2026", "11")
                .andExpect(jsonPath("$.group.total").value("105000.00"));
    }

    @Test
    void sharesAreConvertedFromTheCurrencyTheyWereSplitIn() throws Exception {
        long ana = verifiedUser("Ana");
        long bruno = verifiedUser("Bruno");
        quote(Currency.USD, Currency.ARS, "1000");
        quote(Currency.EUR, Currency.ARS, "1200");
        // Converted to the group currency (USD): the share is in USD.
        long usdGroup = group("USD");
        long hotel = groupExpense(usdGroup, null, bruno, "100000", "ARS", "100", "2026-11-03", "HOUSING");
        share(hotel, 0, ana, "50");
        share(hotel, 1, bruno, "50");
        // Pre-022 expense in EUR, never converted: the share stayed in EUR.
        long legacy = groupExpense(group("ARS"), null, bruno, "20", "EUR", null, "2026-11-04", "FOOD");
        share(legacy, 0, ana, "10");
        share(legacy, 1, bruno, "10");

        statistics(ana, "2026", "11")
                .andExpect(jsonPath("$.group.categories[*].category").value(contains("HOUSING", "FOOD")))
                .andExpect(jsonPath("$.group.categories[*].amount").value(contains("50000.00", "12000.00")))
                .andExpect(jsonPath("$.excludedCount").value(0));
    }

    // ----- Currency -----------------------------------------------------------

    @Test
    void usesTheFirstPreferredCurrencyAndExcludesWhatCannotBeConverted() throws Exception {
        long ana = verifiedUser("Ana");
        jdbcTemplate.update("DELETE FROM user_currencies WHERE user_id = ?", ana);
        jdbcTemplate.update("INSERT INTO user_currencies (user_id, currency_code) VALUES (?, 'EUR'), (?, 'USD')",
                ana, ana);
        quote(Currency.ARS, Currency.USD, "0.001");
        when(exchangeRateProvider.getQuote(eq(Currency.EUR), eq(Currency.USD), any()))
                .thenThrow(new ExchangeRateUnavailableException());
        personal(ana, "10000", "ARS", "2026-11-02", "FOOD");
        personal(ana, "5", "USD", "2026-11-02", "FOOD");
        personal(ana, "7", "EUR", "2026-11-03", "LEISURE");
        personal(ana, "8", "EUR", "2026-11-03", "LEISURE");

        statistics(ana, "2026", "11")
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.personal.categories[*].amount").value(contains("15.00")))
                .andExpect(jsonPath("$.total").value("15.00"))
                .andExpect(jsonPath("$.excludedCount").value(2));
    }

    // ----- Years --------------------------------------------------------------

    @Test
    void yearsCombinePersonalExpensesAndOwnSharesNewestFirst() throws Exception {
        long ana = verifiedUser("Ana");
        long bruno = verifiedUser("Bruno");
        personal(ana, "10", "ARS", "2024-03-01", "FOOD");
        personal(ana, "10", "ARS", "2026-03-01", "FOOD");
        long shared = groupExpense(group("ARS"), null, bruno, "20", "ARS", "20", "2025-06-01", "FOOD");
        share(shared, 0, ana, "10");
        share(shared, 1, bruno, "10");
        long notMine = groupExpense(group("ARS"), null, bruno, "20", "ARS", "20", "2023-06-01", "FOOD");
        share(notMine, 0, bruno, "20");

        statistics(ana, "2026", "1")
                .andExpect(jsonPath("$.years").value(contains(2026, 2025, 2024)));
    }

    // ----- Validation and security --------------------------------------------

    @Test
    void validatesYearAndMonth() throws Exception {
        long ana = verifiedUser("Ana");

        statistics(ana, "abc", "13")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.year").value(ExpenseStatisticsService.YEAR_INVALID))
                .andExpect(jsonPath("$.properties.fields.month").value(ExpenseStatisticsService.MONTH_INVALID));
        mockMvc.perform(get("/api/expenses/statistics")
                        .cookie(new Cookie("amano_auth", jwtService.issueToken(ana))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.year").value(ExpenseStatisticsService.YEAR_INVALID));
    }

    @Test
    void requiresASession() throws Exception {
        mockMvc.perform(get("/api/expenses/statistics").param("year", "2026").param("month", "11"))
                .andExpect(status().isUnauthorized());
    }
}
