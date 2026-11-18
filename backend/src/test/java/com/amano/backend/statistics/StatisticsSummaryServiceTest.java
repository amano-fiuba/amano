package com.amano.backend.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.amano.backend.expense.ExpenseCategory;
import com.amano.backend.expense.ExpenseStatisticsResponse;
import com.amano.backend.expense.ExpenseStatisticsService;
import com.amano.backend.income.IncomeCategory;
import com.amano.backend.income.IncomeRepository;
import com.amano.backend.user.Currency;

/** Unit tests of the composition of the statistics screen (specs/034-income-statistics). */
class StatisticsSummaryServiceTest {

    private static final Long USER_ID = 7L;

    private ExpenseStatisticsService expenseService;
    private IncomeStatisticsService incomeService;
    private IncomeRepository incomeRepository;
    private StatisticsSummaryService service;

    @BeforeEach
    void setUp() {
        expenseService = Mockito.mock(ExpenseStatisticsService.class);
        incomeService = Mockito.mock(IncomeStatisticsService.class);
        incomeRepository = Mockito.mock(IncomeRepository.class);
        service = new StatisticsSummaryService(expenseService, incomeService, incomeRepository);
        when(incomeRepository.findReceivedIncomeYears(any(), any())).thenReturn(List.of());
    }

    private void expenses(String personal, String group, long excluded, Integer... years) {
        String total = new java.math.BigDecimal(personal).add(new java.math.BigDecimal(group))
                .toPlainString();
        when(expenseService.statistics(USER_ID, "2026", "11")).thenReturn(
                new ExpenseStatisticsResponse(2026, 11, Currency.USD, total, excluded,
                        breakdown(personal), breakdown(group), List.of(years)));
    }

    private static ExpenseStatisticsResponse.Breakdown breakdown(String total) {
        return new ExpenseStatisticsResponse.Breakdown(total,
                "0.00".equals(total) ? List.of() : List.of(
                        new ExpenseStatisticsResponse.CategoryAmount(ExpenseCategory.FOOD, total, "100.0")));
    }

    private void income(String total, long excluded) {
        when(incomeService.statistics(USER_ID, YearMonth.of(2026, 11), Currency.USD)).thenReturn(
                new IncomeStatisticsResponse(total, excluded, "0.00".equals(total) ? List.of() : List.of(
                        new IncomeStatisticsResponse.CategoryAmount(IncomeCategory.SALARY, total, "100.0"))));
    }

    @Test
    void composesExpensesAndIncomeInTheSingleCalculationCurrency() {
        expenses("100000.00", "60000.00", 2, 2026);
        income("1000000.00", 1);

        StatisticsSummaryResponse response = service.summary(USER_ID, "2026", "11");

        assertThat(response.year()).isEqualTo(2026);
        assertThat(response.month()).isEqualTo(11);
        assertThat(response.currency()).isEqualTo(Currency.USD);
        assertThat(response.expenses().total()).isEqualTo("160000.00");
        assertThat(response.expenses().excludedCount()).isEqualTo(2);
        assertThat(response.expenses().personal().total()).isEqualTo("100000.00");
        assertThat(response.expenses().group().total()).isEqualTo("60000.00");
        assertThat(response.income().total()).isEqualTo("1000000.00");
        assertThat(response.income().excludedCount()).isEqualTo(1);
        verify(incomeService).statistics(USER_ID, YearMonth.of(2026, 11), Currency.USD);
    }

    @Test
    void balanceIsIncomeMinusExpensesFromTheRoundedTotals() {
        expenses("100000.00", "60000.00", 0, 2026);
        income("1000000.00", 0);

        StatisticsSummaryResponse.Comparison comparison = service.summary(USER_ID, "2026", "11").comparison();

        assertThat(comparison.income()).isEqualTo("1000000.00");
        assertThat(comparison.expenses()).isEqualTo("160000.00");
        assertThat(comparison.balance()).isEqualTo("840000.00");
    }

    @Test
    void negativeBalanceKeepsItsSign() {
        expenses("160000.00", "0.00", 0, 2026);
        income("0.00", 0);

        assertThat(service.summary(USER_ID, "2026", "11").comparison().balance()).isEqualTo("-160000.00");
    }

    @Test
    void zeroBalanceHasTwoDecimals() {
        expenses("0.00", "0.00", 0);
        income("0.00", 0);

        StatisticsSummaryResponse.Comparison comparison = service.summary(USER_ID, "2026", "11").comparison();

        assertThat(comparison.balance()).isEqualTo("0.00");
    }

    @Test
    void yearsUniteExpenseAndReceivedIncomeYearsWithoutDuplicatesNewestFirst() {
        expenses("10.00", "0.00", 0, 2026, 2024);
        income("0.00", 0);
        when(incomeRepository.findReceivedIncomeYears(any(), any())).thenReturn(List.of(2026, 2025));

        assertThat(service.summary(USER_ID, "2026", "11").years()).containsExactly(2026, 2025, 2024);
    }

    @Test
    void yearsAreAskedForReceivedIncomesOnly() {
        expenses("0.00", "0.00", 0);
        income("0.00", 0);

        service.summary(USER_ID, "2026", "11");

        verify(incomeRepository).findReceivedIncomeYears(any(), any());
        assertThat(service.summary(USER_ID, "2026", "11").years()).isEmpty();
    }
}
