package com.amano.backend.groupexpense.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 025 T013: delivery failures. The mail service is mocked so each scenario can
 * fail only the debtor mail (the admin notice still succeeds) or every mail.
 * The dispatcher must mark FAILED, notify the active admins, never retry and
 * never propagate the failure.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@Testcontainers
class ReminderFailureIT {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private ReminderJob job;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private ReminderMailService mailService;

    @BeforeEach
    void setUp() {
        when(clock.getZone()).thenReturn(ZONE);
        when(clock.instant()).thenReturn(LocalDate.parse("2026-11-15").atTime(12, 0).atZone(ZONE).toInstant());
    }

    @Test
    void aFailedDeliveryMarksFailedNotifiesAdminsAndDoesNotRetry() throws Exception {
        long creditorId = user("Ana", "López");
        long debtorId = user("Juan", "Pérez");
        long adminId = user("Admin", "Grupo");
        String debtorEmail = emailOf(debtorId);
        String adminEmail = emailOf(adminId);
        long groupId = group("Grupo Amigos", 30);
        member(adminId, groupId, "ADMIN");
        long expenseId = expense(groupId, creditorId, "2026-10-21", "Súper", "100.00");
        long debtId = debt(groupId, expenseId, debtorId, creditorId, "PENDING", "100.00", "0.00");

        doThrow(new MailSendException("boom")).when(mailService).send(eq(debtorEmail),
                any(ReminderMessageBuilder.Mail.class));

        job.runReminders();

        assertThat(reminderStatus(debtId)).isEqualTo("FAILED");
        verify(mailService, times(1)).send(eq(debtorEmail), any(ReminderMessageBuilder.Mail.class));
        verify(mailService, times(1)).send(eq(adminEmail), any(ReminderMessageBuilder.Mail.class));
        assertThat(eventCount(groupId, "DEBT_REMINDER_SENT")).isZero();

        job.runReminders();
        verify(mailService, times(1)).send(eq(debtorEmail), any(ReminderMessageBuilder.Mail.class));
    }

    @Test
    void aFailureOfTheAdminNoticeOnlyLogsAndDoesNotPropagate() throws Exception {
        long creditorId = user("Ana", "López");
        long debtorId = user("Juan", "Pérez");
        long adminId = user("Admin", "Grupo");
        String debtorEmail = emailOf(debtorId);
        String adminEmail = emailOf(adminId);
        long groupId = group("Grupo Amigos", 30);
        member(adminId, groupId, "ADMIN");
        long expenseId = expense(groupId, creditorId, "2026-10-21", "Súper", "100.00");
        long debtId = debt(groupId, expenseId, debtorId, creditorId, "PENDING", "100.00", "0.00");

        doThrow(new MailSendException("smtp down")).when(mailService).send(eq(debtorEmail),
                any(ReminderMessageBuilder.Mail.class));
        doThrow(new MailSendException("smtp down")).when(mailService).send(eq(adminEmail),
                any(ReminderMessageBuilder.Mail.class));

        assertThatCode(() -> job.runReminders()).doesNotThrowAnyException();

        assertThat(reminderStatus(debtId)).isEqualTo("FAILED");
        verify(mailService, times(1)).send(eq(debtorEmail), any(ReminderMessageBuilder.Mail.class));
        verify(mailService, times(1)).send(eq(adminEmail), any(ReminderMessageBuilder.Mail.class));
    }

    @Test
    void aGroupWithoutActiveAdminsOnlyLogsTheFailure() throws Exception {
        long creditorId = user("Ana", "López");
        long debtorId = user("Juan", "Pérez");
        String debtorEmail = emailOf(debtorId);
        long groupId = group("Grupo Sin Admin", 30);
        long expenseId = expense(groupId, creditorId, "2026-10-21", "Súper", "100.00");
        long debtId = debt(groupId, expenseId, debtorId, creditorId, "PENDING", "100.00", "0.00");

        doThrow(new MailSendException("boom")).when(mailService).send(eq(debtorEmail),
                any(ReminderMessageBuilder.Mail.class));

        assertThatCode(() -> job.runReminders()).doesNotThrowAnyException();

        assertThat(reminderStatus(debtId)).isEqualTo("FAILED");
        verify(mailService, times(1)).send(eq(debtorEmail), any(ReminderMessageBuilder.Mail.class));
    }

