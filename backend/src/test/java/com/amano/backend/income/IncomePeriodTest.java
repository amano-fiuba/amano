package com.amano.backend.income;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.amano.backend.common.FieldValidationException;

/** The period filter shared by the balance and the income search (specs/032, FR-008/FR-010). */
class IncomePeriodTest {

    @Test
    void withoutFiltersCoversEveryDate() {
        IncomePeriod period = IncomePeriod.of(null, "", " ", null);

        assertThat(period.start()).isEqualTo(LocalDate.of(1, 1, 1));
        assertThat(period.end()).isEqualTo(LocalDate.of(9999, 12, 31));
    }

    @Test
    void aYearCoversTheWholeYearAndAMonthOnlyThatMonth() {
        IncomePeriod year = IncomePeriod.of("2026", null, null, null);
        IncomePeriod month = IncomePeriod.of("2024", "2", null, null);

        assertThat(year.start()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(year.end()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(month.start()).isEqualTo(LocalDate.of(2024, 2, 1));
        assertThat(month.end()).isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @Test
    void aRangeIsIncludedAndIntersectsWithTheYear() {
        IncomePeriod range = IncomePeriod.of(null, null, "2026-03-05", "2026-03-10");
        IncomePeriod inside = IncomePeriod.of("2026", null, "2026-03-05", "2027-03-10");
        IncomePeriod outside = IncomePeriod.of("2026", null, "2020-01-01", "2030-01-01");

        assertThat(range.start()).isEqualTo(LocalDate.of(2026, 3, 5));
        assertThat(range.end()).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(inside.start()).isEqualTo(LocalDate.of(2026, 3, 5));
        assertThat(inside.end()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(outside.start()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(outside.end()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void rejectsAnInvalidYearOrMonth() {
        for (String year : new String[] { "0", "10000", "abc", "-1" }) {
            assertThatThrownBy(() -> IncomePeriod.of(year, null, null, null))
                    .isInstanceOfSatisfying(FieldValidationException.class, error ->
                            assertThat(error.getFields()).containsEntry("year",
                                    IncomePeriod.YEAR_INVALID));
        }
        for (String month : new String[] { "0", "13", "x" }) {
            assertThatThrownBy(() -> IncomePeriod.of("2026", month, null, null))
                    .isInstanceOfSatisfying(FieldValidationException.class, error ->
                            assertThat(error.getFields()).containsEntry("month",
                                    IncomePeriod.MONTH_INVALID));
        }
    }

    @Test
    void aMonthNeedsAYear() {
        assertThatThrownBy(() -> IncomePeriod.of(null, "5", null, null))
                .isInstanceOfSatisfying(FieldValidationException.class, error ->
                        assertThat(error.getFields()).containsEntry("month",
                                IncomePeriod.MONTH_WITHOUT_YEAR));
    }

    @Test
    void rejectsInvalidDatesAndAnInvertedRange() {
        assertThatThrownBy(() -> IncomePeriod.of(null, null, "10/03/2026", "2026-13-01"))
                .isInstanceOfSatisfying(FieldValidationException.class, error ->
                        assertThat(error.getFields()).containsEntry("from", IncomePeriod.DATE_INVALID)
                                .containsEntry("to", IncomePeriod.DATE_INVALID));
        assertThatThrownBy(() -> IncomePeriod.of(null, null, "2026-03-10", "2026-03-05"))
                .isInstanceOfSatisfying(FieldValidationException.class, error ->
                        assertThat(error.getFields()).containsEntry("from",
                                IncomePeriod.FROM_AFTER_TO));
    }
}
