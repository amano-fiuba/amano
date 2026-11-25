package com.amano.backend.statistics;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

import com.amano.backend.common.FieldValidationException;

/**
 * Period of the statistics screens: a required year and an optional month. A blank
 * month means the whole year, so the charts can show "Todos los meses" as the
 * personal activity does. Both ends are closed dates for the queries, so no caller
 * has to deal with nullable bounds.
 */
public record StatisticsPeriod(int year, Integer month, LocalDate start, LocalDate end) {

    public static final String YEAR_INVALID = "El año no es válido";
    public static final String MONTH_INVALID = "El mes no es válido";

    /** Validates the raw parameters, accumulating every error into one 400. */
    public static StatisticsPeriod of(String rawYear, String rawMonth) {
        Map<String, String> errors = new LinkedHashMap<>();
        Integer year = integer(rawYear, 1, 9999, "year", YEAR_INVALID, errors);
        Integer month = integer(rawMonth, 1, 12, "month", MONTH_INVALID, errors);
        // The year is mandatory here, unlike the month: a blank year is an error.
        if (year == null && !errors.containsKey("year")) {
            errors.put("year", YEAR_INVALID);
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        if (month != null) {
            YearMonth yearMonth = YearMonth.of(year, month);
            start = yearMonth.atDay(1);
            end = yearMonth.atEndOfMonth();
        }
        return new StatisticsPeriod(year, month, start, end);
    }

    /** True when no month was chosen and the whole year is included. */
    public boolean allMonths() {
        return month == null;
    }

    /** Optional integer parameter within {@code [min, max]}; blank means absent. */
    private static Integer integer(String raw, int min, int max, String field, String message,
            Map<String, String> errors) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.strip());
            if (value >= min && value <= max) {
                return value;
            }
        }
        catch (NumberFormatException invalid) {
            // falls through to the error below
        }
        errors.put(field, message);
        return null;
    }
}
