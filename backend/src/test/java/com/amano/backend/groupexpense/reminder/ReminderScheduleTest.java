package com.amano.backend.groupexpense.reminder;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.amano.backend.groupexpense.DebtStatus;

/** 025 T011: pure rules of the reminder dates and eligibility. */
class ReminderScheduleTest {

    private static final LocalDate EXPENSE_DATE = LocalDate.of(2026, 10, 21);
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 11, 20);

    @Test
    void dueDateIsTheExpenseDatePlusTheGroupDeadline() {
        assertThat(ReminderSchedule.dueDate(EXPENSE_DATE, 30)).isEqualTo(DUE_DATE);
    }

    @Test
    void sendDateIsTheDueDateMinusTheReminderDays() {
        assertThat(ReminderSchedule.sendDate(DUE_DATE, 5)).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(ReminderSchedule.sendDate(DUE_DATE, 7)).isEqualTo(LocalDate.of(2026, 11, 13));
    }

    @Test
    void pendingPartialAndReportedDebtsAreEligible() {
        LocalDate today = LocalDate.of(2026, 11, 10);
        assertThat(ReminderSchedule.isDebtEligible(DebtStatus.PENDING, today, DUE_DATE)).isTrue();
        assertThat(ReminderSchedule.isDebtEligible(DebtStatus.PARTIALLY_PAID, today, DUE_DATE)).isTrue();
        assertThat(ReminderSchedule.isDebtEligible(DebtStatus.PAYMENT_REPORTED, today, DUE_DATE)).isTrue();
    }

    @Test
    void settledAndOverdueDebtsAreNotEligible() {
        LocalDate today = LocalDate.of(2026, 11, 10);
        assertThat(ReminderSchedule.isDebtEligible(DebtStatus.PAID, today, DUE_DATE)).isFalse();
        assertThat(ReminderSchedule.isDebtEligible(DebtStatus.VERIFIED, today, DUE_DATE)).isFalse();
        assertThat(ReminderSchedule.isDebtEligible(DebtStatus.OVERDUE, today, DUE_DATE)).isFalse();
    }

    @Test
    void aDebtIsNotEligibleAfterItsDeadline() {
        assertThat(ReminderSchedule.isDebtEligible(DebtStatus.PENDING, DUE_DATE, DUE_DATE)).isTrue();
        assertThat(ReminderSchedule.isDebtEligible(DebtStatus.PENDING, DUE_DATE.plusDays(1), DUE_DATE)).isFalse();
    }

    @Test
    void sendsOnTheSendDateAndBetweenSendAndDueDates() {
        LocalDate sendDate = LocalDate.of(2026, 11, 15);
        assertThat(ReminderSchedule.isDue(sendDate, sendDate, DUE_DATE)).isTrue();
        assertThat(ReminderSchedule.isDue(sendDate, LocalDate.of(2026, 11, 18), DUE_DATE)).isTrue();
        assertThat(ReminderSchedule.isDue(sendDate, DUE_DATE, DUE_DATE)).isTrue();
    }

    @Test
    void doesNotSendBeforeTheSendDateNorAfterTheDeadline() {
        LocalDate sendDate = LocalDate.of(2026, 11, 15);
        assertThat(ReminderSchedule.isDue(sendDate, LocalDate.of(2026, 11, 14), DUE_DATE)).isFalse();
        assertThat(ReminderSchedule.isDue(sendDate, DUE_DATE.plusDays(1), DUE_DATE)).isFalse();
    }

    @Test
    void aPastSendDateStillSendsWhileTheDeadlineIsTodayOrLater() {
        LocalDate sendDate = LocalDate.of(2026, 11, 13);
        assertThat(ReminderSchedule.isDue(sendDate, LocalDate.of(2026, 11, 15), DUE_DATE)).isTrue();
    }

    @Test
    void theNoticeIsEligibleOnlyWithAnEligibleDebtAndBeforeTheDeadline() {
        assertThat(ReminderSchedule.isNoticeEligible(true, LocalDate.of(2026, 11, 10), DUE_DATE)).isTrue();
        assertThat(ReminderSchedule.isNoticeEligible(true, DUE_DATE, DUE_DATE)).isTrue();
        assertThat(ReminderSchedule.isNoticeEligible(false, LocalDate.of(2026, 11, 10), DUE_DATE)).isFalse();
        assertThat(ReminderSchedule.isNoticeEligible(true, DUE_DATE.plusDays(1), DUE_DATE)).isFalse();
    }
}
