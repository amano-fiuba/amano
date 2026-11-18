package com.amano.backend.statistics;

import java.util.List;

import com.amano.backend.income.IncomeCategory;

/**
 * Income block of the statistics summary (specs/034-income-statistics,
 * contracts/statistics-summary-api.md): the converted total, how many incomes were
 * left out for lack of a quote, and the categories with amount, largest first.
 * Amounts have two decimals and percentages one.
 */
public record IncomeStatisticsResponse(String total, long excludedCount,
        List<CategoryAmount> categories) {

    /** Amount of one income category and its share of the total. */
    public record CategoryAmount(IncomeCategory category, String amount, String percentage) {
    }
}
