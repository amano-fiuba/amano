package com.amano.backend.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.groupexpense.exchange.ExchangeRateProvider;
import com.amano.backend.groupexpense.exchange.ExchangeRateQuote;
import com.amano.backend.groupexpense.exchange.ExchangeRateUnavailableException;
import com.amano.backend.user.Currency;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * Unit tests of the 032 statistics calculation: validation, calculation currency,
 * sums, percentages, rounding, order, conversion and excluded expenses.
 */
class ExpenseStatisticsServiceTest {

    private static final Long USER_ID = 7L;
    private static final LocalDate FROM = LocalDate.parse("2026-11-01");
    private static final LocalDate TO = LocalDate.parse("2026-11-30");

    private ExpenseRepository expenseRepository;
    private SharedExpenseStatisticsRepository sharedRepository;
    private UserRepository userRepository;
    private ExchangeRateProvider rates;
    private ExpenseStatisticsService service;

    @BeforeEach
    void setUp() {
        expenseRepository = Mockito.mock(ExpenseRepository.class);
        sharedRepository = Mockito.mock(SharedExpenseStatisticsRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        rates = Mockito.mock(ExchangeRateProvider.class);
        service = new ExpenseStatisticsService(expenseRepository, sharedRepository, userRepository, rates);
        when(sharedRepository.findYears(USER_ID)).thenReturn(List.of(2026, 2024));
    }

    private static CategorySubtotal subtotal(ExpenseCategory category, Currency currency, String date,
            String amount, long count) {
        return new CategorySubtotal(category, currency, LocalDate.parse(date), new BigDecimal(amount), count);
    }

    private void personal(CategorySubtotal... subtotals) {
        when(expenseRepository.sumByCategoryCurrencyAndDate(USER_ID, FROM, TO)).thenReturn(List.of(subtotals));
    }

    private void shares(CategorySubtotal... subtotals) {
        when(sharedRepository.sumSharesByCategoryCurrencyAndDate(USER_ID, FROM, TO))
                .thenReturn(List.of(subtotals));
    }

    private void preferred(Currency... currencies) {
        User user = new User();
        user.getCurrencies().addAll(Set.of(currencies));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private void quote(Currency from, Currency to, String date, String rate) {
        when(rates.getQuote(from, to, LocalDate.parse(date)))
                .thenReturn(new ExchangeRateQuote(new BigDecimal(rate), LocalDate.parse(date), "BCRA"));
    }

    private ExpenseStatisticsResponse november() {
        return service.statistics(USER_ID, "2026", "11");
    }

    // ----- Validation ---------------------------------------------------------

    @Test
    void validateAcceptsTrimmedYearAndMonth() {
        assertThat(ExpenseStatisticsService.validate(" 2026 ", "3").getMonthValue()).isEqualTo(3);
        assertThat(ExpenseStatisticsService.validate("1", "12").getYear()).isEqualTo(1);
        assertThat(ExpenseStatisticsService.validate("9999", "1").getYear()).isEqualTo(9999);
    }

    @Test
    void validateRejectsMissingOrInvalidValues() {
        for (String[] invalid : new String[][] {
                {null, null}, {"", " "}, {"abc", "x"}, {"0", "0"}, {"10000", "13"}, {"-1", "-1"}}) {
            assertThatThrownBy(() -> ExpenseStatisticsService.validate(invalid[0], invalid[1]))
                    .isInstanceOfSatisfying(FieldValidationException.class, e -> assertThat(e.getFields())
                            .containsEntry("year", ExpenseStatisticsService.YEAR_INVALID)
                            .containsEntry("month", ExpenseStatisticsService.MONTH_INVALID));
        }
        assertThatThrownBy(() -> ExpenseStatisticsService.validate("2026", "13"))
                .isInstanceOfSatisfying(FieldValidationException.class, e -> assertThat(e.getFields())
                        .containsOnlyKeys("month"));
    }

    // ----- Calculation currency -----------------------------------------------

    @Test
    void calculationCurrencyIsTheFirstPreferredInCatalogOrderOrArs() {
        preferred(Currency.EUR, Currency.USD);
        assertThat(service.calculationCurrency(USER_ID)).isEqualTo(Currency.USD);

        preferred(Currency.BRL, Currency.ARS);
        assertThat(service.calculationCurrency(USER_ID)).isEqualTo(Currency.ARS);

        preferred();
        assertThat(service.calculationCurrency(USER_ID)).isEqualTo(Currency.ARS);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        assertThat(service.calculationCurrency(USER_ID)).isEqualTo(Currency.ARS);
    }

    // ----- Sums, percentages and order ----------------------------------------

    @Test
    void sumsEachKindByCategoryWithPercentagesOverItsOwnTotal() {
        preferred(Currency.ARS);
        personal(
                subtotal(ExpenseCategory.FOOD, Currency.ARS, "2026-11-03", "20000.00", 2),
                subtotal(ExpenseCategory.HOUSING, Currency.ARS, "2026-11-02", "45000.00", 1),
                subtotal(ExpenseCategory.FOOD, Currency.ARS, "2026-11-09", "15000.00", 1),
                subtotal(ExpenseCategory.TRANSPORT, Currency.ARS, "2026-11-10", "20000.00", 3));
        shares(
                subtotal(ExpenseCategory.FOOD, Currency.ARS, "2026-11-10", "30000.00", 1),
                subtotal(ExpenseCategory.LEISURE, Currency.ARS, "2026-11-12", "30000.00", 1));

        ExpenseStatisticsResponse response = november();

        assertThat(response.year()).isEqualTo(2026);
        assertThat(response.month()).isEqualTo(11);
        assertThat(response.currency()).isEqualTo(Currency.ARS);
        assertThat(response.total()).isEqualTo("160000.00");
        assertThat(response.excludedCount()).isZero();
        assertThat(response.years()).containsExactly(2026, 2024);
        assertThat(response.personal().total()).isEqualTo("100000.00");
        assertThat(response.personal().categories()).containsExactly(
                new ExpenseStatisticsResponse.CategoryAmount(ExpenseCategory.HOUSING, "45000.00", "45.0"),
                new ExpenseStatisticsResponse.CategoryAmount(ExpenseCategory.FOOD, "35000.00", "35.0"),
                new ExpenseStatisticsResponse.CategoryAmount(ExpenseCategory.TRANSPORT, "20000.00", "20.0"));
        // Same amount: catalog order (FOOD before LEISURE).
        assertThat(response.group().total()).isEqualTo("60000.00");
        assertThat(response.group().categories()).containsExactly(
                new ExpenseStatisticsResponse.CategoryAmount(ExpenseCategory.FOOD, "30000.00", "50.0"),
                new ExpenseStatisticsResponse.CategoryAmount(ExpenseCategory.LEISURE, "30000.00", "50.0"));
    }

    @Test
    void emptyKindsHaveZeroTotalAndNoCategories() {
        preferred();
        personal();
        shares();

        ExpenseStatisticsResponse response = november();

        assertThat(response.total()).isEqualTo("0.00");
        assertThat(response.personal()).isEqualTo(new ExpenseStatisticsResponse.Breakdown("0.00", List.of()));
        assertThat(response.group()).isEqualTo(new ExpenseStatisticsResponse.Breakdown("0.00", List.of()));
    }

    @Test
    void roundsAtTheEndOverUnroundedSums() {
        preferred(Currency.ARS);
        quote(Currency.USD, Currency.ARS, "2026-11-04", "0.333");
        quote(Currency.USD, Currency.ARS, "2026-11-05", "0.333");
        // 0.333 + 0.333 = 0.666 -> 0.67 (rounding each first would give 0.33 + 0.33 = 0.66).
        personal(
                subtotal(ExpenseCategory.FOOD, Currency.USD, "2026-11-04", "1.00", 1),
                subtotal(ExpenseCategory.FOOD, Currency.USD, "2026-11-05", "1.00", 1),
                subtotal(ExpenseCategory.PETS, Currency.ARS, "2026-11-05", "2.00", 1));
        shares();

        ExpenseStatisticsResponse response = november();

        assertThat(response.personal().total()).isEqualTo("2.67");
        assertThat(response.personal().categories()).containsExactly(
                new ExpenseStatisticsResponse.CategoryAmount(ExpenseCategory.PETS, "2.00", "75.0"),
                new ExpenseStatisticsResponse.CategoryAmount(ExpenseCategory.FOOD, "0.67", "25.0"));
    }

    // ----- Conversion ---------------------------------------------------------

    @Test
    void convertsOtherCurrenciesWithTheQuoteOfTheirDateAndAsksEachQuoteOnce() {
        preferred(Currency.ARS);
        quote(Currency.USD, Currency.ARS, "2026-11-04", "1000");
        quote(Currency.EUR, Currency.ARS, "2026-11-04", "1200");
        personal(
                subtotal(ExpenseCategory.FOOD, Currency.ARS, "2026-11-04", "10000.00", 1),
                subtotal(ExpenseCategory.LEISURE, Currency.USD, "2026-11-04", "100.00", 1),
                subtotal(ExpenseCategory.SHOPPING, Currency.USD, "2026-11-04", "5.00", 1));
        shares(subtotal(ExpenseCategory.FOOD, Currency.EUR, "2026-11-04", "50.00", 1));

        ExpenseStatisticsResponse response = november();

        assertThat(response.personal().total()).isEqualTo("115000.00");
        assertThat(response.personal().categories()).containsExactly(
                new ExpenseStatisticsResponse.CategoryAmount(ExpenseCategory.LEISURE, "100000.00", "87.0"),
                new ExpenseStatisticsResponse.CategoryAmount(ExpenseCategory.FOOD, "10000.00", "8.7"),
                new ExpenseStatisticsResponse.CategoryAmount(ExpenseCategory.SHOPPING, "5000.00", "4.3"));
        assertThat(response.group().categories()).containsExactly(
                new ExpenseStatisticsResponse.CategoryAmount(ExpenseCategory.FOOD, "60000.00", "100.0"));
        assertThat(response.total()).isEqualTo("175000.00");
        verify(rates, times(1)).getQuote(Currency.USD, Currency.ARS, LocalDate.parse("2026-11-04"));
        verify(rates, times(0)).getQuote(eq(Currency.ARS), any(), any());
    }

    @Test
    void usesTheFirstPreferredCurrencyForBothKinds() {
        preferred(Currency.EUR, Currency.USD);
        quote(Currency.ARS, Currency.USD, "2026-11-04", "0.001");
        personal(subtotal(ExpenseCategory.FOOD, Currency.ARS, "2026-11-04", "10000.00", 1));
        shares(subtotal(ExpenseCategory.FOOD, Currency.USD, "2026-11-06", "5.00", 1));

        ExpenseStatisticsResponse response = november();

        assertThat(response.currency()).isEqualTo(Currency.USD);
        assertThat(response.personal().total()).isEqualTo("10.00");
        assertThat(response.group().total()).isEqualTo("5.00");
        assertThat(response.total()).isEqualTo("15.00");
    }

    @Test
    void excludesSubtotalsWithoutQuoteAndCountsTheirExpenses() {
        preferred(Currency.ARS);
        when(rates.getQuote(eq(Currency.EUR), eq(Currency.ARS), any()))
                .thenThrow(new ExchangeRateUnavailableException());
        personal(
                subtotal(ExpenseCategory.FOOD, Currency.ARS, "2026-11-04", "100.00", 1),
                subtotal(ExpenseCategory.LEISURE, Currency.EUR, "2026-11-04", "10.00", 2),
                subtotal(ExpenseCategory.LEISURE, Currency.EUR, "2026-11-04", "5.00", 1));
        shares(subtotal(ExpenseCategory.HEALTH, Currency.EUR, "2026-11-08", "7.00", 1));

        ExpenseStatisticsResponse response = november();

        assertThat(response.excludedCount()).isEqualTo(4);
        assertThat(response.personal().categories()).containsExactly(
                new ExpenseStatisticsResponse.CategoryAmount(ExpenseCategory.FOOD, "100.00", "100.0"));
        assertThat(response.group()).isEqualTo(new ExpenseStatisticsResponse.Breakdown("0.00", List.of()));
        // The missing quote is asked once per (currency, date), even if it failed.
        verify(rates, times(1)).getQuote(Currency.EUR, Currency.ARS, LocalDate.parse("2026-11-04"));
    }
}
