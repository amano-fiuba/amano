package com.amano.backend.expense;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Public representation of an expense (contracts/expenses-api.md). The amount is
 * always the canonical decimal string with two decimals, so no floating point is
 * involved on either side (FR-023/SC-004). {@code description} is omitted when
 * absent, following the same NON_NULL policy as {@code UserAccountResponse}.
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
        OffsetDateTime createdAt) {

    public static ExpenseResponse from(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getAmount().toPlainString(),
                expense.getCurrency().name(),
                expense.getExpenseDate().toString(),
                expense.getCategory().name(),
                expense.getType().name(),
                expense.getPaymentMethod().name(),
                expense.getDescription(),
                expense.getCreatedAt());
    }
}
