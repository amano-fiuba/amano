package com.amano.backend.income;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.amano.backend.expense.ExpenseSearchLabels;

/**
 * Spanish labels of the income category catalog, used by the income search to match
 * what the person sees (specs/033-income-management, FR-008). They mirror
 * {@code frontend/src/validation/expenseCatalog.js} ({@code INCOME_CATEGORY_OPTIONS});
 * both lists must stay in sync. The payment method is not searched (spec 033).
 */
public final class IncomeSearchLabels {

    static final Map<IncomeCategory, String> CATEGORY_LABELS = Map.of(
            IncomeCategory.SALARY, "Sueldo",
            IncomeCategory.FREELANCE, "Freelance",
            IncomeCategory.SALE, "Venta",
            IncomeCategory.REFUND, "Reembolso",
            IncomeCategory.OTHER, "Otros");

    private IncomeSearchLabels() {
    }

    /** Lowercase without diacritics, the same form as the expense search (031, FR-004). */
    public static String normalize(String text) {
        return ExpenseSearchLabels.normalize(text);
    }

    /** Categories whose label contains the already normalized term. */
    public static Set<IncomeCategory> categoriesMatching(String normalizedTerm) {
        Set<IncomeCategory> matches = EnumSet.noneOf(IncomeCategory.class);
        CATEGORY_LABELS.forEach((category, label) -> {
            if (normalize(label).contains(normalizedTerm)) {
                matches.add(category);
            }
        });
        return matches;
    }
}
