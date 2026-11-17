package com.amano.backend.groupexpense.overdue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.amano.backend.groupexpense.Debt;
import com.amano.backend.groupexpense.DebtRepository;
import com.amano.backend.support.AbstractClockIntegrationIT;

/**
 * 022 D7/T063: integration tests of {@link OverdueDebtJob} against PostgreSQL.
 * The clock is a mock so each test can place "today" exactly on the deadline or
 * the day after; the job bean is the real transactional component.
 */
class OverdueDebtJobIT extends AbstractClockIntegrationIT {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Autowired
    private OverdueDebtJob job;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DebtRepository debtRepository;

    private long debtorId;
    private long creditorId;

    @BeforeEach
    void setUp() {
        when(clock.getZone()).thenReturn(ZONE);
        at("2026-11-10");
        debtorId = user("Deudor", "Test");
        creditorId = user("Acreedor", "Test");
    }

    private long user(String first, String last) {
        return jdbc.queryForObject("""
                INSERT INTO users (first_name, last_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES (?, ?, ?, 'x', now(), now()) RETURNING id
                """, Long.class, first, last, "job" + UUID.randomUUID() + "@overdue.dev");
    }

    private void at(String date) {
        when(clock.instant()).thenReturn(LocalDate.parse(date).atTime(12, 0).atZone(ZONE).toInstant());
    }

    private long group(Integer dueDays) {
        return jdbc.queryForObject("""
                INSERT INTO groups (name, primary_currency, debt_due_days)
                VALUES (?, 'ARS', ?) RETURNING id
                """, Long.class, "Grupo " + UUID.randomUUID(), dueDays);
    }

    private long debt(long groupId, String expenseDate, String status, String paid) {
        long expenseId = jdbc.queryForObject("""
                INSERT INTO group_expenses (group_id, created_by_user_id, payer_user_id, amount, currency,
                    expense_date, description, idempotency_key)
                VALUES (?, ?, ?, 100.00, 'ARS', ?, 'Job', gen_random_uuid()) RETURNING id
                """, Long.class, groupId, creditorId, creditorId, java.sql.Date.valueOf(expenseDate));
        jdbc.update("""
                UPDATE group_expenses e
                SET due_date = e.expense_date + g.debt_due_days
                FROM groups g
                WHERE g.id = e.group_id AND e.id = ? AND g.debt_due_days IS NOT NULL
                """, expenseId);
        long shareId = jdbc.queryForObject("""
                INSERT INTO group_expense_shares (expense_id, position, participant_type, user_id,
                    participant_name, amount)
                VALUES (?, 0, 'REGISTERED', ?, 'Deudor', 100.00) RETURNING id
                """, Long.class, expenseId, debtorId);
        return jdbc.queryForObject("""
                INSERT INTO debts (group_id, expense_id, share_id, debtor_type, debtor_user_id, debtor_name,
                    creditor_user_id, currency, amount, paid_amount, status)
                VALUES (?, ?, ?, 'REGISTERED', ?, 'Deudor', ?, 'ARS', 100.00, ?, ?) RETURNING id
                """, Long.class, groupId, expenseId, shareId, debtorId, creditorId, new BigDecimal(paid), status);
    }

    private String statusOf(long debtId) {
        return jdbc.queryForObject("SELECT status FROM debts WHERE id = ?", String.class, debtId);
    }

    private OffsetDateTime updatedAt(long debtId) {
        return jdbc.queryForObject("SELECT updated_at FROM debts WHERE id = ?", OffsetDateTime.class, debtId);
    }

