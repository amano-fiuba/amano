package com.amano.backend.balance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.amano.backend.config.JwtService;
import com.amano.backend.groupexpense.exchange.CachingExchangeRateProvider;
import com.amano.backend.groupexpense.exchange.ExchangeRateProvider;
import com.amano.backend.groupexpense.exchange.ExchangeRateQuote;
import com.amano.backend.groupexpense.exchange.ExchangeRateUnavailableException;
import com.amano.backend.support.AbstractIntegrationIT;
import com.amano.backend.user.Currency;

import jakarta.servlet.http.Cookie;

/**
 * Multi-currency and scheduled incomes in {@code GET /api/balance}
 * (specs/033-income-management, US4): quote of each movement's own date,
 * projected incomes outside the net with today's quote as fallback, and 503 only
 * for expenses and received incomes.
 */
class BalanceMultiCurrencyIT extends AbstractIntegrationIT {

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    @Autowired
    private ExchangeRateProvider exchangeRateProvider;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void freshQuotes() {
        if (exchangeRateProvider instanceof CachingExchangeRateProvider caching) {
            caching.clear();
        }
        reset(bcraExchangeRateClient);
        doThrow(new ExchangeRateUnavailableException()).when(bcraExchangeRateClient).getQuote(any(), any(), any());
    }

    private static ExchangeRateQuote quote(String rate, LocalDate date) {
        return new ExchangeRateQuote(new BigDecimal(rate), date, "TEST");
    }

    private long newUser(String name) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (first_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES (?, ?, 'x', now(), now()) RETURNING id
                """, Long.class, name, name + "-" + System.nanoTime() + "@amano.dev");
    }

    private void income(long userId, String amount, String currency, LocalDate date) {
        jdbcTemplate.update("""
                INSERT INTO incomes (user_id, amount, currency, income_date, category,
                                     payment_method, idempotency_key)
                VALUES (?, ?::numeric, ?, ?, 'SALARY', 'TRANSFER', ?)
                """, userId, amount, currency, date, UUID.randomUUID());
    }

    private void expense(long userId, String amount, String currency, LocalDate date) {
        jdbcTemplate.update("""
                INSERT INTO expenses (user_id, amount, currency, expense_date, category,
                                      expense_type, payment_method, idempotency_key)
                VALUES (?, ?::numeric, ?, ?, 'FOOD', 'ONE_TIME', 'CASH', ?)
                """, userId, amount, currency, date, UUID.randomUUID());
    }

    private ResultActions balance(long userId) throws Exception {
        return mockMvc.perform(get("/api/balance")
                .cookie(new Cookie("amano_auth", jwtService.issueToken(userId))));
    }

    @Test
    void incomeAndExpenseInUsdUseTheQuoteOfTheirOwnDate() throws Exception {
        long userId = newUser("usd");
        LocalDate incomeDate = TODAY.minusDays(12);
        LocalDate expenseDate = TODAY.minusDays(6);
        income(userId, "200.00", "USD", incomeDate);
        expense(userId, "50.00", "USD", expenseDate);
        doReturn(quote("1000", incomeDate)).when(bcraExchangeRateClient).getQuote(Currency.USD, Currency.ARS, incomeDate);
        doReturn(quote("1100", expenseDate)).when(bcraExchangeRateClient).getQuote(Currency.USD, Currency.ARS, expenseDate);

        balance(userId).andExpect(status().isOk())
                .andExpect(jsonPath("$.income").value("200000.00"))
                .andExpect(jsonPath("$.expenses").value("55000.00"))
                .andExpect(jsonPath("$.net").value("145000.00"));
    }

    @Test
    void anAccountWithAnotherMainCurrencyConvertsArsMovements() throws Exception {
        long userId = newUser("principal-usd");
        jdbcTemplate.update("INSERT INTO user_currencies (user_id, currency_code) VALUES (?, 'USD')", userId);
        LocalDate date = TODAY.minusDays(3);
        income(userId, "2000.00", "ARS", date);
        expense(userId, "500.00", "ARS", date);
        doReturn(quote("0.001", date)).when(bcraExchangeRateClient).getQuote(Currency.ARS, Currency.USD, date);

        balance(userId).andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.income").value("2.00"))
                .andExpect(jsonPath("$.expenses").value("0.50"))
                .andExpect(jsonPath("$.net").value("1.50"));
    }

    @Test
    void projectedIncomeAddsToProjectedIncomeButNotToTheNet() throws Exception {
        long userId = newUser("proyectado");
        LocalDate future = TODAY.plusDays(30);
        income(userId, "1000.00", "ARS", TODAY.minusDays(1));
        income(userId, "500.00", "ARS", future);

        balance(userId).andExpect(jsonPath("$.income").value("1000.00"))
                .andExpect(jsonPath("$.projectedIncome").value("500.00"))
                .andExpect(jsonPath("$.net").value("1000.00"));
    }

    @Test
    void aProjectedUsdIncomeFallsBackToTodaysQuote() throws Exception {
        long userId = newUser("fallback");
        income(userId, "10.00", "USD", TODAY.plusDays(15));
        doReturn(quote("1300", TODAY)).when(bcraExchangeRateClient).getQuote(Currency.USD, Currency.ARS, TODAY);

        balance(userId).andExpect(status().isOk())
                .andExpect(jsonPath("$.projectedIncome").value("13000.00"))
                .andExpect(jsonPath("$.net").value("0.00"));
    }

    @Test
    void withoutTodaysQuoteEitherTheProjectedIncomeIsOmittedAndTheBalanceIs200() throws Exception {
        long userId = newUser("sin-hoy");
        income(userId, "100.00", "ARS", TODAY.minusDays(1));
        income(userId, "10.00", "USD", TODAY.plusDays(15));

        balance(userId).andExpect(status().isOk())
                .andExpect(jsonPath("$.net").value("100.00"))
                .andExpect(jsonPath("$.projectedIncome").doesNotExist());
    }

    @Test
    void aMissingQuoteOfAnExpenseOrAReceivedIncomeAnswers503() throws Exception {
        long expenseUser = newUser("gasto-sin-cot");
        expense(expenseUser, "10.00", "USD", TODAY.minusDays(2));
        long incomeUser = newUser("ingreso-sin-cot");
        income(incomeUser, "10.00", "USD", TODAY.minusDays(2));

        balance(expenseUser).andExpect(status().isServiceUnavailable());
        balance(incomeUser).andExpect(status().isServiceUnavailable());
    }

    @Test
    void anIncomeDatedTodayCountsAsReceivedNotProjected() throws Exception {
        long userId = newUser("hoy");
        income(userId, "300.00", "ARS", TODAY);

        balance(userId).andExpect(jsonPath("$.income").value("300.00"))
                .andExpect(jsonPath("$.projectedIncome").value("0.00"))
                .andExpect(jsonPath("$.net").value("300.00"));
    }
}
