package com.amano.backend.expense;

import java.util.List;

import com.amano.backend.user.Currency;

/**
 * Statistics of a period (specs/032-estadisticas-gastos,
 * contracts/expense-statistics-api.md): the calculation currency, the combined
 * total, how many expenses were left out for lack of a quote, the breakdown by
 * category of the personal expenses and of the person's group shares, and the
 * years with expenses for the selector. A null month means the whole year. Amounts
 * are strings with two decimals and percentages strings with one decimal (FR-011).
 */
public record ExpenseStatisticsResponse(int year, Integer month, Currency currency, String total,
        long excludedCount, Breakdown personal, Breakdown group, List<Integer> years) {

    /** Total of one kind of expense and its categories, largest amount first (FR-012). */
    public record Breakdown(String total, List<CategoryAmount> categories) {
    }

    /** Amount of one category and its share of the total of its kind. */
    public record CategoryAmount(ExpenseCategory category, String amount, String percentage) {
    }
}
