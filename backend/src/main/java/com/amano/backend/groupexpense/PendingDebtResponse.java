package com.amano.backend.groupexpense;

import java.math.BigDecimal;

import com.amano.backend.user.Currency;

/**
 * One open debt of the caller in a group (022 US7, T043/T044). {@code amount}
 * and {@code currency} are the original debt values; {@code convertedAmount}
 * and {@code convertedCurrency} are present only when the origin expense has a
 * stored conversion, so the UI can show the original and converted amounts
 * together without inventing a rate.
 */
public record PendingDebtResponse(
        Long debtId,
        String debtorName,
        String creditorName,
        BigDecimal amount,
        BigDecimal paidAmount,
        BigDecimal outstanding,
        DebtStatus status,
        Long expenseId,
        Long shareId,
        Currency currency,
        BigDecimal convertedAmount,
        Currency convertedCurrency) {
}
