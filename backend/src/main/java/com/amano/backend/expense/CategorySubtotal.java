package com.amano.backend.expense;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.amano.backend.user.Currency;

/**
 * Sum of the amounts of one category, in one currency, on one expense date
 * (specs/032-estadisticas-gastos, plan D2). Expenses are grouped by date because
 * the exchange rate depends on it; {@code count} is the number of expenses (or
 * group shares) summed, reported when the subtotal cannot be converted (FR-010).
 */
public record CategorySubtotal(ExpenseCategory category, Currency currency, LocalDate date,
        BigDecimal amount, Long count) {
}
