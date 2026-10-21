package com.amano.backend.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.amano.backend.common.FieldValidationException;

/** Date validation and the one-day tolerance fallback (FR-009/FR-016/FR-022, SC-011). */
class ExpenseDateValidatorTest {

    @Test
    void acceptsTodayAndPastInTheInformedZone() {
        String zone = "America/Argentina/Buenos_Aires";
        LocalDate today = LocalDate.now(ZoneId.of(zone));

        assertThat(ExpenseDateValidator.validationError(today, zone)).isNull();
        assertThat(ExpenseDateValidator.validationError(today.minusDays(30), zone)).isNull();
    }

    @Test
    void rejectsTomorrowInTheInformedZone() {
        String zone = "America/Argentina/Buenos_Aires";
        LocalDate today = LocalDate.now(ZoneId.of(zone));

        assertThat(ExpenseDateValidator.validationError(today.plusDays(1), zone))
                .isEqualTo(ExpenseDateValidator.FUTURE_MESSAGE);
    }

    @Test
    void usesTheDeviceZoneNotUtc() {
        // UTC+14: its "today" can be ahead of UTC. The device day must be accepted.
        String advancedZone = "Pacific/Kiritimati";
        LocalDate todayInZone = LocalDate.now(ZoneId.of(advancedZone));

        assertThat(ExpenseDateValidator.validationError(todayInZone, advancedZone)).isNull();
        assertThat(ExpenseDateValidator.validationError(todayInZone.plusDays(1), advancedZone))
                .isEqualTo(ExpenseDateValidator.FUTURE_MESSAGE);
    }

    @Test
    void missingZoneAppliesOneDayTolerance() {
        LocalDate utcToday = LocalDate.now(ZoneOffset.UTC);

        assertThat(ExpenseDateValidator.validationError(utcToday, null)).isNull();
        assertThat(ExpenseDateValidator.validationError(utcToday.plusDays(1), null)).isNull();
        assertThat(ExpenseDateValidator.validationError(utcToday.plusDays(2), null))
                .isEqualTo(ExpenseDateValidator.FUTURE_MESSAGE);
    }

    @Test
    void invalidZoneAppliesOneDayTolerance() {
        LocalDate utcToday = LocalDate.now(ZoneOffset.UTC);

        assertThat(ExpenseDateValidator.validationError(utcToday, "Not/AZone")).isNull();
        assertThat(ExpenseDateValidator.validationError(utcToday.plusDays(1), "Not/AZone")).isNull();
        assertThat(ExpenseDateValidator.validationError(utcToday.plusDays(2), "Not/AZone"))
                .isEqualTo(ExpenseDateValidator.FUTURE_MESSAGE);
    }

    @Test
    void explicitOffsetsAreValidZonesAndDoNotUseTheTolerance() {
        ZoneOffset offset = ZoneOffset.of("+03:00");
        LocalDate todayInOffset = LocalDate.now(offset);

        assertThat(ExpenseDateValidator.limitOf("+03:00")).isEqualTo(todayInOffset);
        assertThat(ExpenseDateValidator.validationError(todayInOffset, "+03:00")).isNull();

        assertThat(ExpenseDateValidator.limitOf("Z")).isEqualTo(LocalDate.now(ZoneOffset.UTC));
        assertThat(ExpenseDateValidator.validationError(
                LocalDate.now(ZoneOffset.UTC).plusDays(1), "Z"))
                .isEqualTo(ExpenseDateValidator.FUTURE_MESSAGE);
    }

    @Test
    void missingDateIsRejected() {
        assertThat(ExpenseDateValidator.validationError(null, "UTC"))
                .isEqualTo(ExpenseDateValidator.REQUIRED_MESSAGE);
    }

    @Test
    void validateThrowsTheFieldErrorWhenRejected() {
        String zone = "America/Argentina/Buenos_Aires";
        LocalDate tomorrow = LocalDate.now(ZoneId.of(zone)).plusDays(1);

        assertThatThrownBy(() -> ExpenseDateValidator.validate(tomorrow, zone))
                .isInstanceOf(FieldValidationException.class)
                .satisfies(exception -> assertThat(
                        ((FieldValidationException) exception).getFields())
                        .containsEntry("date", ExpenseDateValidator.FUTURE_MESSAGE));
    }
}
