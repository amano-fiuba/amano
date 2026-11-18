package com.amano.backend.statistics;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.amano.backend.income.IncomeCategory;
import com.amano.backend.user.Currency;

/**
 * Sum of the amounts of one income category, in one currency, on one income date
 * (specs/034-income-statistics, plan D4). Grouped by date because the exchange
 * rate depends on it; {@code count} is reported when the subtotal cannot be converted.
 */
public record IncomeCategorySubtotal(IncomeCategory category, Currency currency, LocalDate date,
        BigDecimal amount, Long count) {
}
