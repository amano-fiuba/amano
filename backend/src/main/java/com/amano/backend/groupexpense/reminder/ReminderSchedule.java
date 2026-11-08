package com.amano.backend.groupexpense.reminder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.amano.backend.groupexpense.Debt;
import com.amano.backend.groupexpense.DebtStatus;
import com.amano.backend.groupexpense.GroupExpense;
import com.amano.backend.user.Currency;

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
     * The payer notice is due when the expense still has at least one eligible
     * debt and the deadline has not passed. It shares the date rules of the
     * debt reminder.
     */
    public static boolean isNoticeEligible(boolean hasEligibleDebt, LocalDate today, LocalDate dueDate) {
        return hasEligibleDebt && today != null && dueDate != null && !today.isAfter(dueDate);
    }

    /** The payer's own share never becomes a debt, so it neither triggers nor sums the notice. */
    public static boolean isPayerOwnDebt(GroupExpense expense, Debt debt) {
        return expense != null && debt != null && expense.getPayerUserId() != null
                && expense.getPayerUserId().equals(debt.getDebtorUserId());
    }

    /**
     * Pending total of the payer in the group, one entry per currency: the
     * outstanding amount of its unsettled debts. Deterministic order by currency.
     */
    public static List<Map<String, String>> pendingTotals(List<Debt> debts, Long payerUserId) {
        if (payerUserId == null || debts == null) {
            return List.of();
        }
        Map<Currency, BigDecimal> totals = new TreeMap<>();
        for (Debt debt : debts) {
            if (!payerUserId.equals(debt.getDebtorUserId())
                    || (debt.getStatus() != null && debt.getStatus().isSettled())) {
                continue;
            }
            BigDecimal outstanding = debt.getOutstanding();
            if (outstanding == null || outstanding.signum() <= 0) {
                continue;
            }
            totals.merge(debt.getCurrency(), outstanding, BigDecimal::add);
        }
        return totals.entrySet().stream().map(entry -> {
            Map<String, String> total = new LinkedHashMap<>();
            total.put("currency", entry.getKey().name());
            total.put("amount", entry.getValue().setScale(2, RoundingMode.HALF_UP).toPlainString());
            return total;
        }).toList();
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
