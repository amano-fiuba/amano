package com.amano.backend.income;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

import com.amano.backend.common.FieldValidationException;

/**
 * Period filter shared by the income search and the personal balance
 * (specs/032-income-management, contracts/personal-incomes-api.md): an optional
 * {@code year}, a {@code month} of that year, and an optional {@code from}/{@code to}
 * date range (both included). The filters intersect. The result is always a closed
 * range of dates ({@link #start()} to {@link #end()}), with far bounds when nothing
 * restricts a side, so the queries never receive a {@code null} date.
 */
public record IncomePeriod(LocalDate start, LocalDate end) {

    static final String YEAR_INVALID = "El año no es válido";
    static final String MONTH_INVALID = "El mes no es válido";
    static final String MONTH_WITHOUT_YEAR = "Elegí un año para filtrar por mes";
    static final String DATE_INVALID = "La fecha no es válida";
    static final String FROM_AFTER_TO = "La fecha desde no puede ser posterior a la fecha hasta";

    private static final LocalDate MIN = LocalDate.of(1, 1, 1);
    private static final LocalDate MAX = LocalDate.of(9999, 12, 31);

    /** Validates the raw parameters, accumulating every error into one 400. */
    public static IncomePeriod of(String rawYear, String rawMonth, String rawFrom, String rawTo) {
        Map<String, String> errors = new LinkedHashMap<>();
        Integer year = integer(rawYear, 1, 9999, "year", YEAR_INVALID, errors);
        Integer month = integer(rawMonth, 1, 12, "month", MONTH_INVALID, errors);
        if (month != null && year == null && !errors.containsKey("year")) {
            errors.put("month", MONTH_WITHOUT_YEAR);
        }
        LocalDate from = date(rawFrom, "from", errors);
        LocalDate to = date(rawTo, "to", errors);
        if (from != null && to != null && from.isAfter(to)) {
            errors.put("from", FROM_AFTER_TO);
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }

        LocalDate start = MIN;
        LocalDate end = MAX;
        if (year != null) {
            if (month != null) {
                YearMonth yearMonth = YearMonth.of(year, month);
                start = yearMonth.atDay(1);
                end = yearMonth.atEndOfMonth();
            }
            else {
                start = LocalDate.of(year, 1, 1);
                end = LocalDate.of(year, 12, 31);
            }
        }
        if (from != null && from.isAfter(start)) {
            start = from;
        }
        if (to != null && to.isBefore(end)) {
            end = to;
        }
        return new IncomePeriod(start, end);
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
        catch (NumberFormatException ignored) {
            // falls through to the error below
        }
        errors.put(field, message);
        return null;
    }

    /** Optional {@code yyyy-MM-dd} date; blank means absent. */
    private static LocalDate date(String raw, String field, Map<String, String> errors) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.strip());
        }
        catch (DateTimeException invalid) {
            errors.put(field, DATE_INVALID);
            return null;
        }
    }
}
