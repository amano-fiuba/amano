package com.amano.backend.expense;

import java.math.BigDecimal;
import java.util.Map;
import java.util.regex.Pattern;

import com.amano.backend.common.FieldValidationException;

/**
 * Canonical amount parsing for the backend (FR-013/FR-014/FR-023, research.md D2).
 * The API always receives the client already-parsed canonical decimal with a dot
 * and at most two decimals ({@code "10.10"}); Argentine notation (comma,
 * thousands separators) is a frontend concern. Values are rejected — never
 * rounded — when they are not numeric, have more than two decimals, are not
 * positive or fall outside {@code 0.01}–{@code 999999999.99}. Comparisons use
 * {@link BigDecimal#compareTo} so scale differences do not matter.
 */
public final class ExpenseAmountNormalizer {

    public static final BigDecimal MIN = new BigDecimal("0.01");
    public static final BigDecimal MAX = new BigDecimal("999999999.99");

    public static final String REQUIRED_MESSAGE = "El monto es obligatorio";
    public static final String FORMAT_MESSAGE = "Ingresá un monto válido";
    public static final String DECIMALS_MESSAGE = "El monto no puede tener más de dos decimales";
    public static final String POSITIVE_MESSAGE = "El monto debe ser mayor a cero";
    public static final String RANGE_MESSAGE = "El monto debe estar entre 0,01 y 999.999.999,99";

    /** Plain decimal only: no exponent, sign, comma or lone dot. */
    private static final Pattern CANONICAL = Pattern.compile("^\\d+(\\.\\d+)?$");

    private ExpenseAmountNormalizer() {
    }

    /** Parses and validates the canonical amount, throwing the field error when invalid. */
    public static BigDecimal normalize(String raw) {
        String error = validationError(raw);
        if (error != null) {
            throw new FieldValidationException(Map.of("amount", error));
        }
        return new BigDecimal(raw.trim());
    }

    /** Null when the canonical amount is valid, the Spanish message otherwise (FR-022). */
    public static String validationError(String raw) {
        if (raw == null || raw.isBlank()) {
            return REQUIRED_MESSAGE;
        }
        String text = raw.trim();
        if (!CANONICAL.matcher(text).matches()) {
            return FORMAT_MESSAGE;
        }
        BigDecimal value = new BigDecimal(text);
        if (value.scale() > 2) {
            return DECIMALS_MESSAGE;
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            return POSITIVE_MESSAGE;
        }
        if (value.compareTo(MIN) < 0 || value.compareTo(MAX) > 0) {
            return RANGE_MESSAGE;
        }
        return null;
    }
}
