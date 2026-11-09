package com.amano.backend.groupexpense.reminder;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.groupexpense.Debt;
import com.amano.backend.groupexpense.DebtRepository;
import com.amano.backend.groupexpense.GroupExpense;
import com.amano.backend.groupexpense.GroupExpenseRepository;
import com.amano.backend.groups.Group;

/**
 * Creates, reschedules and cancels the {@code SCHEDULED} reminders of a group
 * (025 T017). It only touches rows that were never claimed: {@code SENT},
 * {@code FAILED} and claimed rows are final snapshots. The unique partial
 * indexes of V27 guarantee at most one reminder per debt/expense even if two
 * instances run at once.
 */
@Component
public class ReminderMaterializer {

    private final ReminderRepository reminderRepository;
    private final DebtRepository debtRepository;
    private final GroupExpenseRepository groupExpenseRepository;
    private final Clock clock;

    public ReminderMaterializer(ReminderRepository reminderRepository, DebtRepository debtRepository,
            GroupExpenseRepository groupExpenseRepository, Clock clock) {
        this.reminderRepository = reminderRepository;
        this.debtRepository = debtRepository;
        this.groupExpenseRepository = groupExpenseRepository;
        this.clock = clock;
    }

    @Transactional
    public void materialize(Group group, LocalDate today) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (group.getDebtDueDays() == null) {
            cancelScheduled(group.getId(), ReminderKind.DEBT, now);
            cancelScheduled(group.getId(), ReminderKind.PAYER_NOTICE, now);
            return;
        }
        int reminderSendDays = group.getReminderSendDays() == null ? 5 : group.getReminderSendDays();
        List<Debt> debts = debtRepository.findByGroupId(group.getId());
        Map<Long, GroupExpense> expenses = new HashMap<>();
        Set<Long> currentDebtIds = new HashSet<>();
        for (Debt debt : debts) {
            currentDebtIds.add(debt.getId());
            GroupExpense expense = expenses.computeIfAbsent(debt.getExpenseId(),
                    id -> groupExpenseRepository.findById(id).orElse(null));
            if (expense == null || expense.getDueDate() == null) {
                cancelDebtReminder(debt.getId(), now);
                continue;
            }
            LocalDate dueDate = expense.getDueDate();
            if (!ReminderSchedule.isDebtEligible(debt.getStatus(), today, dueDate)) {
                cancelDebtReminder(debt.getId(), now);
                continue;
            }
            LocalDate sendDate = ReminderSchedule.sendDate(dueDate, reminderSendDays);
            Optional<Reminder> existing = reminderRepository.findByKindAndDebtId(ReminderKind.DEBT, debt.getId());
            if (existing.isEmpty()) {
                reminderRepository.save(Reminder.scheduledForDebt(group.getId(), debt.getId(), sendDate));
            }
            else {
                Reminder reminder = existing.get();
                if (reminder.getStatus() == ReminderStatus.SCHEDULED && reminder.getClaimedAt() == null
                        && !sendDate.equals(reminder.getSendDate())) {
                    reminderRepository.reschedule(reminder.getId(), sendDate, now);
                }
            }
        }
        Set<Long> currentNoticeExpenseIds = new HashSet<>();
        Map<Long, List<Debt>> debtsByExpense = debts.stream()
                .filter(debt -> debt.getExpenseId() != null)
                .collect(Collectors.groupingBy(Debt::getExpenseId));
        for (Map.Entry<Long, List<Debt>> entry : debtsByExpense.entrySet()) {
            Long expenseId = entry.getKey();
            GroupExpense expense = expenses.computeIfAbsent(expenseId,
                    id -> groupExpenseRepository.findById(id).orElse(null));
            if (expense == null || expense.getDueDate() == null) {
                cancelPayerNotice(expenseId, now);
                continue;
            }
            LocalDate dueDate = expense.getDueDate();
            boolean hasEligibleDebt = entry.getValue().stream()
                    .filter(debt -> !ReminderSchedule.isPayerOwnDebt(expense, debt))
                    .anyMatch(debt -> ReminderSchedule.isDebtEligible(debt.getStatus(), today, dueDate));
            if (!ReminderSchedule.isNoticeEligible(hasEligibleDebt, today, dueDate)) {
                cancelPayerNotice(expenseId, now);
                continue;
            }
            currentNoticeExpenseIds.add(expenseId);
            LocalDate sendDate = ReminderSchedule.sendDate(dueDate, reminderSendDays);
            Optional<Reminder> existing = reminderRepository.findByKindAndExpenseId(
                    ReminderKind.PAYER_NOTICE, expenseId);
            if (existing.isEmpty()) {
                reminderRepository.save(Reminder.scheduledForPayerNotice(group.getId(), expenseId, sendDate));
            }
            else {
                Reminder reminder = existing.get();
                if (reminder.getStatus() == ReminderStatus.SCHEDULED && reminder.getClaimedAt() == null
                        && !sendDate.equals(reminder.getSendDate())) {
                    reminderRepository.reschedule(reminder.getId(), sendDate, now);
                }
            }
        }
        for (Reminder reminder : reminderRepository.findByGroupIdAndStatusAndClaimedAtIsNull(group.getId(),
                ReminderStatus.SCHEDULED)) {
            if (reminder.getKind() == ReminderKind.DEBT && !currentDebtIds.contains(reminder.getDebtId())) {
                reminderRepository.cancel(reminder.getId(), now);
            }
            if (reminder.getKind() == ReminderKind.PAYER_NOTICE
                    && !currentNoticeExpenseIds.contains(reminder.getExpenseId())) {
                reminderRepository.cancel(reminder.getId(), now);
            }
        }
    }

    private void cancelPayerNotice(Long expenseId, OffsetDateTime now) {
        reminderRepository.findByKindAndExpenseId(ReminderKind.PAYER_NOTICE, expenseId)
                .filter(reminder -> reminder.getStatus() == ReminderStatus.SCHEDULED)
                .filter(reminder -> reminder.getClaimedAt() == null)
                .ifPresent(reminder -> reminderRepository.cancel(reminder.getId(), now));
    }

    private void cancelDebtReminder(Long debtId, OffsetDateTime now) {
        reminderRepository.findByKindAndDebtId(ReminderKind.DEBT, debtId)
                .filter(reminder -> reminder.getStatus() == ReminderStatus.SCHEDULED)
                .filter(reminder -> reminder.getClaimedAt() == null)
                .ifPresent(reminder -> reminderRepository.cancel(reminder.getId(), now));
    }

    private void cancelScheduled(Long groupId, ReminderKind kind, OffsetDateTime now) {
        List<Reminder> scheduled = reminderRepository.findByGroupIdAndStatusAndClaimedAtIsNull(groupId,
                ReminderStatus.SCHEDULED);
        scheduled.stream()
                .filter(reminder -> reminder.getKind() == kind)
                .map(Reminder::getId)
                .collect(Collectors.toList())
                .forEach(id -> reminderRepository.cancel(id, now));
    }
}
