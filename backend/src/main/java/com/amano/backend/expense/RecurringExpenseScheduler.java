package com.amano.backend.expense;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily trigger of the occurrence generation (009, FR-005), in the same style as
 * {@code UnverifiedAccountCleanupScheduler}: the cron and zone come from
 * {@code recurrence.*} and the logic lives in {@link RecurringExpenseService}, so
 * the tests call the service directly with an explicit date.
 */
@Component
public class RecurringExpenseScheduler {

    private final RecurringExpenseService recurringExpenseService;

    public RecurringExpenseScheduler(RecurringExpenseService recurringExpenseService) {
        this.recurringExpenseService = recurringExpenseService;
    }

    @Scheduled(cron = "${recurrence.cron:0 15 0 * * *}",
            zone = "${recurrence.zone:America/Argentina/Buenos_Aires}")
    public void generateDueOccurrences() {
        recurringExpenseService.generateDueOccurrences(recurringExpenseService.today());
    }
}
