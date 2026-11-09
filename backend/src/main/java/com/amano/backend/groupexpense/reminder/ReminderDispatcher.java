package com.amano.backend.groupexpense.reminder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.amano.backend.groupexpense.Debt;
import com.amano.backend.groupexpense.DebtRepository;
import com.amano.backend.groupexpense.GroupExpense;
import com.amano.backend.groupexpense.GroupExpenseRepository;
import com.amano.backend.groups.ActivityEventService;
import com.amano.backend.groups.Group;
import com.amano.backend.user.Currency;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * Claims and delivers the due reminders of a group (025 T020). The claim is a
 * conditional update, the SMTP send happens outside that transaction, and the
 * final {@code SENT} mark plus the history event share one transaction. A
 * failure marks the row {@code FAILED}, notifies every active admin and never
 * retries or blocks the remaining reminders.
 */
@Component
public class ReminderDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ReminderDispatcher.class);

    private final ReminderRepository reminderRepository;
    private final ReminderRecipients recipients;
    private final ReminderMessageBuilder messageBuilder;
    private final ReminderMailService mailService;
    private final ActivityEventService activityEventService;
    private final DebtRepository debtRepository;
    private final GroupExpenseRepository groupExpenseRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public ReminderDispatcher(ReminderRepository reminderRepository, ReminderRecipients recipients,
            ReminderMessageBuilder messageBuilder, ReminderMailService mailService,
            ActivityEventService activityEventService, DebtRepository debtRepository,
            GroupExpenseRepository groupExpenseRepository, UserRepository userRepository,
            Clock clock, PlatformTransactionManager transactionManager) {
        this.reminderRepository = reminderRepository;
        this.recipients = recipients;
        this.messageBuilder = messageBuilder;
        this.mailService = mailService;
        this.activityEventService = activityEventService;
        this.debtRepository = debtRepository;
        this.groupExpenseRepository = groupExpenseRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void dispatchGroup(Group group, LocalDate today) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (Reminder orphan : reminderRepository.findByGroupIdAndStatusAndClaimedAtIsNotNull(group.getId(),
                ReminderStatus.SCHEDULED)) {
            reminderRepository.markFailed(orphan.getId(), now);
            notifyFailure(group, orphan, targetOf(group, orphan), today);
        }
        List<Reminder> due = reminderRepository.findByGroupIdAndStatusAndSendDateLessThanEqual(group.getId(),
                ReminderStatus.SCHEDULED, today);
        for (Reminder reminder : due) {
            if (!stillEligible(group, reminder, today)) {
                reminderRepository.cancel(reminder.getId(), now);
                continue;
            }
            if (reminderRepository.claim(reminder.getId(), now) == 0) {
                continue;
            }
            deliver(group, reminder, today);
        }
    }

    private void deliver(Group group, Reminder reminder, LocalDate today) {
        Target target = targetOf(group, reminder);
        if (target == null) {
            log.warn("Reminder {} of group {} has no resolvable target; marking FAILED",
                    reminder.getId(), group.getId());
            reminderRepository.markFailed(reminder.getId(), OffsetDateTime.now(clock));
            notifyFailure(group, reminder, null, today);
            return;
        }
        if (target.email() == null) {
            log.warn("Reminder {} of group {} has no recipient email; marking FAILED",
                    reminder.getId(), group.getId());
            reminderRepository.markFailed(reminder.getId(), OffsetDateTime.now(clock));
            notifyFailure(group, reminder, target, today);
            return;
        }
        try {
            mailService.send(target.email(), target.mail());
        }
        catch (Exception exception) {
            log.warn("Reminder {} of group {} could not be sent to {}: {}",
                    reminder.getId(), group.getId(), target.email(), exception.getMessage());
            reminderRepository.markFailed(reminder.getId(), OffsetDateTime.now(clock));
            notifyFailure(group, reminder, target, today);
            return;
        }
        markSentAndRecord(group, reminder, target);
    }

    private void markSentAndRecord(Group group, Reminder reminder, Target target) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        transactionTemplate.executeWithoutResult(status -> {
            int updated = reminderRepository.markSent(reminder.getId(), now, target.mail().subject(),
                    target.mail().body(), now);
            if (updated == 0 || target.expense() == null) {
                return;
            }
            if (target.debt() != null) {
                Debt debt = target.debt();
                activityEventService.recordDebtReminderSent(group.getId(), reminder.getId(), debt.getId(),
                        debt.getExpenseId(), debt.getAmount(), debt.getCurrency().name(),
                        debt.getDebtorName(), target.creditorName(), target.dueDate());
            }
            else {
                activityEventService.recordPayerDueNoticeSent(group.getId(), reminder.getId(),
                        target.expense().getId(), target.payerName(), target.concept(), target.dueDate(),
                        target.daysRemaining(), target.pendingTotals());
            }
        });
    }

    private void notifyFailure(Group group, Reminder reminder, Target target, LocalDate today) {
        List<String> admins = recipients.activeAdminEmails(group.getId());
        if (admins.isEmpty()) {
            log.warn("Reminder {} of group {} failed and the group has no active admins",
                    reminder.getId(), group.getId());
            return;
        }
        if (target == null) {
            log.warn("Reminder {} of group {} failed but its target could not be resolved",
                    reminder.getId(), group.getId());
            return;
        }
        String type = reminder.getKind() == ReminderKind.DEBT ? "deuda" : "aviso de vencimiento";
        ReminderMessageBuilder.Mail mail = messageBuilder.failureNotice(group.getName(), type,
                target.name(), target.email() == null ? "sin correo" : target.email(),
                target.dueDate(), target.amount(), target.currency());
        for (String admin : admins) {
            try {
                mailService.send(admin, mail);
            }
            catch (Exception exception) {
                log.warn("Failure notice of reminder {} could not be sent to admin {}: {}",
                        reminder.getId(), admin, exception.getMessage());
            }
        }
    }

    private boolean stillEligible(Group group, Reminder reminder, LocalDate today) {
        if (reminder.getKind() == ReminderKind.PAYER_NOTICE) {
            return payerNoticeStillEligible(group, reminder, today);
        }
        Debt debt = debtRepository.findById(reminder.getDebtId()).orElse(null);
        if (debt == null || group.getDebtDueDays() == null) {
            return false;
        }
        GroupExpense expense = groupExpenseRepository.findById(debt.getExpenseId()).orElse(null);
        if (expense == null || expense.getDueDate() == null) {
            return false;
        }
        LocalDate dueDate = expense.getDueDate();
        return ReminderSchedule.isDebtEligible(debt.getStatus(), today, dueDate);
    }

    private boolean payerNoticeStillEligible(Group group, Reminder reminder, LocalDate today) {
        if (group.getDebtDueDays() == null || reminder.getExpenseId() == null) {
            return false;
        }
        GroupExpense expense = groupExpenseRepository.findById(reminder.getExpenseId()).orElse(null);
        if (expense == null || expense.getDueDate() == null) {
            return false;
        }
        LocalDate dueDate = expense.getDueDate();
        boolean hasEligibleDebt = debtRepository.findByExpenseId(expense.getId()).stream()
                .filter(debt -> !ReminderSchedule.isPayerOwnDebt(expense, debt))
                .anyMatch(debt -> ReminderSchedule.isDebtEligible(debt.getStatus(), today, dueDate));
        return ReminderSchedule.isNoticeEligible(hasEligibleDebt, today, dueDate);
    }

    private Target targetOf(Group group, Reminder reminder) {
        if (reminder.getKind() == ReminderKind.PAYER_NOTICE) {
            return payerNoticeTarget(group, reminder);
        }
        Debt debt = reminder.getDebtId() == null ? null : debtRepository.findById(reminder.getDebtId()).orElse(null);
        if (debt == null || group.getDebtDueDays() == null) {
            return null;
        }
        GroupExpense expense = groupExpenseRepository.findById(debt.getExpenseId()).orElse(null);
        if (expense == null || expense.getDueDate() == null) {
            return null;
        }
        LocalDate dueDate = expense.getDueDate();
        String email = recipients.emailOf(debt).orElse(null);
        String creditorName = creditorName(debt.getCreditorUserId());
        ReminderMessageBuilder.Mail mail = messageBuilder.debtReminder(group.getName(), debt.getDebtorName(),
                expense.getDescription(), debt.getAmount(), debt.getCurrency(), dueDate, debt.getStatus(),
                group.getReminderMessage(), expense);
        return new Target(debt.getDebtorName(), email, dueDate, debt.getAmount(), debt.getCurrency(),
                mail, debt, expense, creditorName, null, null, 0, List.of());
    }

    private Target payerNoticeTarget(Group group, Reminder reminder) {
        if (group.getDebtDueDays() == null || reminder.getExpenseId() == null) {
            return null;
        }
        GroupExpense expense = groupExpenseRepository.findById(reminder.getExpenseId()).orElse(null);
        if (expense == null || expense.getDueDate() == null || expense.getPayerUserId() == null) {
            return null;
        }
        User payer = userRepository.findById(expense.getPayerUserId()).orElse(null);
        if (payer == null) {
            return null;
        }
        String payerName = ActivityEventService.displayName(payer);
        if (payerName == null) {
            payerName = payer.getEmail();
        }
        LocalDate dueDate = expense.getDueDate();
        long daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(clock), dueDate);
        List<Map<String, String>> pendingTotals = ReminderSchedule.pendingTotals(
                debtRepository.findByGroupId(group.getId()), expense.getPayerUserId());
        String email = recipients.payerEmail(expense).orElse(null);
        ReminderMessageBuilder.Mail mail = messageBuilder.payerNotice(group.getName(), payerName,
                expense.getDescription(), dueDate, daysRemaining, pendingTotals, expense);
        return new Target(payerName, email, dueDate, expense.getAmount(), expense.getCurrency(), mail,
                null, expense, null, payerName, expense.getDescription(), daysRemaining, pendingTotals);
    }

    private String creditorName(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }
        String name = ActivityEventService.displayName(user);
        return name == null ? user.getEmail() : name;
    }

    private record Target(String name, String email, LocalDate dueDate, BigDecimal amount, Currency currency,
            ReminderMessageBuilder.Mail mail, Debt debt, GroupExpense expense, String creditorName,
            String payerName, String concept, long daysRemaining, List<Map<String, String>> pendingTotals) {
    }
}
