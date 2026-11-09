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
 * 025 T029: integration tests of the payer due notice with PostgreSQL and
 * GreenMail. One notice per expense, sent on the same date as the debt
 * reminders, with the payer's pending totals and no custom message.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@Testcontainers
class PayerNoticeIT {

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
    void sendsOneNoticePerExpenseToThePayerWithoutTheCustomMessage() throws Exception {
        long ana = user("Ana", "López");
        long juan = user("Juan", "Pérez");
        String anaEmail = emailOf(ana);
        long groupId = group("Grupo Amigos", 30, 5, "Mensaje especial");
        long expenseId = expense(groupId, ana, "2026-10-21", "Súper", "100.00");
        debt(groupId, expenseId, juan, ana, "PENDING", "100.00", "0.00");

        job.runReminders();

        assertThat(greenMail.waitForIncomingEmail(5_000, 2)).isTrue();
        MimeMessage notice = noticeTo(anaEmail, "«Súper»");
        assertThat(notice.getSubject()).isEqualTo("Aviso de vencimiento - Grupo Amigos");
        String body = decodedBody(notice);
        assertThat(body)
                .contains("Hola Ana López:")
                .contains("«Grupo Amigos»")
                .contains("«Súper»")
                .contains("20/11/2026")
                .contains("5 días restantes")
                .contains("No tenés deudas pendientes en el grupo.")
                .doesNotContain("Mensaje especial");

        assertThat(noticeStatus(expenseId)).isEqualTo("SENT");
        assertThat(eventCount(groupId, "PAYER_DUE_NOTICE_SENT")).isEqualTo(1);
        String details = eventDetails(groupId, expenseId);
        assertThat(details)
                .contains("\"reminderId\"", "\"expenseId\":" + expenseId)
                .contains("\"payerName\":\"Ana López\"", "\"concept\":\"Súper\"")
                .contains("\"dueDate\":\"2026-11-20\"", "\"daysRemaining\":5")
                .contains("\"pendingTotals\":[]");
    }

    @Test
    void thePayerSeesItsOwnPendingDebtsGroupedByCurrency() throws Exception {
        long ana = user("Ana", "López");
        long juan = user("Juan", "Pérez");
        String anaEmail = emailOf(ana);
        long groupId = group("Grupo Amigos", 30, 5, null);
        long superExpense = expense(groupId, ana, "2026-10-21", "Súper", "100.00");
        debt(groupId, superExpense, juan, ana, "PENDING", "100.00", "0.00");
        long taxiExpense = expense(groupId, juan, "2026-10-21", "Taxi", "80.00");
        debt(groupId, taxiExpense, ana, juan, "PARTIALLY_PAID", "80.00", "30.00");

        job.runReminders();

        assertThat(greenMail.waitForIncomingEmail(5_000, 4)).isTrue();
        String body = decodedBody(noticeTo(anaEmail, "«Súper»"));
        assertThat(body).contains("También tenés deudas pendientes en el grupo por 50.00 ARS.");

        String details = eventDetails(groupId, superExpense);
        assertThat(details).contains("\"currency\":\"ARS\"", "\"amount\":\"50.00\"");
    }

