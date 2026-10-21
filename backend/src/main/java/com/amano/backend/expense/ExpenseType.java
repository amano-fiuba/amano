package com.amano.backend.expense;

/**
 * Closed expense type catalog (FR-010, data-model.md): recurring or one-time.
 * Persisted by its stable code; the Spanish labels live in the frontend.
 */
public enum ExpenseType {
    RECURRING,
    ONE_TIME
}
