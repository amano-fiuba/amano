package com.amano.backend.groupexpense.overdue;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.groupexpense.DebtRepository;
import com.amano.backend.groups.Group;
import com.amano.backend.groups.GroupRepository;

/**
 * Daily overdue-debt job (022 D7, T063). For every group with a non-null
 * {@code debtDueDays}, a debt still in {@code PENDING} whose origin expense is
 * older than {@code today - debtDueDays} becomes {@code OVERDUE}. The boundary
 * is strict: the debt does not expire on the deadline day itself, only the day
 * after. A null deadline means the group never expires debts.
 *
 * <p>The transition is a single conditional bulk update, so running the job
 * twice (or on several instances at once) is idempotent. It never touches
 * {@code PARTIALLY_PAID}, {@code PAYMENT_REPORTED}, {@code PAID} or
 * {@code VERIFIED}. The clock is injectable for deterministic tests and the
 * cadence comes from {@code overdue.cron}.
 */
@Component
public class OverdueDebtJob {

    private final GroupRepository groupRepository;
    private final DebtRepository debtRepository;
    private final Clock clock;

    public OverdueDebtJob(GroupRepository groupRepository, DebtRepository debtRepository, Clock clock) {
        this.groupRepository = groupRepository;
        this.debtRepository = debtRepository;
        this.clock = clock;
    }

    /** Marks the due PENDING debts as OVERDUE; a second run changes nothing. */
    @Scheduled(cron = "${overdue.cron:0 30 0 * * *}",
            zone = "${recurrence.zone:America/Argentina/Buenos_Aires}")
    @Transactional
    public void markOverdueDebts() {
        LocalDate today = LocalDate.now(clock);
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (Group group : groupRepository.findByDebtDueDaysIsNotNull()) {
            Integer dueDays = group.getDebtDueDays();
            if (dueDays == null) {
                continue;
            }
            LocalDate before = today.minusDays(dueDays);
            debtRepository.markPendingOverdue(group.getId(), before, now);
        }
    }
}
