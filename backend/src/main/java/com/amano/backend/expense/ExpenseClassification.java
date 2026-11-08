package com.amano.backend.expense;

import java.util.Map;

/**
 * Category, type and payment method of an expense, parsed with the 007 messages.
 * Shared by personal expenses (007) and, since 027 (FR-020), by group and friend
 * expenses. Any field may be null when its raw value was missing or invalid; the
 * error is then in {@code fieldErrors}.
 */
public record ExpenseClassification(ExpenseCategory category, ExpenseType type, PaymentMethod paymentMethod) {

    public static final String CATEGORY_REQUIRED = "La categoría es obligatoria";
    public static final String CATEGORY_INVALID = "La categoría no es válida";
    public static final String TYPE_REQUIRED = "El tipo de gasto es obligatorio";
    public static final String TYPE_INVALID = "El tipo de gasto no es válido";
    public static final String PAYMENT_REQUIRED = "El medio de pago es obligatorio";
    public static final String PAYMENT_INVALID = "El medio de pago no es válido";

    /** Parses the three raw values; every problem is added to {@code fieldErrors}. */
    public static ExpenseClassification parse(String category, String type, String paymentMethod,
            Map<String, String> fieldErrors) {
        return new ExpenseClassification(
                parseCatalog(category, ExpenseCategory.class, "category", CATEGORY_REQUIRED, CATEGORY_INVALID,
                        fieldErrors),
                parseCatalog(type, ExpenseType.class, "type", TYPE_REQUIRED, TYPE_INVALID, fieldErrors),
                parseCatalog(paymentMethod, PaymentMethod.class, "paymentMethod", PAYMENT_REQUIRED,
                        PAYMENT_INVALID, fieldErrors));
    }

    /** Closed-catalog value: missing → {@code requiredMessage}, unknown → {@code invalidMessage}. */
    public static <E extends Enum<E>> E parseCatalog(String raw, Class<E> type, String field,
            String requiredMessage, String invalidMessage, Map<String, String> fieldErrors) {
        if (raw == null || raw.isBlank()) {
            fieldErrors.put(field, requiredMessage);
            return null;
        }
        try {
            return Enum.valueOf(type, raw);
        }
        catch (IllegalArgumentException exception) {
            fieldErrors.put(field, invalidMessage);
            return null;
        }
    }
}