    @Test
    void anOrphanClaimedRowBecomesFailedAndNotifiesTheAdmins() throws Exception {
        long creditorId = user("Ana", "López");
        long debtorId = user("Juan", "Pérez");
        long adminId = user("Admin", "Grupo");
        String adminEmail = emailOf(adminId);
        long groupId = group("Grupo Amigos", 30);
        member(adminId, groupId, "ADMIN");
        long expenseId = expense(groupId, creditorId, "2026-10-21", "Súper", "100.00");
        long debtId = debt(groupId, expenseId, debtorId, creditorId, "PENDING", "100.00", "0.00");
        jdbc.update("""
                INSERT INTO reminders (group_id, kind, debt_id, send_date, status, claimed_at)
                VALUES (?, 'DEBT', ?, DATE '2026-11-15', 'SCHEDULED', now())
                """, groupId, debtId);

        job.runReminders();

        assertThat(reminderStatus(debtId)).isEqualTo("FAILED");
        verify(mailService, times(1)).send(eq(adminEmail), any(ReminderMessageBuilder.Mail.class));
    }

    private long user(String first, String last) {
        return jdbc.queryForObject("""
                INSERT INTO users (first_name, last_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES (?, ?, ?, 'x', now(), now()) RETURNING id
                """, Long.class, first, last, "failure" + UUID.randomUUID() + "@example.com");
    }

    private String emailOf(long userId) {
        return jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, userId);
    }

    private long group(String name, Integer dueDays) {
        return jdbc.queryForObject("""
                INSERT INTO groups (name, primary_currency, debt_due_days)
                VALUES (?, 'ARS', ?) RETURNING id
                """, Long.class, name, dueDays);
    }

    private void member(long userId, long groupId, String role) {
        jdbc.update("INSERT INTO group_members (user_id, group_id, role) VALUES (?, ?, ?)",
                userId, groupId, role);
    }

    private long expense(long groupId, long payerId, String date, String description, String amount) {
        return jdbc.queryForObject("""
                INSERT INTO group_expenses (group_id, created_by_user_id, payer_user_id, amount, currency,
                    expense_date, description, idempotency_key)
                VALUES (?, ?, ?, ?, 'ARS', ?, ?, gen_random_uuid()) RETURNING id
                """, Long.class, groupId, payerId, payerId, new BigDecimal(amount),
                java.sql.Date.valueOf(date), description);
    }

    private long debt(long groupId, long expenseId, long debtorId, long creditorId, String status,
            String amount, String paid) {
        long shareId = jdbc.queryForObject("""
                INSERT INTO group_expense_shares (expense_id, position, participant_type, user_id,
                    participant_name, amount)
                VALUES (?, 0, 'REGISTERED', ?, 'Deudor', ?) RETURNING id
                """, Long.class, expenseId, debtorId, new BigDecimal(amount));
        return jdbc.queryForObject("""
                INSERT INTO debts (group_id, expense_id, share_id, debtor_type, debtor_user_id, debtor_name,
                    creditor_user_id, currency, amount, paid_amount, status)
                VALUES (?, ?, ?, 'REGISTERED', ?, 'Deudor', ?, 'ARS', ?, ?, ?) RETURNING id
                """, Long.class, groupId, expenseId, shareId, debtorId, creditorId,
                new BigDecimal(amount), new BigDecimal(paid), status);
    }

    private String reminderStatus(long debtId) {
        return jdbc.queryForObject("SELECT status FROM reminders WHERE debt_id = ? AND kind = 'DEBT'",
                String.class, debtId);
    }

    private int eventCount(long groupId, String eventType) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM group_activity WHERE group_id = ? AND event_type = ?",
                Integer.class, groupId, eventType);
    }
}
