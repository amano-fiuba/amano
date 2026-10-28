package com.amano.backend.expense;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Closed frequency catalog of a recurring expense series (009, FR-001). Series
 * dates are always computed from the start date as {@code start + n periods}
 * (FR-004): Java's {@code plusMonths}/{@code plusYears} clamp to the last day of a
 * shorter month, and anchoring at the start keeps a 31/01 series on 31/03 instead
 * of drifting to the 28th. Persisted by its stable code; labels live in the frontend.
 */
public enum RecurrenceFrequency {
    WEEKLY(ChronoUnit.WEEKS),
    MONTHLY(ChronoUnit.MONTHS),
    YEARLY(ChronoUnit.YEARS);

    private final ChronoUnit unit;

    RecurrenceFrequency(ChronoUnit unit) {
        this.unit = unit;
    }

    /** The {@code n}-th date of a series that starts on {@code start} (n = 0 is the start). */
    public LocalDate nth(LocalDate start, long n) {
        return start.plus(n, unit);
    }

    /** First series date strictly after {@code reference} (never the start itself). */
    public LocalDate firstAfter(LocalDate start, LocalDate reference) {
        long n = Math.max(1, unit.between(start, reference));
        LocalDate candidate = nth(start, n);
        while (!candidate.isAfter(reference)) {
            candidate = nth(start, ++n);
        }
        // Clamping can make "between" overshoot by one period: step back while still valid.
        while (n > 1 && nth(start, n - 1).isAfter(reference)) {
            candidate = nth(start, --n);
        }
        return candidate;
    }

    /** First series date on or after {@code reference}, excluding the start (resume, FR-008). */
    public LocalDate firstOnOrAfter(LocalDate start, LocalDate reference) {
        return firstAfter(start, reference.minusDays(1));
    }
}
