package com.amano.backend.groupexpense;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily trigger of the shared-expense recurrence generation, next to the
 * personal one. The logic lives in {@link GroupRecurringExpenseService} so the
 * tests call it with an explicit date.
 */
@Component
public class GroupRecurringExpenseScheduler {

    private final GroupRecurringExpenseService service;

    public GroupRecurringExpenseScheduler(GroupRecurringExpenseService service) {
        this.service = service;
    }

    @Scheduled(cron = "${group-recurrence.cron:0 20 0 * * *}",
            zone = "${group-recurrence.zone:America/Argentina/Buenos_Aires}")
    public void generateDueOccurrences() {
        service.generateDueOccurrences(service.today());
    }
}
