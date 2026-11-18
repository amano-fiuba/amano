package com.amano.backend.income;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.amano.backend.config.JwtService;
import com.amano.backend.groupexpense.exchange.CachingExchangeRateProvider;
import com.amano.backend.groupexpense.exchange.ExchangeRateProvider;
import com.amano.backend.groupexpense.exchange.ExchangeRateQuote;
import com.amano.backend.groupexpense.exchange.ExchangeRateUnavailableException;
import com.amano.backend.user.Currency;

import jakarta.servlet.http.Cookie;
import com.amano.backend.support.AbstractIntegrationIT;

/**
 * {@code GET /api/incomes/search} and {@code /years} over a real PostgreSQL
 * (specs/033-income-management, US2): text search without case or accents on the
 * description and the category label, year, month and range filters, order, paging,
 * conversions and the isolation between people.
 */
class IncomeSearchIT extends AbstractIntegrationIT {

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

    private long newUser(String name) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (first_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES (?, ?, 'x', now(), now()) RETURNING id
                """, Long.class, name, name + "-" + System.nanoTime() + "@amano.dev");
    }

    private Cookie sessionOf(long userId) {
        return new Cookie("amano_auth", jwtService.issueToken(userId));
    }

    private long income(long userId, String amount, String currency, LocalDate date,
            String category, String description) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO incomes (user_id, amount, currency, income_date, category,
                                     payment_method, description, idempotency_key)
                VALUES (?, ?::numeric, ?, ?, ?, 'TRANSFER', ?, ?) RETURNING id
                """, Long.class, userId, amount, currency, date, category, description,
                UUID.randomUUID());
    }

    private ResultActions search(long userId, String query) throws Exception {
        return mockMvc.perform(get("/api/incomes/search" + query).cookie(sessionOf(userId)));
    }

    /** The term travels as a request parameter, so the client-side encoding is the real one. */
    private ResultActions searchTerm(long userId, String term) throws Exception {
        return mockMvc.perform(get("/api/incomes/search").param("q", term).cookie(sessionOf(userId)));
    }

    @Test
    void listsEveryIncomeOfThePersonNewestIncomeDateFirstWithTheContractShape() throws Exception {
        long userId = newUser("lista");
        long oldest = income(userId, "10.00", "ARS", LocalDate.of(2024, 1, 5), "SALE", null);
        long newest = income(userId, "30.00", "ARS", LocalDate.of(2026, 5, 20), "SALARY", "Sueldo mayo");
        long middle = income(userId, "20.00", "ARS", LocalDate.of(2025, 3, 1), "REFUND", "Devolución");

        search(userId, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.incomes[0].id").value(newest))
                .andExpect(jsonPath("$.incomes[1].id").value(middle))
                .andExpect(jsonPath("$.incomes[2].id").value(oldest))
                .andExpect(jsonPath("$.incomes[0].amount").value("30.00"))
                .andExpect(jsonPath("$.incomes[0].category").value("SALARY"))
                .andExpect(jsonPath("$.incomes[0].paymentMethod").value("TRANSFER"))
                .andExpect(jsonPath("$.incomes[0].description").value("Sueldo mayo"))
                .andExpect(jsonPath("$.incomes[0].projected").value(false))
                .andExpect(jsonPath("$.incomes[0].conversions").isArray())
                .andExpect(jsonPath("$.incomes[2].description").doesNotExist());
    }

    @Test
    void sameDateIsBrokenByTheLastRegistered() throws Exception {
        long userId = newUser("empate");
        LocalDate date = LocalDate.of(2026, 2, 2);
        long first = income(userId, "1.00", "ARS", date, "SALE", null);
        long second = income(userId, "2.00", "ARS", date, "SALE", null);

        search(userId, "")
                .andExpect(jsonPath("$.incomes[0].id").value(second))
                .andExpect(jsonPath("$.incomes[1].id").value(first));
    }

    @Test
    void matchesTheDescriptionAndTheCategoryLabelIgnoringCaseAndAccents() throws Exception {
        long userId = newUser("texto");
        income(userId, "1.00", "ARS", LocalDate.of(2026, 1, 1), "FREELANCE", "Proyecto web");
        income(userId, "2.00", "ARS", LocalDate.of(2026, 1, 2), "SALE", "Venta bici");
        income(userId, "3.00", "ARS", LocalDate.of(2026, 1, 3), "SALARY", "Café de la oficina");
        income(userId, "4.00", "ARS", LocalDate.of(2026, 1, 4), "REFUND", null);

        search(userId, "?q=proyec").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.incomes[0].description").value("Proyecto web"));
        search(userId, "?q=PROYEC").andExpect(jsonPath("$.totalElements").value(1));
        search(userId, "?q=cafe").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.incomes[0].category").value("SALARY"));
        search(userId, "?q=CAFÉ").andExpect(jsonPath("$.totalElements").value(1));
        // The Spanish label of the category: "Sueldo" finds the SALARY one, "reembol" the REFUND one.
        search(userId, "?q=sueldo").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.incomes[0].amount").value("3.00"));
        search(userId, "?q=reembol").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.incomes[0].amount").value("4.00"));
        // "venta" is both the label of SALE and the start of a description.
        search(userId, "?q=venta").andExpect(jsonPath("$.totalElements").value(1));
        // The payment method is not searched.
        search(userId, "?q=transferencia").andExpect(jsonPath("$.totalElements").value(0));
        search(userId, "?q=zzz").andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.incomes").isEmpty());
    }

    @Test
    void percentAndUnderscoreAreSearchedLiterally() throws Exception {
        long userId = newUser("literal");
        income(userId, "1.00", "ARS", LocalDate.of(2026, 1, 1), "SALE", "100% efectivo");
        income(userId, "2.00", "ARS", LocalDate.of(2026, 1, 2), "SALE", "pago_final");
        income(userId, "3.00", "ARS", LocalDate.of(2026, 1, 3), "SALE", "otro");

        searchTerm(userId, "%%").andExpect(jsonPath("$.totalElements").value(0));
        searchTerm(userId, "0%").andExpect(jsonPath("$.totalElements").value(1));
        searchTerm(userId, "o_f").andExpect(jsonPath("$.totalElements").value(1));
        searchTerm(userId, "o__f").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filtersByYearMonthAndInclusiveDateRange() throws Exception {
        long userId = newUser("periodo");
        income(userId, "1.00", "ARS", LocalDate.of(2024, 1, 15), "SALE", null);
        income(userId, "2.00", "ARS", LocalDate.of(2024, 2, 10), "SALE", null);
        income(userId, "3.00", "ARS", LocalDate.of(2025, 6, 1), "SALE", null);

        search(userId, "?year=2024").andExpect(jsonPath("$.totalElements").value(2));
        search(userId, "?year=2024&month=2").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.incomes[0].amount").value("2.00"));
        search(userId, "?from=2024-01-15&to=2024-02-10").andExpect(jsonPath("$.totalElements").value(2));
        search(userId, "?from=2024-01-16&to=2024-02-09").andExpect(jsonPath("$.totalElements").value(0));
        search(userId, "?from=2024-02-10").andExpect(jsonPath("$.totalElements").value(2));
        search(userId, "?to=2024-01-15").andExpect(jsonPath("$.totalElements").value(1));
        // Range and year intersect.
        search(userId, "?year=2025&from=2024-01-01&to=2025-12-31")
                .andExpect(jsonPath("$.totalElements").value(1));
        search(userId, "?year=2024&from=2025-01-01&to=2025-12-31")
                .andExpect(jsonPath("$.totalElements").value(0));
        search(userId, "?q=xx&year=2024").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void pagesTheResultsAndAnswersTheLastPageWhenThePageDoesNotExist() throws Exception {
        long userId = newUser("paginas");
        for (int day = 1; day <= 7; day++) {
            income(userId, day + ".00", "ARS", LocalDate.of(2026, 3, day), "SALE", null);
        }

        search(userId, "?size=3&page=1").andExpect(jsonPath("$.incomes.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(7))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.incomes[0].amount").value("7.00"));
        search(userId, "?size=3&page=3").andExpect(jsonPath("$.incomes.length()").value(1))
                .andExpect(jsonPath("$.page").value(3))
                .andExpect(jsonPath("$.incomes[0].amount").value("1.00"));
        search(userId, "?size=3&page=99").andExpect(jsonPath("$.page").value(3))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.incomes.length()").value(1));
    }

    @Test
    void listsTheYearsWithIncomesNewestFirst() throws Exception {
        long userId = newUser("anios");
        long empty = newUser("sinanios");
        income(userId, "1.00", "ARS", LocalDate.of(2023, 12, 31), "SALE", null);
        income(userId, "1.00", "ARS", LocalDate.of(2026, 1, 1), "SALE", null);
        income(userId, "1.00", "ARS", LocalDate.of(2026, 7, 1), "SALE", null);

        mockMvc.perform(get("/api/incomes/years").cookie(sessionOf(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.years.length()").value(2))
                .andExpect(jsonPath("$.years[0]").value(2026))
                .andExpect(jsonPath("$.years[1]").value(2023));
        mockMvc.perform(get("/api/incomes/years").cookie(sessionOf(empty)))
                .andExpect(jsonPath("$.years").isEmpty());
    }

    @Test
    void onlyTheIncomesOfThePersonAreListed() throws Exception {
        long mine = newUser("mios");
        long theirs = newUser("ajenos");
        income(mine, "1.00", "ARS", LocalDate.of(2026, 1, 1), "SALE", "Mío");
        income(theirs, "2.00", "ARS", LocalDate.of(2026, 1, 1), "SALE", "Ajeno");

        search(mine, "").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.incomes[0].description").value("Mío"));
        search(mine, "?q=ajeno").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void marksAFutureIncomeAsProjected() throws Exception {
        long userId = newUser("futuro");
        income(userId, "5.00", "ARS", LocalDate.now().plusDays(40), "SALE", "Cobro programado");
        income(userId, "6.00", "ARS", LocalDate.of(2020, 1, 1), "SALE", null);

        search(userId, "").andExpect(jsonPath("$.incomes[0].projected").value(true))
                .andExpect(jsonPath("$.incomes[1].projected").value(false));
    }

    @Test
    void addsTheEquivalentInThePreferredCurrenciesAndOmitsAMissingQuote() throws Exception {
        long userId = newUser("equivalentes");
        jdbcTemplate.update("INSERT INTO user_currencies (user_id, currency_code) VALUES (?, 'ARS')", userId);
        jdbcTemplate.update("INSERT INTO user_currencies (user_id, currency_code) VALUES (?, 'EUR')", userId);
        LocalDate quoted = LocalDate.of(2026, 3, 10);
        LocalDate unquoted = LocalDate.of(2026, 3, 11);
        income(userId, "100.00", "USD", quoted, "FREELANCE", null);
        income(userId, "50.00", "USD", unquoted, "FREELANCE", null);
        doReturn(new ExchangeRateQuote(new BigDecimal("1000"), quoted, "TEST"))
                .when(bcraExchangeRateClient).getQuote(Currency.USD, Currency.ARS, quoted);
        doReturn(new ExchangeRateQuote(new BigDecimal("0.9"), quoted, "TEST"))
                .when(bcraExchangeRateClient).getQuote(Currency.USD, Currency.EUR, quoted);

        search(userId, "")
                .andExpect(jsonPath("$.incomes[0].conversions").isEmpty())
                .andExpect(jsonPath("$.incomes[1].conversions.length()").value(2))
                .andExpect(jsonPath("$.incomes[1].conversions[0].currency").value("ARS"))
                .andExpect(jsonPath("$.incomes[1].conversions[0].amount").value("100000.00"))
                .andExpect(jsonPath("$.incomes[1].conversions[1].currency").value("EUR"))
                .andExpect(jsonPath("$.incomes[1].conversions[1].amount").value("90.00"));
    }

    @Test
    void rejectsInvalidCriteriaWithTheirFieldNames() throws Exception {
        long userId = newUser("invalidos");

        search(userId, "?q=a").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.q").value("Ingresá al menos 2 caracteres para buscar"));
        search(userId, "?q=" + "a".repeat(101)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.q").exists());
        search(userId, "?year=0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.year").exists());
        search(userId, "?month=5").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.month").value("Elegí un año para filtrar por mes"));
        search(userId, "?from=2026-03-10&to=2026-03-01").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.from").exists());
        search(userId, "?to=mañana").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.to").exists());
        search(userId, "?page=0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.page").exists());
        search(userId, "?size=51").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.size").exists());
        search(userId, "?q=a&year=x&page=z").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.q").exists())
                .andExpect(jsonPath("$.properties.fields.year").exists())
                .andExpect(jsonPath("$.properties.fields.page").exists());
    }
}
