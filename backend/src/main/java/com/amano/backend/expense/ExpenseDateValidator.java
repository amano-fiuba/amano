package com.amano.backend.expense;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import com.amano.backend.common.FieldValidationException;

/**
 * Expense date validation against the device time zone (FR-009/FR-016, research.md
 * D4). A missing or invalid IANA zone never fails the request: it applies a
 * one-day tolerance anchored at UTC, so a legitimate "today" of a time zone ahead
 * of the server is not rejected. The internal parse exception is never exposed
 * (SC-006).
 */
public final class ExpenseDateValidator {

    public static final String REQUIRED_MESSAGE = "La fecha es obligatoria";
    public static final String FUTURE_MESSAGE = "La fecha no puede ser posterior a hoy";

    private ExpenseDateValidator() {
    }

    /** Null when the date is not in the future, the Spanish message otherwise. */
    public static String validationError(LocalDate date, String deviceTimeZone) {
        if (date == null) {
            return REQUIRED_MESSAGE;
        }
        return date.isAfter(limitOf(deviceTimeZone)) ? FUTURE_MESSAGE : null;
    }

    /** Validates the date, throwing the field error when it is absent or in the future. */
    public static void validate(LocalDate date, String deviceTimeZone) {
        String error = validationError(date, deviceTimeZone);
        if (error != null) {
            throw new FieldValidationException(Map.of("date", error));
        }
    }

    /**
     * Latest accepted date: today in the informed zone, or tomorrow in UTC when
     * the zone is absent/invalid (one-day tolerance). An explicit offset such as
     * {@code +03:00} or {@code Z} is a valid {@link ZoneId} and is honored as-is;
     * only unparseable values fall back to the tolerance. Never throws.
     */
    public static LocalDate limitOf(String deviceTimeZone) {
        ZoneId zone;
        try {
            zone = ZoneId.of(deviceTimeZone);
        }
        catch (DateTimeException | NullPointerException ignored) {
            zone = null;
        }
        return zone == null ? LocalDate.now(ZoneOffset.UTC).plusDays(1) : LocalDate.now(zone);
    }
}