    private int overdueEvents(long groupId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM group_activity WHERE group_id = ? AND event_type = 'DEBT_OVERDUE'",
                Integer.class, groupId);
    }

    @Test
    void scheduledAnnotationUsesTheBuenosAiresZone() throws Exception {
        var scheduled = OverdueDebtJob.class.getMethod("markOverdueDebts")
                .getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.zone()).isEqualTo("America/Argentina/Buenos_Aires");
    }

    @Test
    void runningTheJobTwiceIsIdempotent() {
        long groupId = group(5);
        long debtId = debt(groupId, "2026-11-01", "PENDING", "0.00");

        at("2026-11-10");
        job.markOverdueDebts();
        assertThat(statusOf(debtId)).isEqualTo("OVERDUE");
        assertThat(overdueEvents(groupId)).isEqualTo(1);
        OffsetDateTime firstRun = updatedAt(debtId);

        at("2026-11-11");
        job.markOverdueDebts();
        assertThat(statusOf(debtId)).isEqualTo("OVERDUE");
        assertThat(updatedAt(debtId)).isEqualTo(firstRun);
        assertThat(overdueEvents(groupId)).isEqualTo(1);
    }

    @Test
    void nullDueDaysNeverExpires() {
        long groupId = group(null);
        long debtId = debt(groupId, "2020-01-01", "PENDING", "0.00");

        at("2030-01-01");
        job.markOverdueDebts();

        assertThat(statusOf(debtId)).isEqualTo("PENDING");
    }

    @Test
    void theDeadlineDayDoesNotExpireAndTheNextDayDoes() {
        long groupId = group(5);
        long debtId = debt(groupId, "2026-11-05", "PENDING", "0.00");

        at("2026-11-10"); // expenseDate + 5: still on time
        job.markOverdueDebts();
        assertThat(statusOf(debtId)).isEqualTo("PENDING");

        at("2026-11-11"); // first day after the deadline
        job.markOverdueDebts();
        assertThat(statusOf(debtId)).isEqualTo("OVERDUE");
    }

    @Test
    void aRejectedOverdueDebtBecomesOverdueAgainOnTheNextRun() {
        long groupId = group(5);
        long debtId = debt(groupId, "2026-11-01", "OVERDUE", "0.00");

        // 022 US5 rejection path: the reported payment is rejected and the debt
        // returns to PENDING (no legacy partial payment in this case).
        jdbc.update("UPDATE debts SET status = 'PAYMENT_REPORTED', paid_amount = 0 WHERE id = ?", debtId);
        Debt debt = debtRepository.findById(debtId).orElseThrow();
        debt.rejectReportedPayment();
        debtRepository.saveAndFlush(debt);
        assertThat(statusOf(debtId)).isEqualTo("PENDING");

        at("2026-11-10");
        job.markOverdueDebts();

        assertThat(statusOf(debtId)).isEqualTo("OVERDUE");
    }

    @Test
    void changingTheGroupDeadlineDoesNotMoveAnExistingDeadline() {
        long groupId = group(5);
        long debtId = debt(groupId, "2026-11-01", "PENDING", "0.00");

        at("2026-11-07");
        jdbc.update("UPDATE groups SET debt_due_days = 30 WHERE id = ?", groupId);
        job.markOverdueDebts();

        // El vencimiento persistido (2026-11-06) manda; recalcular con 30 lo dejaría PENDING.
        assertThat(statusOf(debtId)).isEqualTo("OVERDUE");
    }

    @Test
    void theJobNeverTouchesOtherStates() {
        long groupId = group(5);
        long partial = debt(groupId, "2026-11-01", "PARTIALLY_PAID", "20.00");
        long reported = debt(groupId, "2026-11-01", "PAYMENT_REPORTED", "0.00");
        long paid = debt(groupId, "2026-11-01", "PAID", "100.00");
        long verified = debt(groupId, "2026-11-01", "VERIFIED", "100.00");

        at("2026-11-10");
        job.markOverdueDebts();

        assertThat(statusOf(partial)).isEqualTo("PARTIALLY_PAID");
        assertThat(statusOf(reported)).isEqualTo("PAYMENT_REPORTED");
        assertThat(statusOf(paid)).isEqualTo("PAID");
        assertThat(statusOf(verified)).isEqualTo("VERIFIED");
    }
}
