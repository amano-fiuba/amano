package com.amano.backend.groupexpense.reminder;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.amano.backend.groups.Group;
import com.amano.backend.groups.GroupRepository;

/**
 * Daily reminder job (025 T021). Runs after the overdue job so a debt that
 * expires that same day is already {@code OVERDUE} and does not receive a
 * reminder. For every group with a deadline (or with reminders already
 * scheduled) it materializes the missing rows and dispatches the due ones.
 * The clock is injectable and the cadence comes from {@code reminders.cron}.
 */
@Component
public class ReminderJob {

    public static final String ZONE = "America/Argentina/Buenos_Aires";

    private final GroupRepository groupRepository;
    private final ReminderRepository reminderRepository;
    private final ReminderMaterializer materializer;
    private final ReminderDispatcher dispatcher;
    private final Clock clock;

    public ReminderJob(GroupRepository groupRepository, ReminderRepository reminderRepository,
            ReminderMaterializer materializer, ReminderDispatcher dispatcher, Clock clock) {
        this.groupRepository = groupRepository;
        this.reminderRepository = reminderRepository;
        this.materializer = materializer;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    @Scheduled(cron = "${reminders.cron:0 45 0 * * *}", zone = ZONE)
    public void runReminders() {
        LocalDate today = LocalDate.now(clock);
        Set<Long> groupIds = new LinkedHashSet<>();
        groupRepository.findByDebtDueDaysIsNotNull().forEach(group -> groupIds.add(group.getId()));
        groupIds.addAll(reminderRepository.findDistinctGroupIdsByStatus(ReminderStatus.SCHEDULED));
        for (Long groupId : groupIds) {
            Group group = groupRepository.findById(groupId).orElse(null);
            if (group == null) {
                continue;
            }
            materializer.materialize(group, today);
            dispatcher.dispatchGroup(group, today);
        }
    }
}
