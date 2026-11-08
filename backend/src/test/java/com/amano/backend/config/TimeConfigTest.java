package com.amano.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.amano.backend.expense.RecurrenceProperties;

/** 022 T011: the shared clock uses the same zone as the recurring-expense job. */
class TimeConfigTest {

    @Test
    void theClockUsesTheRecurrenceZone() {
        Clock clock = new TimeConfig().clock(new RecurrenceProperties(ZoneId.of("America/Argentina/Buenos_Aires"),
                "0 15 0 * * *"));

        assertThat(clock.getZone()).isEqualTo(ZoneId.of("America/Argentina/Buenos_Aires"));
    }
}
