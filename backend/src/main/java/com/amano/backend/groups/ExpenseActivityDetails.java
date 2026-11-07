package com.amano.backend.groups;

import java.math.BigDecimal;

/**
 * Data of a group expense for the {@code EXPENSE_CREATED} history event (014
 * US2, data-model.md; 022 D11, T061). Used by the group-expenses module through
 * {@link ActivityEventService#recordExpenseCreated}.
 *
 * @param expenseId   id of the expense (optional)
 * @param amount      amount, &gt; 0 (required)
 * @param currency    ISO 4217 code (required)
 * @param description optional description
 * @param payerUserId id of the member who paid (optional; null on pre-022 events)
 * @param payerName   visible name of the member who paid (optional; null on pre-022 events)
 */
public record ExpenseActivityDetails(Long expenseId, BigDecimal amount, String currency, String description,
        Long payerUserId, String payerName) {

    /** The 014 format, without the payer fields (kept for backward compatibility). */
    public ExpenseActivityDetails(Long expenseId, BigDecimal amount, String currency, String description) {
        this(expenseId, amount, currency, description, null, null);
    }
}
