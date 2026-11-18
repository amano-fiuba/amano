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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.amano.backend.config.JwtService;
import com.amano.backend.groupexpense.exchange.BcraExchangeRateClient;
import com.amano.backend.groupexpense.exchange.CachingExchangeRateProvider;
import com.amano.backend.groupexpense.exchange.ExchangeRateProvider;
import com.amano.backend.groupexpense.exchange.ExchangeRateQuote;
import com.amano.backend.groupexpense.exchange.ExchangeRateUnavailableException;
import com.amano.backend.user.Currency;

import jakarta.servlet.http.Cookie;

/**
 * {@code GET /api/balance} over a real PostgreSQL (specs/032-income-management, US1):
 * net = income - expenses in the main currency, period filters, quotes of each
 * movement's date, scheduled incomes outside the net and the 503 only for
 * expenses and received incomes.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class BalanceIT {

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @MockitoBean
    private BcraExchangeRateClient bcra;

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
        reset(bcra);
        doThrow(new ExchangeRateUnavailableException()).when(bcra).getQuote(any(), any(), any());
    }

    private static ExchangeRateQuote quote(String rate, LocalDate date) {
        return new ExchangeRateQuote(new BigDecimal(rate), date, "TEST");
    }

    private long newUser(String name, boolean verified) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (first_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES (?, ?, 'x', now(), CASE WHEN ? THEN now() END) RETURNING id
                """, Long.class, name, name + "-" + System.nanoTime() + "@amano.dev", verified);
    }

    private Cookie sessionOf(long userId) {
        return new Cookie("amano_auth", jwtService.issueToken(userId));
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

    private ResultActions balance(long userId, String query) throws Exception {
        return mockMvc.perform(get("/api/balance" + query).cookie(sessionOf(userId)));
    }

    @Test
    void theNetBalanceIsIncomeMinusExpensesInTheMainCurrency() throws Exception {
        long userId = newUser("neto", true);
        income(userId, "1000.00", "ARS", TODAY.minusDays(3));
        income(userId, "500.50", "ARS", TODAY.minusDays(2));
        expense(userId, "400.00", "ARS", TODAY.minusDays(1));

        balance(userId, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("ARS"))
                .andExpect(jsonPath("$.income").value("1500.50"))
                .andExpect(jsonPath("$.expenses").value("400.00"))
                .andExpect(jsonPath("$.net").value("1100.50"))
                .andExpect(jsonPath("$.projectedIncome").value("0.00"));
    }

    @Test
    void theNetBalanceCanBeNegativeAndAnEmptyAccountIsZero() throws Exception {
        long userId = newUser("negativo", true);
        long empty = newUser("vacio", true);
        income(userId, "100.00", "ARS", TODAY.minusDays(1));
        expense(userId, "350.00", "ARS", TODAY.minusDays(1));

        balance(userId, "").andExpect(jsonPath("$.net").value("-250.00"));
        balance(empty, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.income").value("0.00"))
                .andExpect(jsonPath("$.net").value("0.00"));
    }

    @Test
    void onlyTheMovementsOfTheSessionUserCount() throws Exception {
        long userId = newUser("propio", true);
        long other = newUser("ajeno", true);
        income(userId, "10.00", "ARS", TODAY.minusDays(1));
        income(other, "99999.00", "ARS", TODAY.minusDays(1));
        expense(other, "88888.00", "ARS", TODAY.minusDays(1));

        balance(userId, "").andExpect(jsonPath("$.net").value("10.00"));
    }

    @Test
    void filtersByYearMonthAndDateRange() throws Exception {
        long userId = newUser("periodo", true);
        income(userId, "100.00", "ARS", LocalDate.of(2024, 1, 15));
        income(userId, "200.00", "ARS", LocalDate.of(2024, 2, 10));
        income(userId, "400.00", "ARS", LocalDate.of(2025, 6, 1));
        expense(userId, "50.00", "ARS", LocalDate.of(2024, 2, 20));

        balance(userId, "?year=2024")
                .andExpect(jsonPath("$.income").value("300.00"))
                .andExpect(jsonPath("$.expenses").value("50.00"))
                .andExpect(jsonPath("$.net").value("250.00"));
        balance(userId, "?year=2024&month=2")
                .andExpect(jsonPath("$.income").value("200.00"))
                .andExpect(jsonPath("$.net").value("150.00"));
        balance(userId, "?from=2024-01-15&to=2024-02-10")
                .andExpect(jsonPath("$.income").value("300.00"))
                .andExpect(jsonPath("$.expenses").value("0.00"));
        balance(userId, "?year=2025&from=2024-01-01&to=2025-12-31")
                .andExpect(jsonPath("$.income").value("400.00"));
    }

    @Test
    void invalidFiltersAreRejectedWithTheirFieldNames() throws Exception {
        long userId = newUser("filtros", true);

        balance(userId, "?year=abc").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.year").exists());
        balance(userId, "?month=5").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.month").exists());
        balance(userId, "?from=2026-03-10&to=2026-03-05").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.from").exists());
        balance(userId, "?from=ayer").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.from").exists());
    }

    @Test
    void convertsForeignMovementsWithTheQuoteOfTheirOwnDate() throws Exception {
        long userId = newUser("multimoneda", true);
        LocalDate incomeDate = TODAY.minusDays(10);
        LocalDate expenseDate = TODAY.minusDays(4);
        income(userId, "100.00", "USD", incomeDate);
        expense(userId, "10.00", "USD", expenseDate);
        doReturn(quote("1000", incomeDate)).when(bcra).getQuote(Currency.USD, Currency.ARS, incomeDate);
        doReturn(quote("900", expenseDate)).when(bcra).getQuote(Currency.USD, Currency.ARS, expenseDate);

        balance(userId, "")
                .andExpect(jsonPath("$.income").value("100000.00"))
                .andExpect(jsonPath("$.expenses").value("9000.00"))
                .andExpect(jsonPath("$.net").value("91000.00"));
    }

    @Test
    void theBalanceIsExpressedInTheMainCurrencyOfTheAccount() throws Exception {
        long userId = newUser("principal", true);
        jdbcTemplate.update("INSERT INTO user_currencies (user_id, currency_code) VALUES (?, 'USD')", userId);
        LocalDate date = TODAY.minusDays(3);
        income(userId, "1000.00", "ARS", date);
        doReturn(quote("0.001", date)).when(bcra).getQuote(Currency.ARS, Currency.USD, date);

        balance(userId, "")
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.income").value("1.00"));
    }

    @Test
    void scheduledIncomesStayOutOfTheNetAndUseTodaysQuoteAsFallback() throws Exception {
        long userId = newUser("proyectado", true);
        LocalDate future = TODAY.plusDays(20);
        income(userId, "1000.00", "ARS", TODAY.minusDays(1));
        income(userId, "10.00", "USD", future);
        doReturn(quote("1200", TODAY)).when(bcra).getQuote(Currency.USD, Currency.ARS, TODAY);

        balance(userId, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.income").value("1000.00"))
                .andExpect(jsonPath("$.projectedIncome").value("12000.00"))
                .andExpect(jsonPath("$.net").value("1000.00"));
    }

    @Test
    void aScheduledIncomeWithoutAnyQuoteNeverFailsTheBalance() throws Exception {
        long userId = newUser("sincotizacion", true);
        income(userId, "1000.00", "ARS", TODAY.minusDays(1));
        income(userId, "10.00", "USD", TODAY.plusDays(20));

        balance(userId, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.net").value("1000.00"))
                .andExpect(jsonPath("$.projectedIncome").doesNotExist());
    }

    @Test
    void aScheduledIncomeBecomesReceivedWhenItsDateArrives() throws Exception {
        long userId = newUser("llega", true);
        income(userId, "700.00", "ARS", TODAY);

        balance(userId, "")
                .andExpect(jsonPath("$.income").value("700.00"))
                .andExpect(jsonPath("$.projectedIncome").value("0.00"));
    }

    @Test
    void aMissingQuoteForAnExpenseOrAReceivedIncomeAnswers503() throws Exception {
        long expenseUser = newUser("gastosincot", true);
        expense(expenseUser, "10.00", "USD", TODAY.minusDays(2));
        long incomeUser = newUser("ingresoincot", true);
        income(incomeUser, "10.00", "USD", TODAY.minusDays(2));

        balance(expenseUser, "").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.properties.code").value("EXCHANGE_RATE_UNAVAILABLE"));
        balance(incomeUser, "").andExpect(status().isServiceUnavailable());
    }

    @Test
    void requiresAVerifiedSession() throws Exception {
        long pending = newUser("pendiente-balance", false);

        mockMvc.perform(get("/api/balance")).andExpect(status().isUnauthorized());
        balance(pending, "").andExpect(status().isUnauthorized());
    }
}
