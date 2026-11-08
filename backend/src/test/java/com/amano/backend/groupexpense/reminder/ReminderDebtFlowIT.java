package com.amano.backend.groupexpense.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * 025 T013: integration tests of the debt reminder flow with PostgreSQL and an
 * in-process SMTP (GreenMail only, never a real mailbox). The clock is a mock
 * so "today" lands exactly on the send date or after the deadline.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@Testcontainers
class ReminderDebtFlowIT {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(
            new ServerSetup(0, "localhost", ServerSetup.PROTOCOL_SMTP))
            .withPerMethodLifecycle(false);

    @Autowired
    private ReminderJob job;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private Clock clock;

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> greenMail.getSmtp().getPort());
        registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");
        registry.add("spring.mail.properties.mail.smtp.auth", () -> "false");
    }

    @BeforeEach
    void setUp() throws Exception {
        when(clock.getZone()).thenReturn(ZONE);
        at("2026-11-15");
        greenMail.purgeEmailFromAllMailboxes();
    }

    @Test
    void sendsExactlyOneMailFiveDaysBeforeTheDeadlineAndIsIdempotent() throws Exception {
        long creditorId = user("Ana", "López");
        long debtorId = user("Juan", "Pérez");
        String debtorEmail = emailOf(debtorId);
        long groupId = group("Grupo Amigos", 30, null);
        long expenseId = expense(groupId, creditorId, "2026-10-21", "Súper", "100.00");
        long debtId = debt(groupId, expenseId, debtorId, creditorId, "PENDING", "100.00", "0.00");

        job.runReminders();

        assertThat(greenMail.waitForIncomingEmail(5_000, 2)).isTrue();
        List<MimeMessage> debtMails = messagesWithSubject("Recordatorio de deuda - Grupo Amigos");
        assertThat(debtMails).hasSize(1);
        MimeMessage mail = debtMails.get(0);
        assertThat(recipients(mail)).containsExactly(debtorEmail);
        assertThat(mail.getSubject()).isEqualTo("Recordatorio de deuda - Grupo Amigos");
        String body = decodedBody(mail);
        assertThat(body).contains("Hola Deudor:", "100.00 ARS", "«Súper»", "20/11/2026");

        assertThat(reminderStatus(debtId, "DEBT")).isEqualTo("SENT");
        assertThat(eventCount(groupId, "DEBT_REMINDER_SENT")).isEqualTo(1);
        String details = jdbc.queryForObject("""
                SELECT details_json FROM group_activity
                WHERE group_id = ? AND event_type = 'DEBT_REMINDER_SENT'
                """, String.class, groupId);
        assertThat(details).contains("\"reminderId\"", "\"debtId\"", "\"expenseId\"",
                "\"dueDate\":\"2026-11-20\"");

        job.runReminders();
        assertThat(messagesWithSubject("Recordatorio de deuda - Grupo Amigos")).hasSize(1);
        assertThat(eventCount(groupId, "DEBT_REMINDER_SENT")).isEqualTo(1);
    }

    @Test
    void aSettledDebtCancelsTheScheduledReminderAndSendsNothing() throws Exception {
        long creditorId = user("Ana", "López");
        long debtorId = user("Juan", "Pérez");
        long groupId = group("Grupo Amigos", 30, null);
        long expenseId = expense(groupId, creditorId, "2026-10-21", "Súper", "100.00");
        long debtId = debt(groupId, expenseId, debtorId, creditorId, "PAID", "100.00", "100.00");
        scheduledDebtReminder(groupId, debtId, "2026-11-15");

        job.runReminders();

        assertThat(reminderStatus(debtId, "DEBT")).isEqualTo("CANCELLED");
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void aReportedPaymentSendsTheVerificationLegend() throws Exception {
        long creditorId = user("Ana", "López");
        long debtorId = user("Juan", "Pérez");
        long groupId = group("Grupo Amigos", 30, null);
        long expenseId = expense(groupId, creditorId, "2026-10-21", "Súper", "100.00");
        debt(groupId, expenseId, debtorId, creditorId, "PAYMENT_REPORTED", "100.00", "100.00");

        job.runReminders();

        assertThat(greenMail.waitForIncomingEmail(5_000, 1)).isTrue();
        assertThat(decodedBody(greenMail.getReceivedMessages()[0])).contains("pagado a verificar");
    }

    @Test
    void aGroupWithoutDeadlineCreatesNoReminderAndCancelsExistingOnes() throws Exception {
        long creditorId = user("Ana", "López");
        long debtorId = user("Juan", "Pérez");
        long groupId = group("Grupo Sin Vencimiento", null, null);
        long expenseId = expense(groupId, creditorId, "2026-10-21", "Súper", "100.00");
        long debtId = debt(groupId, expenseId, debtorId, creditorId, "PENDING", "100.00", "0.00");
        scheduledDebtReminder(groupId, debtId, "2026-11-15");

        job.runReminders();

        assertThat(reminderStatus(debtId, "DEBT")).isEqualTo("CANCELLED");
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void aPastSendDateStillSendsWhileTheDeadlineIsAhead() throws Exception {
        long creditorId = user("Ana", "López");
        long debtorId = user("Juan", "Pérez");
        long groupId = group("Grupo Amigos", 30, 7);
        long expenseId = expense(groupId, creditorId, "2026-10-21", "Súper", "100.00");
        debt(groupId, expenseId, debtorId, creditorId, "PENDING", "100.00", "0.00");

        job.runReminders();

        assertThat(greenMail.waitForIncomingEmail(5_000, 1)).isTrue();
    }

    @Test
    void aDebtPastItsDeadlineIsCancelledAndNotSent() throws Exception {
        long creditorId = user("Ana", "López");
        long debtorId = user("Juan", "Pérez");
        long groupId = group("Grupo Amigos", 30, null);
        long expenseId = expense(groupId, creditorId, "2026-10-01", "Súper", "100.00");
        long debtId = debt(groupId, expenseId, debtorId, creditorId, "PENDING", "100.00", "0.00");
        scheduledDebtReminder(groupId, debtId, "2026-10-26");

        job.runReminders();

        assertThat(reminderStatus(debtId, "DEBT")).isEqualTo("CANCELLED");
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void registeredAndSimulatedDebtorsReceiveTheirOwnMail() throws Exception {
        long creditorId = user("Ana", "López");
        long debtorId = user("Juan", "Pérez");
        String debtorEmail = emailOf(debtorId);
        long groupId = group("Grupo Amigos", 30, null);
        long registeredExpense = expense(groupId, creditorId, "2026-10-21", "Súper", "100.00");
        debt(groupId, registeredExpense, debtorId, creditorId, "PENDING", "100.00", "0.00");
        long profileId = simulatedProfile(groupId, "Invitado", "invitado@example.com");
        long simulatedExpense = expense(groupId, creditorId, "2026-10-21", "Taxi", "50.00");
        simulatedDebt(groupId, simulatedExpense, profileId, creditorId, "PENDING", "50.00", "0.00");

        job.runReminders();

        assertThat(greenMail.waitForIncomingEmail(5_000, 4)).isTrue();
        List<String> recipients = messagesWithSubject("Recordatorio de deuda - Grupo Amigos").stream()
                .map(this::recipients)
                .flatMap(List::stream)
                .toList();
        assertThat(recipients).containsExactlyInAnyOrder(debtorEmail, "invitado@example.com");
    }

    @Test
    void aCustomMessageAccompaniesTheDebtData() throws Exception {
        long creditorId = user("Ana", "López");
        long debtorId = user("Juan", "Pérez");
        long groupId = group("Grupo Amigos", 30, null);
        jdbc.update("UPDATE groups SET reminder_message = ? WHERE id = ?", "Te esperamos", groupId);
        long expenseId = expense(groupId, creditorId, "2026-10-21", "Súper", "100.00");
        debt(groupId, expenseId, debtorId, creditorId, "PENDING", "100.00", "0.00");

        job.runReminders();

        assertThat(greenMail.waitForIncomingEmail(5_000, 2)).isTrue();
        String body = decodedBody(messagesWithSubject("Recordatorio de deuda - Grupo Amigos").get(0));
        assertThat(body)
                .contains("Te esperamos")
                .contains("Grupo: Grupo Amigos")
                .contains("Concepto: Súper")
                .contains("Monto: 100.00 ARS")
                .contains("Fecha límite: 20/11/2026");
    }

    @Test
    void aSentMailKeepsItsSnapshotWhenTheMessageChanges() throws Exception {
        long creditorId = user("Ana", "López");
        long debtorId = user("Juan", "Pérez");
        long groupId = group("Grupo Amigos", 30, null);
        long expenseId = expense(groupId, creditorId, "2026-10-21", "Súper", "100.00");
        debt(groupId, expenseId, debtorId, creditorId, "PENDING", "100.00", "0.00");

        job.runReminders();
        assertThat(greenMail.waitForIncomingEmail(5_000, 2)).isTrue();
        String sentBody = decodedBody(messagesWithSubject("Recordatorio de deuda - Grupo Amigos").get(0));

        jdbc.update("UPDATE groups SET reminder_message = 'Otro mensaje' WHERE id = ?", groupId);
        job.runReminders();

        List<MimeMessage> debtMails = messagesWithSubject("Recordatorio de deuda - Grupo Amigos");
        assertThat(debtMails).hasSize(1);
        assertThat(decodedBody(debtMails.get(0))).isEqualTo(sentBody);
    }

    @Test
    void theScheduledAnnotationUsesTheBuenosAiresZoneAndRunsAfterOverdue() throws Exception {
        var scheduled = ReminderJob.class.getMethod("runReminders")
                .getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.zone()).isEqualTo("America/Argentina/Buenos_Aires");
        assertThat(scheduled.cron()).isEqualTo("${reminders.cron:0 45 0 * * *}");
    }

    private void at(String date) {
        when(clock.instant()).thenReturn(LocalDate.parse(date).atTime(12, 0).atZone(ZONE).toInstant());
    }

    private long user(String first, String last) {
        return jdbc.queryForObject("""
                INSERT INTO users (first_name, last_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES (?, ?, ?, 'x', now(), now()) RETURNING id
                """, Long.class, first, last, "reminder" + UUID.randomUUID() + "@example.com");
    }

    private String emailOf(long userId) {
        return jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, userId);
    }

    private long group(String name, Integer dueDays, Integer sendDays) {
        if (sendDays == null) {
            return jdbc.queryForObject("""
                    INSERT INTO groups (name, primary_currency, debt_due_days)
                    VALUES (?, 'ARS', ?) RETURNING id
                    """, Long.class, name, dueDays);
        }
        return jdbc.queryForObject("""
                INSERT INTO groups (name, primary_currency, debt_due_days, reminder_send_days)
                VALUES (?, 'ARS', ?, ?) RETURNING id
                """, Long.class, name, dueDays, sendDays);
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

    private long simulatedProfile(long groupId, String name, String email) {
        return jdbc.queryForObject("""
                INSERT INTO simulated_profiles (group_id, name, email) VALUES (?, ?, ?) RETURNING id
                """, Long.class, groupId, name, email);
    }

    private long simulatedDebt(long groupId, long expenseId, long profileId, long creditorId, String status,
            String amount, String paid) {
        long shareId = jdbc.queryForObject("""
                INSERT INTO group_expense_shares (expense_id, position, participant_type, simulated_profile_id,
                    participant_name, amount)
                VALUES (?, 0, 'SIMULATED', ?, 'Invitado', ?) RETURNING id
                """, Long.class, expenseId, profileId, new BigDecimal(amount));
        return jdbc.queryForObject("""
                INSERT INTO debts (group_id, expense_id, share_id, debtor_type, debtor_simulated_profile_id,
                    debtor_name, creditor_user_id, currency, amount, paid_amount, status)
                VALUES (?, ?, ?, 'SIMULATED', ?, 'Invitado', ?, 'ARS', ?, ?, ?) RETURNING id
                """, Long.class, groupId, expenseId, shareId, profileId, creditorId,
                new BigDecimal(amount), new BigDecimal(paid), status);
    }

    private void scheduledDebtReminder(long groupId, long debtId, String sendDate) {
        jdbc.update("""
                INSERT INTO reminders (group_id, kind, debt_id, send_date, status)
                VALUES (?, 'DEBT', ?, ?, 'SCHEDULED')
                """, groupId, debtId, java.sql.Date.valueOf(sendDate));
    }

    private String reminderStatus(long debtId, String kind) {
        return jdbc.queryForObject("SELECT status FROM reminders WHERE debt_id = ? AND kind = ?",
                String.class, debtId, kind);
    }

    private int eventCount(long groupId, String eventType) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM group_activity WHERE group_id = ? AND event_type = ?",
                Integer.class, groupId, eventType);
    }

    private List<MimeMessage> messagesWithSubject(String subject) {
        return Arrays.stream(greenMail.getReceivedMessages())
                .filter(mail -> subject.equals(subjectOf(mail)))
                .toList();
    }

    private String subjectOf(MimeMessage mail) {
        try {
            return mail.getSubject();
        }
        catch (MessagingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private List<String> recipients(MimeMessage mail) {
        try {
            return Arrays.stream(mail.getAllRecipients()).map(Object::toString).toList();
        }
        catch (MessagingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String decodedBody(MimeMessage message) {
        String raw = GreenMailUtil.getBody(message).replace("=\r\n", "").replace("=\n", "");
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '=' && i + 2 < raw.length()) {
                try {
                    bytes.write(Integer.parseInt(raw.substring(i + 1, i + 3), 16));
                    i += 2;
                    continue;
                }
                catch (NumberFormatException notAnEscape) {
                    // literal '=' that was not encoded: fall through and copy it
                }
            }
            bytes.write((byte) c);
        }
        return bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
