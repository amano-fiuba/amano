package com.amano.backend.statistics;

import java.util.List;

import com.amano.backend.expense.ExpenseStatisticsResponse.Breakdown;
import com.amano.backend.user.Currency;

/**
 * One response for the whole statistics screen (specs/034-income-statistics,
 * contracts/statistics-summary-api.md): expenses, income, their comparison and the
 * years of the selector.
 */
public record StatisticsSummaryResponse(int year, int month, Currency currency,
        ExpensesSummary expenses, IncomeStatisticsResponse income, Comparison comparison,
        List<Integer> years) {

    /** Expenses of 032, decoupled from its response: exactly the contract fields. */
    public record ExpensesSummary(String total, long excludedCount, Breakdown personal,
            Breakdown group) {
    }

    /** Income against combined expenses; the balance is income minus expenses. */
    public record Comparison(String income, String expenses, String balance) {
    }
}
