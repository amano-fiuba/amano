package com.amano.backend.expense;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Public representation of an expense (contracts/expenses-api.md). The amount is
 * always the canonical decimal string with two decimals, so no floating point is
 * involved on either side (FR-023/SC-004). {@code description} is omitted when
 * absent, following the same NON_NULL policy as {@code UserAccountResponse}.
 * Feature 009 appends {@code frequency}, {@code status} and
 * {@code nextOccurrenceDate}, present only on recurring series, so a one-time
 * expense keeps the exact 007 shape.
 *
 * <p>The personal activity screen also wants the amount in the user's preferred
 * currencies: {@link #conversions} is only attached by
 * {@link ExpenseService#listHistory(Long)} and stays {@code null} (thus omitted)
 * everywhere else, so the other responses keep their previous shape.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExpenseResponse(
        Long id,
        String amount,
        String currency,
        String date,
        String category,
        String type,
        String paymentMethod,
        String description,
        OffsetDateTime createdAt,
        String frequency,
        String status,
        String nextOccurrenceDate,
        List<Conversion> conversions) {

    /** One preferred-currency equivalent of an expense, canonical 2-decimal string. */
    public record Conversion(String currency, String amount) {
    }

    public static ExpenseResponse from(Expense expense) {
        return from(expense, null);
    }

    public static ExpenseResponse from(Expense expense, List<Conversion> conversions) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getAmount().toPlainString(),
                expense.getCurrency().name(),
                expense.getExpenseDate().toString(),
                expense.getCategory().name(),
                expense.getType().name(),
                expense.getPaymentMethod().name(),
                expense.getDescription(),
                expense.getCreatedAt(),
                nameOf(expense.getRecurrenceFrequency()),
                nameOf(expense.getRecurrenceStatus()),
                expense.getRecurrenceNextDate() == null
                        ? null : expense.getRecurrenceNextDate().toString(),
                conversions);
    }

    private static String nameOf(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
