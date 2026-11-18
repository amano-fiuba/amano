package com.amano.backend.statistics;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.amano.backend.config.JwtService;
import com.amano.backend.groupexpense.exchange.ExchangeRateProvider;
import com.amano.backend.groupexpense.exchange.ExchangeRateQuote;
import com.amano.backend.groupexpense.exchange.ExchangeRateUnavailableException;
import com.amano.backend.support.AbstractIntegrationIT;
import com.amano.backend.user.Currency;

import jakarta.servlet.http.Cookie;

/**
 * Contract tests of specs/034-income-statistics over a real PostgreSQL:
 * {@code GET /api/stats/summary}. Rows are inserted directly so dates and currencies
 * are fixed; the exchange rate provider is replaced.
 */
class StatisticsSummaryIT extends AbstractIntegrationIT {

    private static final String DOMAIN = "@stats034.test";

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
        jdbcTemplate.update("DELETE FROM groups WHERE name LIKE 'Stats034%'");
        jdbcTemplate.update("DELETE FROM incomes WHERE user_id IN " + users);
        jdbcTemplate.update("DELETE FROM expenses WHERE user_id IN " + users);
        jdbcTemplate.update("DELETE FROM user_currencies WHERE user_id IN " + users);
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE '%" + DOMAIN + "'");
    }

    private long verifiedUser(String name) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (first_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES (?, ?, 'x', now(), CURRENT_TIMESTAMP) RETURNING id
                """, Long.class, name, name.toLowerCase() + DOMAIN);
    }

    private ResultActions summary(long userId, String year, String month) throws Exception {
        return mockMvc.perform(get("/api/stats/summary").param("year", year).param("month", month)
                .cookie(new Cookie("amano_auth", jwtService.issueToken(userId))));
    }

    private void expense(long userId, String amount, String currency, String date, String category) {
        jdbcTemplate.update("""
                INSERT INTO expenses (user_id, amount, currency, expense_date, category, expense_type,
                                      payment_method, idempotency_key, created_at)
                VALUES (?, ?, ?, ?, ?, 'ONE_TIME', 'CASH', gen_random_uuid(), now())
                """, userId, new BigDecimal(amount), currency, LocalDate.parse(date), category);
    }

    private void income(long userId, String amount, String currency, String date, String category) {
        jdbcTemplate.update("""
                INSERT INTO incomes (user_id, amount, currency, income_date, category, payment_method,
                                     idempotency_key)
                VALUES (?, ?, ?, ?, ?, 'TRANSFER', ?)
                """, userId, new BigDecimal(amount), currency, LocalDate.parse(date), category,
                UUID.randomUUID());
    }

    private void quote(Currency from, Currency to, String rate) {
        when(exchangeRateProvider.getQuote(eq(from), eq(to), any()))
                .thenReturn(new ExchangeRateQuote(new BigDecimal(rate), LocalDate.parse("2026-11-01"), "BCRA"));
    }

    @Test
    void composesExpensesIncomeAndComparison() throws Exception {
        long ana = verifiedUser("Ana");
        long bruno = verifiedUser("Bruno");
        expense(ana, "45000", "ARS", "2026-11-02", "HOUSING");
        expense(ana, "35000", "ARS", "2026-11-03", "FOOD");
        expense(ana, "20000", "ARS", "2026-11-04", "TRANSPORT");
        long trip = jdbcTemplate.queryForObject(
                "INSERT INTO groups (name, primary_currency) VALUES ('Stats034 ' || gen_random_uuid(), 'ARS') RETURNING id",
                Long.class);
        long shared = jdbcTemplate.queryForObject("""
                INSERT INTO group_expenses (group_id, created_by_user_id, payer_user_id, amount, currency,
                                            expense_date, idempotency_key, converted_amount, category)
                VALUES (?, ?, ?, 120000, 'ARS', DATE '2026-11-10', gen_random_uuid(), 120000, 'LEISURE')
                RETURNING id
                """, Long.class, trip, bruno, bruno);
        jdbcTemplate.update("""
                INSERT INTO group_expense_shares (expense_id, position, participant_type, user_id,
                                                  participant_name, amount)
                VALUES (?, 0, 'REGISTERED', ?, 'Ana', 60000), (?, 1, 'REGISTERED', ?, 'Bruno', 60000)
                """, shared, ana, shared, bruno);
        income(ana, "800000", "ARS", "2026-11-01", "SALARY");
        income(ana, "150000", "ARS", "2026-11-05", "FREELANCE");
        income(ana, "50000", "ARS", "2026-11-06", "SALE");
        income(bruno, "999999", "ARS", "2026-11-01", "SALARY");

        summary(ana, "2026", "11")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.month").value(11))
                .andExpect(jsonPath("$.currency").value("ARS"))
                .andExpect(jsonPath("$.expenses.total").value("160000.00"))
                .andExpect(jsonPath("$.expenses.excludedCount").value(0))
                .andExpect(jsonPath("$.expenses.personal.total").value("100000.00"))
                .andExpect(jsonPath("$.expenses.group.total").value("60000.00"))
                .andExpect(jsonPath("$.expenses.group.categories[*].category").value(contains("LEISURE")))
                .andExpect(jsonPath("$.income.total").value("1000000.00"))
                .andExpect(jsonPath("$.income.categories[*].category")
                        .value(contains("SALARY", "FREELANCE", "SALE")))
                .andExpect(jsonPath("$.income.categories[*].percentage").value(contains("80.0", "15.0", "5.0")))
                .andExpect(jsonPath("$.comparison.income").value("1000000.00"))
                .andExpect(jsonPath("$.comparison.expenses").value("160000.00"))
                .andExpect(jsonPath("$.comparison.balance").value("840000.00"))
                .andExpect(jsonPath("$.years").value(contains(2026)));
    }

    @Test
    void projectedIncomesAreNotAggregatedNorGiveTheirYear() throws Exception {
        long ana = verifiedUser("Ana");
        income(ana, "500", "ARS", "2099-03-10", "SALARY");

        summary(ana, "2099", "3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.income.total").value("0.00"))
                .andExpect(jsonPath("$.income.categories", empty()))
                .andExpect(jsonPath("$.comparison.balance").value("0.00"))
                .andExpect(jsonPath("$.years", empty()));
    }

    @Test
    void incomesWithoutQuoteAreExcludedAndCounted() throws Exception {
        long ana = verifiedUser("Ana");
        quote(Currency.USD, Currency.ARS, "1000");
        when(exchangeRateProvider.getQuote(eq(Currency.EUR), eq(Currency.ARS), any()))
                .thenThrow(new ExchangeRateUnavailableException());
        income(ana, "100", "ARS", "2026-11-02", "SALARY");
        income(ana, "2", "USD", "2026-11-03", "FREELANCE");
        income(ana, "5", "EUR", "2026-11-04", "SALE");
        income(ana, "6", "EUR", "2026-11-04", "SALE");

        summary(ana, "2026", "11")
                .andExpect(jsonPath("$.income.total").value("2100.00"))
                .andExpect(jsonPath("$.income.excludedCount").value(2))
                .andExpect(jsonPath("$.income.categories[*].category").value(contains("FREELANCE", "SALARY")))
                .andExpect(jsonPath("$.comparison.income").value("2100.00"));
    }

    @Test
    void negativeBalanceWhenExpensesExceedIncome() throws Exception {
        long ana = verifiedUser("Ana");
        expense(ana, "1500", "ARS", "2026-11-02", "FOOD");
        income(ana, "1000", "ARS", "2026-11-03", "SALARY");

        summary(ana, "2026", "11").andExpect(jsonPath("$.comparison.balance").value("-500.00"));
    }

    @Test
    void yearsUniteExpensesAndReceivedIncomesNewestFirst() throws Exception {
        long ana = verifiedUser("Ana");
        expense(ana, "10", "ARS", "2024-03-01", "FOOD");
        income(ana, "10", "ARS", "2025-03-01", "SALARY");
        income(ana, "10", "ARS", "2026-03-01", "SALARY");
        income(ana, "10", "ARS", "2099-03-01", "SALARY");

        summary(ana, "2026", "1").andExpect(jsonPath("$.years").value(contains(2026, 2025, 2024)));
    }

    @Test
    void validatesYearAndMonth() throws Exception {
        long ana = verifiedUser("Ana");

        summary(ana, "abc", "13")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.year").value("El año no es válido"))
                .andExpect(jsonPath("$.properties.fields.month").value("El mes no es válido"));
        mockMvc.perform(get("/api/stats/summary")
                        .cookie(new Cookie("amano_auth", jwtService.issueToken(ana))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresASessionAndAVerifiedAccount() throws Exception {
        mockMvc.perform(get("/api/stats/summary").param("year", "2026").param("month", "11"))
                .andExpect(status().isUnauthorized());
        long unverified = jdbcTemplate.queryForObject("""
                INSERT INTO users (first_name, email, password_hash, terms_accepted_at)
                VALUES ('Nadie', 'nadie@stats034.test', 'x', now()) RETURNING id
                """, Long.class);
        summary(unverified, "2026", "11").andExpect(status().isUnauthorized());
    }
}
