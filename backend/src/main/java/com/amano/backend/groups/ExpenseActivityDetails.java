package com.amano.backend.groups;

import java.math.BigDecimal;

/**
 * Data of a group expense for the {@code EXPENSE_CREATED} history event (014
 * US2, data-model.md). Used by the future group-expenses module through
 * {@link ActivityEventService#recordExpenseCreated}.
 *
 * @param expenseId   id of the expense (optional)
 * @param amount      amount, &gt; 0 (required)
 * @param currency    ISO 4217 code (required)
 * @param description optional description
 */
public record ExpenseActivityDetails(Long expenseId, BigDecimal amount, String currency, String description) {
}
