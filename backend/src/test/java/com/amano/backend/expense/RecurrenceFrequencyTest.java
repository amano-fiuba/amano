package com.amano.backend.expense;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/**
 * Unit tests of the series date arithmetic (009, FR-003/FR-004/FR-008): dates are
 * anchored at the start, month ends clamp without drifting and leap days fall back
 * to 28/02.
 */
class RecurrenceFrequencyTest {

    private static LocalDate d(String iso) {
        return LocalDate.parse(iso);
    }

    @Test
    void weeklySeriesAdvancesSevenDays() {
        assertThat(RecurrenceFrequency.WEEKLY.firstAfter(d("2026-10-24"), d("2026-10-24")))
                .isEqualTo(d("2026-10-31"));
        assertThat(RecurrenceFrequency.WEEKLY.firstAfter(d("2026-10-01"), d("2026-10-24")))
                .isEqualTo(d("2026-10-29"));
    }

    @Test
    void monthlySeriesRegisteredTodayStartsNextMonth() {
        assertThat(RecurrenceFrequency.MONTHLY.firstAfter(d("2026-10-24"), d("2026-10-24")))
                .isEqualTo(d("2026-11-24"));
    }

    @Test
    void pastStartDateIsNeverRetroactive() {
        // US1 scenario 4: 01/01 registered on 24/10 → next is 01/11, not 01/02.
        assertThat(RecurrenceFrequency.MONTHLY.firstAfter(d("2026-01-01"), d("2026-10-24")))
                .isEqualTo(d("2026-11-01"));
    }

    @Test
    void monthEndSeriesClampsWithoutDrifting() {
        LocalDate start = d("2026-01-31");
        assertThat(RecurrenceFrequency.MONTHLY.firstAfter(start, start)).isEqualTo(d("2026-02-28"));
        assertThat(RecurrenceFrequency.MONTHLY.firstAfter(start, d("2026-02-28")))
                .isEqualTo(d("2026-03-31"));
        assertThat(RecurrenceFrequency.MONTHLY.firstAfter(start, d("2026-03-31")))
                .isEqualTo(d("2026-04-30"));
        assertThat(RecurrenceFrequency.MONTHLY.firstAfter(start, d("2026-04-30")))
                .isEqualTo(d("2026-05-31"));
        assertThat(RecurrenceFrequency.MONTHLY.firstAfter(d("2028-01-31"), d("2028-01-31")))
                .isEqualTo(d("2028-02-29")); // leap year
    }

    @Test
    void yearlyLeapDaySeriesFallsBackTo28February() {
        LocalDate start = d("2028-02-29");
        assertThat(RecurrenceFrequency.YEARLY.firstAfter(start, start)).isEqualTo(d("2029-02-28"));
        assertThat(RecurrenceFrequency.YEARLY.firstAfter(start, d("2029-02-28")))
                .isEqualTo(d("2030-02-28"));
        assertThat(RecurrenceFrequency.YEARLY.firstAfter(start, d("2031-06-01")))
                .isEqualTo(d("2032-02-29"));
    }

    @Test
    void nthIsAlwaysComputedFromTheStart() {
        assertThat(RecurrenceFrequency.MONTHLY.nth(d("2026-01-31"), 2)).isEqualTo(d("2026-03-31"));
        assertThat(RecurrenceFrequency.WEEKLY.nth(d("2026-10-24"), 0)).isEqualTo(d("2026-10-24"));
    }

    @Test
    void firstOnOrAfterIncludesTodayWhenItIsASeriesDate() {
        // US3 scenario 4: series of 24/09 resumed on 24/10 → today.
        assertThat(RecurrenceFrequency.MONTHLY.firstOnOrAfter(d("2026-09-24"), d("2026-10-24")))
                .isEqualTo(d("2026-10-24"));
        // US3 scenario 3: series of 05/08 resumed on 24/10 → 05/11 (Sept/Oct skipped).
        assertThat(RecurrenceFrequency.MONTHLY.firstOnOrAfter(d("2026-08-05"), d("2026-10-24")))
                .isEqualTo(d("2026-11-05"));
    }

    @Test
    void firstOnOrAfterNeverReturnsTheStartItself() {
        assertThat(RecurrenceFrequency.MONTHLY.firstOnOrAfter(d("2026-10-24"), d("2026-10-24")))
                .isEqualTo(d("2026-11-24"));
    }
}