    @Test
    void aSettledExpenseCancelsTheScheduledNoticeAndSendsNothing() throws Exception {
        long ana = user("Ana", "López");
        long juan = user("Juan", "Pérez");
        long groupId = group("Grupo Amigos", 30, 5, null);
        long expenseId = expense(groupId, ana, "2026-10-21", "Súper", "100.00");
        debt(groupId, expenseId, juan, ana, "PAID", "100.00", "100.00");
        scheduledNotice(groupId, expenseId, "2026-11-15");

        job.runReminders();

        assertThat(noticeStatus(expenseId)).isEqualTo("CANCELLED");
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void twoExpensesOfTheSamePayerWithTheSameDeadlineGenerateTwoNotices() throws Exception {
        long ana = user("Ana", "López");
        long juan = user("Juan", "Pérez");
        String anaEmail = emailOf(ana);
        long groupId = group("Grupo Amigos", 30, 5, null);
        long superExpense = expense(groupId, ana, "2026-10-21", "Súper", "100.00");
        debt(groupId, superExpense, juan, ana, "PENDING", "100.00", "0.00");
        long taxiExpense = expense(groupId, ana, "2026-10-21", "Taxi", "80.00");
        debt(groupId, taxiExpense, juan, ana, "PENDING", "80.00", "0.00");

        job.runReminders();

        assertThat(greenMail.waitForIncomingEmail(5_000, 4)).isTrue();
        List<String> anaBodies = Arrays.stream(greenMail.getReceivedMessages())
                .filter(mail -> recipients(mail).contains(anaEmail))
                .filter(mail -> subjectOf(mail).equals("Aviso de vencimiento - Grupo Amigos"))
                .map(this::decodedBody)
                .toList();
        assertThat(anaBodies).hasSize(2);
        assertThat(anaBodies.get(0)).contains("«Súper»");
        assertThat(anaBodies.get(1)).contains("«Taxi»");
        assertThat(noticeStatus(superExpense)).isEqualTo("SENT");
        assertThat(noticeStatus(taxiExpense)).isEqualTo("SENT");
    }

    @Test
    void aLaterConfigurationChangeDoesNotResendTheNotice() throws Exception {
        long ana = user("Ana", "López");
        long juan = user("Juan", "Pérez");
        String anaEmail = emailOf(ana);
        long groupId = group("Grupo Amigos", 30, 5, null);
        long expenseId = expense(groupId, ana, "2026-10-21", "Súper", "100.00");
        debt(groupId, expenseId, juan, ana, "PENDING", "100.00", "0.00");

        job.runReminders();
        assertThat(greenMail.waitForIncomingEmail(5_000, 2)).isTrue();
        String sentBody = decodedBody(noticeTo(anaEmail, "«Súper»"));

        jdbc.update("UPDATE groups SET reminder_message = 'Otro mensaje', reminder_send_days = 7 WHERE id = ?",
                groupId);
        job.runReminders();

        assertThat(greenMail.getReceivedMessages()).hasSize(2);
        assertThat(noticeStatus(expenseId)).isEqualTo("SENT");
        assertThat(decodedBody(noticeTo(anaEmail, "«Súper»"))).isEqualTo(sentBody);
    }

    private void at(String date) {
        when(clock.instant()).thenReturn(LocalDate.parse(date).atTime(12, 0).atZone(ZONE).toInstant());
    }

    private long user(String first, String last) {
        return jdbc.queryForObject("""
                INSERT INTO users (first_name, last_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES (?, ?, ?, 'x', now(), now()) RETURNING id
                """, Long.class, first, last, "payer" + UUID.randomUUID() + "@example.com");
    }

    private String emailOf(long userId) {
        return jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, userId);
    }

    private long group(String name, Integer dueDays, Integer sendDays, String message) {
        return jdbc.queryForObject("""
                INSERT INTO groups (name, primary_currency, debt_due_days, reminder_send_days, reminder_message)
                VALUES (?, 'ARS', ?, ?, ?) RETURNING id
                """, Long.class, name, dueDays, sendDays, message);
    }

    private long expense(long groupId, long payerId, String date, String description, String amount) {
        long expenseId = jdbc.queryForObject("""
                INSERT INTO group_expenses (group_id, created_by_user_id, payer_user_id, amount, currency,
                    expense_date, description, idempotency_key)
                VALUES (?, ?, ?, ?, 'ARS', ?, ?, gen_random_uuid()) RETURNING id
                """, Long.class, groupId, payerId, payerId, new BigDecimal(amount),
                java.sql.Date.valueOf(date), description);
        jdbc.update("""
                UPDATE group_expenses e
                SET due_date = e.expense_date + g.debt_due_days
                FROM groups g
                WHERE g.id = e.group_id AND e.id = ? AND g.debt_due_days IS NOT NULL
                """, expenseId);
        return expenseId;
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

    private void scheduledNotice(long groupId, long expenseId, String sendDate) {
        jdbc.update("""
                INSERT INTO reminders (group_id, kind, expense_id, send_date, status)
                VALUES (?, 'PAYER_NOTICE', ?, ?, 'SCHEDULED')
                """, groupId, expenseId, java.sql.Date.valueOf(sendDate));
    }

    private String noticeStatus(long expenseId) {
        return jdbc.queryForObject(
                "SELECT status FROM reminders WHERE expense_id = ? AND kind = 'PAYER_NOTICE'",
                String.class, expenseId);
    }

    private int eventCount(long groupId, String eventType) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM group_activity WHERE group_id = ? AND event_type = ?",
                Integer.class, groupId, eventType);
    }

    private String eventDetails(long groupId, long expenseId) {
        return jdbc.queryForObject("""
                SELECT details_json FROM group_activity
                WHERE group_id = ? AND event_type = 'PAYER_DUE_NOTICE_SENT'
                  AND details_json LIKE ?
                """, String.class, groupId, "%\"expenseId\":" + expenseId + "%");
    }

    private MimeMessage noticeTo(String email, String concept) {
        return Arrays.stream(greenMail.getReceivedMessages())
                .filter(mail -> recipients(mail).contains(email))
                .filter(mail -> subjectOf(mail).equals("Aviso de vencimiento - Grupo Amigos"))
                .filter(mail -> decodedBody(mail).contains(concept))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se encontró el aviso para " + email));
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
