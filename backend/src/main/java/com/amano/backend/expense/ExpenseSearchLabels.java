package com.amano.backend.expense;

import java.text.Normalizer;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Spanish labels of the category and payment method catalogs, used by the personal
 * activity search to match what the user sees (031, FR-003). They mirror
 * {@code frontend/src/validation/expenseCatalog.js}, where the labels are defined
 * for the UI; both lists must stay in sync.
 */
public final class ExpenseSearchLabels {

    static final Map<ExpenseCategory, String> CATEGORY_LABELS = Map.of(
            ExpenseCategory.FOOD, "Alimentos",
            ExpenseCategory.TRANSPORT, "Transporte",
            ExpenseCategory.HOUSING, "Vivienda",
            ExpenseCategory.SERVICES, "Servicios",
            ExpenseCategory.HEALTH, "Salud",
            ExpenseCategory.EDUCATION, "Educación",
            ExpenseCategory.LEISURE, "Ocio",
            ExpenseCategory.SHOPPING, "Compras",
            ExpenseCategory.PETS, "Mascotas",
            ExpenseCategory.OTHER, "Otros");

    static final Map<PaymentMethod, String> PAYMENT_METHOD_LABELS = Map.of(
            PaymentMethod.CASH, "Efectivo",
            PaymentMethod.DEBIT_CARD, "Tarjeta de débito",
            PaymentMethod.CREDIT_CARD, "Tarjeta de crédito",
            PaymentMethod.TRANSFER, "Transferencia",
            PaymentMethod.VIRTUAL_WALLET, "Billetera virtual",
            PaymentMethod.OTHER, "Otro");

    private ExpenseSearchLabels() {
    }

    /** Lowercase without diacritics, the form both sides of the comparison share (FR-004). */
    public static String normalize(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /** Categories whose label contains the already normalized term. */
    public static Set<ExpenseCategory> categoriesMatching(String normalizedTerm) {
        Set<ExpenseCategory> matches = EnumSet.noneOf(ExpenseCategory.class);
        CATEGORY_LABELS.forEach((category, label) -> {
            if (normalize(label).contains(normalizedTerm)) {
                matches.add(category);
            }
        });
        return matches;
    }

    /** Payment methods whose label contains the already normalized term. */
    public static Set<PaymentMethod> paymentMethodsMatching(String normalizedTerm) {
        Set<PaymentMethod> matches = EnumSet.noneOf(PaymentMethod.class);
        PAYMENT_METHOD_LABELS.forEach((method, label) -> {
            if (normalize(label).contains(normalizedTerm)) {
                matches.add(method);
            }
        });
        return matches;
    }
}
