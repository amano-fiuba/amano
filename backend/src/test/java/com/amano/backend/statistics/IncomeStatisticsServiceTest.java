package com.amano.backend.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.amano.backend.groupexpense.exchange.ExchangeRateProvider;
import com.amano.backend.groupexpense.exchange.ExchangeRateQuote;
import com.amano.backend.groupexpense.exchange.ExchangeRateUnavailableException;
import com.amano.backend.income.IncomeCategory;
import com.amano.backend.income.IncomeRepository;
import com.amano.backend.user.Currency;

/** Unit tests of the received-income statistics (specs/034-income-statistics, FR-008..FR-013). */
class IncomeStatisticsServiceTest {

    private static final Long USER_ID = 7L;
    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);

    private IncomeRepository repository;
    private ExchangeRateProvider rates;
    private IncomeStatisticsService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(IncomeRepository.class);
        rates = Mockito.mock(ExchangeRateProvider.class);
        service = new IncomeStatisticsService(repository, rates);
    }

    private static IncomeCategorySubtotal subtotal(IncomeCategory category, Currency currency,
            String date, String amount, long count) {
        return new IncomeCategorySubtotal(category, currency, LocalDate.parse(date),
                new BigDecimal(amount), count);
    }

    private void subtotals(IncomeCategorySubtotal... subtotals) {
        when(repository.sumByCategoryCurrencyAndDate(eq(USER_ID), eq(LocalDate.parse("2026-11-01")),
                eq(LocalDate.parse("2026-11-30")), any())).thenReturn(List.of(subtotals));
    }

    private void quote(Currency from, Currency to, String date, String rate) {
        when(rates.getQuote(from, to, LocalDate.parse(date)))
                .thenReturn(new ExchangeRateQuote(new BigDecimal(rate), LocalDate.parse(date), "BCRA"));
    }

    @Test
    void asksOnlyForTheMonthAndForIncomesReceivedUntilTodayUtc() {
        subtotals();

        service.statistics(USER_ID, NOVEMBER, Currency.ARS);

        verify(repository).sumByCategoryCurrencyAndDate(USER_ID, LocalDate.parse("2026-11-01"),
                LocalDate.parse("2026-11-30"), LocalDate.now(ZoneOffset.UTC));
    }

    @Test
    void sumsByCategoryWithPercentagesAndLargestFirst() {
        subtotals(
                subtotal(IncomeCategory.SALARY, Currency.ARS, "2026-11-05", "700000.00", 1),
                subtotal(IncomeCategory.FREELANCE, Currency.ARS, "2026-11-10", "150000.00", 1),
                subtotal(IncomeCategory.SALARY, Currency.ARS, "2026-11-20", "100000.00", 1),
                subtotal(IncomeCategory.SALE, Currency.ARS, "2026-11-21", "50000.00", 1));

        IncomeStatisticsResponse response = service.statistics(USER_ID, NOVEMBER, Currency.ARS);

        assertThat(response.total()).isEqualTo("1000000.00");
        assertThat(response.excludedCount()).isZero();
        assertThat(response.categories()).containsExactly(
                new IncomeStatisticsResponse.CategoryAmount(IncomeCategory.SALARY, "800000.00", "80.0"),
                new IncomeStatisticsResponse.CategoryAmount(IncomeCategory.FREELANCE, "150000.00", "15.0"),
                new IncomeStatisticsResponse.CategoryAmount(IncomeCategory.SALE, "50000.00", "5.0"));
    }

    @Test
    void tiesAreBrokenByCatalogOrder() {
        subtotals(
                subtotal(IncomeCategory.OTHER, Currency.ARS, "2026-11-05", "100.00", 1),
                subtotal(IncomeCategory.REFUND, Currency.ARS, "2026-11-05", "100.00", 1),
                subtotal(IncomeCategory.SALARY, Currency.ARS, "2026-11-05", "100.00", 1));

        IncomeStatisticsResponse response = service.statistics(USER_ID, NOVEMBER, Currency.ARS);

        assertThat(response.categories()).extracting(IncomeStatisticsResponse.CategoryAmount::category)
                .containsExactly(IncomeCategory.SALARY, IncomeCategory.REFUND, IncomeCategory.OTHER);
        assertThat(response.categories()).extracting(IncomeStatisticsResponse.CategoryAmount::percentage)
                .containsOnly("33.3");
    }

    @Test
    void noIncomesGivesZeroTotalAndNoCategories() {
        subtotals();

        IncomeStatisticsResponse response = service.statistics(USER_ID, NOVEMBER, Currency.ARS);

        assertThat(response).isEqualTo(new IncomeStatisticsResponse("0.00", 0, List.of()));
    }

    @Test
    void convertsWithTheQuoteOfEachIncomeDateAndAsksEachQuoteOnce() {
        quote(Currency.USD, Currency.ARS, "2026-11-04", "1000");
        quote(Currency.USD, Currency.ARS, "2026-11-18", "1100");
        subtotals(
                subtotal(IncomeCategory.FREELANCE, Currency.USD, "2026-11-04", "100.00", 1),
                subtotal(IncomeCategory.SALE, Currency.USD, "2026-11-04", "50.00", 1),
                subtotal(IncomeCategory.FREELANCE, Currency.USD, "2026-11-18", "100.00", 1),
                subtotal(IncomeCategory.SALARY, Currency.ARS, "2026-11-05", "10000.00", 1));

        IncomeStatisticsResponse response = service.statistics(USER_ID, NOVEMBER, Currency.ARS);

        assertThat(response.total()).isEqualTo("270000.00");
        assertThat(response.categories()).containsExactly(
                new IncomeStatisticsResponse.CategoryAmount(IncomeCategory.FREELANCE, "210000.00", "77.8"),
                new IncomeStatisticsResponse.CategoryAmount(IncomeCategory.SALE, "50000.00", "18.5"),
                new IncomeStatisticsResponse.CategoryAmount(IncomeCategory.SALARY, "10000.00", "3.7"));
        verify(rates, times(1)).getQuote(Currency.USD, Currency.ARS, LocalDate.parse("2026-11-04"));
        verify(rates, times(0)).getQuote(eq(Currency.ARS), any(), any());
    }

    @Test
    void anIncomeAlreadyInTheCalculationCurrencyIsNotConverted() {
        subtotals(subtotal(IncomeCategory.SALARY, Currency.USD, "2026-11-05", "10.00", 1));

        IncomeStatisticsResponse response = service.statistics(USER_ID, NOVEMBER, Currency.USD);

        assertThat(response.total()).isEqualTo("10.00");
        Mockito.verifyNoInteractions(rates);
    }

    @Test
    void excludesIncomesWithoutQuoteAndCountsThem() {
        when(rates.getQuote(eq(Currency.EUR), eq(Currency.ARS), any()))
                .thenThrow(new ExchangeRateUnavailableException());
        subtotals(
                subtotal(IncomeCategory.SALARY, Currency.ARS, "2026-11-04", "100.00", 1),
                subtotal(IncomeCategory.SALE, Currency.EUR, "2026-11-04", "10.00", 2),
                subtotal(IncomeCategory.SALE, Currency.EUR, "2026-11-04", "5.00", 1),
                subtotal(IncomeCategory.REFUND, Currency.EUR, "2026-11-08", "7.00", 1));

        IncomeStatisticsResponse response = service.statistics(USER_ID, NOVEMBER, Currency.ARS);

        assertThat(response.excludedCount()).isEqualTo(4);
        assertThat(response.total()).isEqualTo("100.00");
        assertThat(response.categories()).containsExactly(
                new IncomeStatisticsResponse.CategoryAmount(IncomeCategory.SALARY, "100.00", "100.0"));
    }

    @Test
    void roundsAtTheEndOverUnroundedSums() {
        quote(Currency.USD, Currency.ARS, "2026-11-04", "0.333");
        quote(Currency.USD, Currency.ARS, "2026-11-05", "0.333");
        subtotals(
                subtotal(IncomeCategory.SALE, Currency.USD, "2026-11-04", "1.00", 1),
                subtotal(IncomeCategory.SALE, Currency.USD, "2026-11-05", "1.00", 1),
                subtotal(IncomeCategory.SALARY, Currency.ARS, "2026-11-05", "2.00", 1));

        IncomeStatisticsResponse response = service.statistics(USER_ID, NOVEMBER, Currency.ARS);

        assertThat(response.total()).isEqualTo("2.67");
        assertThat(response.categories()).containsExactly(
                new IncomeStatisticsResponse.CategoryAmount(IncomeCategory.SALARY, "2.00", "75.0"),
                new IncomeStatisticsResponse.CategoryAmount(IncomeCategory.SALE, "0.67", "25.0"));
    }

    @Test
    void dropsCategoriesWhoseAmountIsNotPositive() {
        subtotals(
                subtotal(IncomeCategory.SALARY, Currency.ARS, "2026-11-04", "100.00", 1),
                subtotal(IncomeCategory.SALE, Currency.ARS, "2026-11-04", "0.00", 1));

        IncomeStatisticsResponse response = service.statistics(USER_ID, NOVEMBER, Currency.ARS);

        assertThat(response.categories()).extracting(IncomeStatisticsResponse.CategoryAmount::category)
                .containsExactly(IncomeCategory.SALARY);
    }
}
