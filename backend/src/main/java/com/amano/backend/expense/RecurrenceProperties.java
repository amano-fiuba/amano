package com.amano.backend.expense;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Recurrence settings (009, FR-005): the zone that defines "today" for the daily
 * generation and for resuming a series, and the Spring 6-field cron of the job.
 * Read from {@code application.properties}, like {@code VerificationProperties}.
 */
@ConfigurationProperties(prefix = "recurrence")
public record RecurrenceProperties(
        @DefaultValue("America/Argentina/Buenos_Aires") ZoneId zone,
        @DefaultValue("0 15 0 * * *") String cron) {

    public RecurrenceProperties {
        if (cron == null || cron.isBlank()) {
            throw new IllegalStateException("recurrence.cron must be a cron expression");
        }
    }
}
