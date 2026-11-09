package com.amano.backend.groupexpense;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 028: one recurring series of a group for the management screen. Codes match
 * the enums; the frontend maps them to Spanish labels. Only series are listed
 * ({@code frequency != null}), with their next scheduled date.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecurringGroupExpenseResponse(
        Long id,
        String amount,
        String currency,
        String date,
        String category,
        String type,
        String paymentMethod,
        String description,
        String frequency,
        String status,
        String nextOccurrenceDate) {

    public static RecurringGroupExpenseResponse from(GroupExpense expense) {
        return new RecurringGroupExpenseResponse(
                expense.getId(),
                expense.getAmount().toPlainString(),
                expense.getCurrency().name(),
                expense.getExpenseDate().toString(),
                nameOf(expense.getCategory()),
                nameOf(expense.getExpenseType()),
                nameOf(expense.getPaymentMethod()),
                expense.getDescription(),
                nameOf(expense.getRecurrenceFrequency()),
                nameOf(expense.getRecurrenceStatus()),
                expense.getRecurrenceNextDate() == null
                        ? null : expense.getRecurrenceNextDate().toString());
    }

    private static String nameOf(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
