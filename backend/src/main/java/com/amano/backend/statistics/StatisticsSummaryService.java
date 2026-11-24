package com.amano.backend.statistics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.expense.ExpenseStatisticsResponse;
import com.amano.backend.expense.ExpenseStatisticsService;
import com.amano.backend.income.IncomeRepository;

/**
 * Composes the statistics screen (specs/034-income-statistics, plan D2, D6..D8):
 * the expenses of 032, the received incomes, their comparison and the unified years.
 */
@Service
public class StatisticsSummaryService {

    private final ExpenseStatisticsService expenseStatisticsService;
    private final IncomeStatisticsService incomeStatisticsService;
    private final IncomeRepository incomeRepository;

    public StatisticsSummaryService(ExpenseStatisticsService expenseStatisticsService,
            IncomeStatisticsService incomeStatisticsService, IncomeRepository incomeRepository) {
        this.expenseStatisticsService = expenseStatisticsService;
        this.incomeStatisticsService = incomeStatisticsService;
        this.incomeRepository = incomeRepository;
    }

    @Transactional(readOnly = true)
    public StatisticsSummaryResponse summary(Long userId, String rawYear, String rawMonth) {
        // Validates the period and fixes the single calculation currency. A blank month
        // spans the whole year for both kinds of movements.
        StatisticsPeriod period = StatisticsPeriod.of(rawYear, rawMonth);
        ExpenseStatisticsResponse expenses = expenseStatisticsService.statistics(userId, period);
        IncomeStatisticsResponse income = incomeStatisticsService.statistics(userId, period.start(),
                period.end(), expenses.currency());

        // The balance uses the rounded totals, so it matches what is shown (plan D7).
        BigDecimal balance = new BigDecimal(income.total()).subtract(new BigDecimal(expenses.total()));
        StatisticsSummaryResponse.Comparison comparison = new StatisticsSummaryResponse.Comparison(
                income.total(), expenses.total(),
                balance.setScale(2, RoundingMode.HALF_UP).toPlainString());

        List<Integer> years = Stream.concat(expenses.years().stream(),
                        incomeRepository.findReceivedIncomeYears(userId,
                                LocalDate.now(ZoneOffset.UTC)).stream())
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();

        return new StatisticsSummaryResponse(period.year(), period.month(), expenses.currency(),
                new StatisticsSummaryResponse.ExpensesSummary(expenses.total(),
                        expenses.excludedCount(), expenses.personal(), expenses.group()),
                income, comparison, years);
    }
}
