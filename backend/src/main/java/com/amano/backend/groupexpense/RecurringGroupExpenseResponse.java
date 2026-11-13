package com.amano.backend.groupexpense;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.amano.backend.expense.RecurrenceStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 028/029: one recurring series of a group for the management screen. Codes match
 * the enums; the frontend maps them to Spanish labels. Only series are listed
 * ({@code frequency != null}), with their next scheduled date, next due date and
 * the amount accumulated from generated occurrences.
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
        String nextOccurrenceDate,
        String accumulatedAmount,
        String nextDueDate) {

    public static RecurringGroupExpenseResponse from(GroupExpense expense) {
        return from(expense, 0, 0L);
    }

    public static RecurringGroupExpenseResponse from(GroupExpense expense, int debtDueDays, long occurrenceCount) {
        String nextDate = nextOccurrenceDateOf(expense);
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
                nextDate,
                accumulatedAmountOf(expense, occurrenceCount),
                nextDueDateOf(expense, debtDueDays, occurrenceCount));
    }

    private static String nameOf(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static String nextOccurrenceDateOf(GroupExpense expense) {
        if (expense.getRecurrenceStatus() == null
                || expense.getRecurrenceStatus() == RecurrenceStatus.PAUSED
                || expense.getRecurrenceStatus() == RecurrenceStatus.FINISHED) {
            return null;
        }
        return expense.getRecurrenceNextDate() == null
                ? null : expense.getRecurrenceNextDate().toString();
    }

    private static String accumulatedAmountOf(GroupExpense expense, long occurrenceCount) {
        return expense.getAmount().multiply(BigDecimal.valueOf(occurrenceCount)).toPlainString();
    }

    private static String nextDueDateOf(GroupExpense expense, int debtDueDays, long occurrenceCount) {
        if (expense.getRecurrenceStatus() != RecurrenceStatus.ACTIVE
                || expense.getRecurrenceNextDate() == null) {
            return null;
        }
        LocalDate due = expense.getRecurrenceNextDate();
        if (occurrenceCount == 0 && debtDueDays > 0) {
            due = due.plusDays(debtDueDays);
        }
        return due.toString();
    }
}
