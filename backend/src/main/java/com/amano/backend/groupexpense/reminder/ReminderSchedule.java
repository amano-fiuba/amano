package com.amano.backend.groupexpense.reminder;

import java.time.LocalDate;

import com.amano.backend.groupexpense.DebtStatus;

/**
 * Pure date and eligibility rules of the reminders (025 data-model.md §Datos
 * derivados). No Spring dependencies: the job, the materializer and the
 * dispatcher share exactly these calculations.
 */
public final class ReminderSchedule {

    private ReminderSchedule() {
    }

    /** Deadline of an expense: {@code expenseDate + group.debtDueDays}. */
    public static LocalDate dueDate(LocalDate expenseDate, int debtDueDays) {
        return expenseDate.plusDays(debtDueDays);
    }

    /** Date on which the reminder is sent: {@code dueDate - group.reminderSendDays}. */
    public static LocalDate sendDate(LocalDate dueDate, int reminderSendDays) {
        return dueDate.minusDays(reminderSendDays);
    }

    /**
     * A debt is eligible while it is pending, partially paid or waiting for
     * validation, and the deadline has not passed. PAID, VERIFIED and OVERDUE
     * never generate a reminder.
     */
    public static boolean isDebtEligible(DebtStatus status, LocalDate today, LocalDate dueDate) {
        if (status == null || dueDate == null || today == null) {
            return false;
        }
        boolean unsettled = status == DebtStatus.PENDING
                || status == DebtStatus.PARTIALLY_PAID
                || status == DebtStatus.PAYMENT_REPORTED;
        return unsettled && !today.isAfter(dueDate);
    }

    /**
     * The reminder is sent when the send date has arrived and the deadline has
     * not passed. A send date in the past still sends while the deadline is
     * today or later.
     */
    public static boolean isDue(LocalDate sendDate, LocalDate today, LocalDate dueDate) {
        return sendDate != null && today != null && dueDate != null
                && !today.isBefore(sendDate)
                && !today.isAfter(dueDate);
    }
}
