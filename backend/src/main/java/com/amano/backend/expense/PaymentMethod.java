package com.amano.backend.expense;

/**
 * Closed payment method catalog (FR-011, data-model.md). Persisted by its stable
 * code; the Spanish labels live only in the frontend ({@code expenseCatalog.js}).
 */
public enum PaymentMethod {
    CASH,
    DEBIT_CARD,
    CREDIT_CARD,
    TRANSFER,
    VIRTUAL_WALLET,
    OTHER
}
