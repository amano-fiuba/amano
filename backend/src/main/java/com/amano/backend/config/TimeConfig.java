package com.amano.backend.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.amano.backend.expense.RecurrenceProperties;

/**
 * Injectable clock for every date calculation of 022 (D7, D8): overdue
 * deadlines and historical quote dates must be testable with a fixed clock.
 * The zone is the same one used by the recurring-expense job.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock(RecurrenceProperties recurrenceProperties) {
        return Clock.system(recurrenceProperties.zone());
    }
}
