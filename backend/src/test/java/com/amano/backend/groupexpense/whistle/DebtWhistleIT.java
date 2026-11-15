package com.amano.backend.groupexpense.whistle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.amano.backend.config.JwtService;
import com.amano.backend.groupexpense.reminder.ReminderMailService;
import com.amano.backend.groupexpense.reminder.ReminderMessageBuilder;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;

import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 030 T008, T022, T026: {@code POST /api/groups/{id}/debts/{debtId}/whistle}
 * against PostgreSQL and an in-process SMTP (GreenMail, never a real mailbox).
 * The clock is a mock so the 24 h window can be crossed exactly; the mail
 * service is a spy so a single scenario can make the SMTP fail.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class DebtWhistleIT {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final ZonedDateTime NOW = LocalDate.parse("2026-11-15").atTime(15, 0).atZone(ZONE);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(
            new ServerSetup(0, "localhost", ServerSetup.PROTOCOL_SMTP))
            .withPerMethodLifecycle(false);

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> greenMail.getSmtp().getPort());
        registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");
        registry.add("spring.mail.properties.mail.smtp.auth", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private Clock clock;

    @MockitoSpyBean
    private ReminderMailService mailService;

    private long ana;
    private long juan;
    private long carla;
    private long outsider;
    private long groupId;
    private Cookie anaSession;
    private Cookie juanSession;
    private Cookie carlaSession;
    private Cookie outsiderSession;

    @BeforeEach
    void setUp() throws Exception {
        when(clock.getZone()).thenReturn(ZONE);
        at(NOW);
        greenMail.purgeEmailFromAllMailboxes();
        doCallRealMethod().when(mailService).send(anyString(), any(ReminderMessageBuilder.Mail.class));

        ana = user("Ana", "López");
        juan = user("Juan", "Pérez");
        carla = user("Carla", "Gómez");
        outsider = user("Otra", "Persona");
        groupId = group("Viaje");
        member(ana, groupId, "ADMIN");
        member(juan, groupId, "MEMBER");
        member(carla, groupId, "MEMBER");
        anaSession = session(ana);
        juanSession = session(juan);
        carlaSession = session(carla);
        outsiderSession = session(outsider);
    }

    // ---- US1 (T008) ----

    @Test
    void theCreditorWhistlesAndTheDebtorGetsExactlyOneMail() throws Exception {
        long debt = debt(groupId, ana, juan, "Cena", "PENDING", "8000.00", "0.00");

        JsonNode body = whistle(anaSession, groupId, debt, 201);

        assertThat(body.get("debtId").asLong()).isEqualTo(debt);
        assertThat(body.get("debtorName").asString()).isEqualTo("Juan Pérez");
        assertThat(body.get("whistleId").asLong()).isPositive();
        assertThat(body.has("sentAt")).isTrue();
        assertThat(body.has("nextAvailableAt")).isTrue();
        assertThat(body.toString()).doesNotContain(emailOf(juan));

        assertThat(greenMail.waitForIncomingEmail(5_000, 1)).isTrue();
        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);
        assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo(emailOf(juan));
        assertThat(received[0].getSubject()).isEqualTo("Ana López te recuerda una deuda - Viaje");

        assertThat(jdbc.queryForObject("SELECT status FROM debts WHERE id = ?", String.class, debt))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT paid_amount FROM debts WHERE id = ?", BigDecimal.class, debt))
                .isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForObject("SELECT status FROM debt_whistles WHERE debt_id = ?", String.class, debt))
                .isEqualTo("SENT");
    }

    @Test
    void partialAndOverdueDebtsCanBeWhistled() throws Exception {
        whistle(anaSession, groupId, debt(groupId, ana, juan, "Cena", "PARTIALLY_PAID", "8000.00", "1000.00"), 201);
        long otherGroup = group("Depto");
        member(ana, otherGroup, "ADMIN");
        member(juan, otherGroup, "MEMBER");
        whistle(anaSession, otherGroup, debt(otherGroup, ana, juan, "Luz", "OVERDUE", "500.00", "0.00"), 201);
        assertThat(greenMail.waitForIncomingEmail(5_000, 2)).isTrue();
    }

    @Test
    void withoutSessionIs401() throws Exception {
        long debt = debt(groupId, ana, juan, "Cena", "PENDING", "8000.00", "0.00");
        mockMvc.perform(post(path(groupId, debt))).andExpect(status().isUnauthorized());
    }

    @Test
    void outsidersAndDebtsOfAnotherGroupGetTheNeutral404() throws Exception {
        long debt = debt(groupId, ana, juan, "Cena", "PENDING", "8000.00", "0.00");
        problem(outsiderSession, groupId, debt, 404);

        long otherGroup = group("Depto");
        member(ana, otherGroup, "ADMIN");
        problem(anaSession, otherGroup, debt, 404);
        problem(anaSession, groupId, 999_999L, 404);
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void theDebtorAndAThirdMemberGet403() throws Exception {
        long debt = debt(groupId, ana, juan, "Cena", "PENDING", "8000.00", "0.00");
        assertThat(problem(juanSession, groupId, debt, 403).at("/properties/code").asString())
                .isEqualTo("WHISTLE_NOT_CREDITOR");
        problem(carlaSession, groupId, debt, 403);
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void settledOrReportedDebtsAre409() throws Exception {
        JsonNode paid = problem(anaSession, groupId, debt(groupId, ana, juan, "Cena", "PAID", "100.00", "100.00"), 409);
        assertThat(paid.get("detail").asString()).isEqualTo("La deuda ya no tiene saldo pendiente");
        assertThat(paid.at("/properties/code").asString()).isEqualTo("DEBT_NOT_WHISTLEABLE");
        JsonNode reported = problem(anaSession, groupId,
                debt(groupId, ana, juan, "Taxi", "PAYMENT_REPORTED", "100.00", "0.00"), 409);
        assertThat(reported.get("detail").asString()).isEqualTo("La deuda tiene un pago esperando validación");
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void aSimulatedDebtorIs422AndReceivesNothing() throws Exception {
        long profile = jdbc.queryForObject("""
                INSERT INTO simulated_profiles (group_id, name, email) VALUES (?, 'Pepe', ?) RETURNING id
                """, Long.class, groupId, "pepe" + UUID.randomUUID() + "@example.com");
        long expense = expense(groupId, ana, "Nafta");
        long share = jdbc.queryForObject("""
                INSERT INTO group_expense_shares (expense_id, position, participant_type, simulated_profile_id,
                    participant_name, amount)
                VALUES (?, 0, 'SIMULATED', ?, 'Pepe', 3000.00) RETURNING id
                """, Long.class, expense, profile);
        long debt = jdbc.queryForObject("""
                INSERT INTO debts (group_id, expense_id, share_id, debtor_type, debtor_simulated_profile_id,
                    debtor_name, creditor_user_id, currency, amount, paid_amount, status)
                VALUES (?, ?, ?, 'SIMULATED', ?, 'Pepe', ?, 'ARS', 3000.00, 0.00, 'PENDING') RETURNING id
                """, Long.class, groupId, expense, share, profile, ana);

        JsonNode body = problem(anaSession, groupId, debt, 422);

        assertThat(body.get("detail").asString()).isEqualTo("Los perfiles simulados no reciben silbidos");
        assertThat(body.at("/properties/code").asString()).isEqualTo("SIMULATED_DEBTOR");
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void aDebtorThatIsNoLongerAMemberIs409() throws Exception {
        long debt = debt(groupId, ana, juan, "Cena", "PENDING", "8000.00", "0.00");
        jdbc.update("UPDATE group_members SET left_at = now() WHERE group_id = ? AND user_id = ?", groupId, juan);

        assertThat(problem(anaSession, groupId, debt, 409).at("/properties/code").asString())
                .isEqualTo("DEBTOR_NOT_MEMBER");
    }

    @Test
    void anSmtpFailureIs503LeavesNoEventAndDoesNotConsumeTheLimit() throws Exception {
        long debt = debt(groupId, ana, juan, "Cena", "PENDING", "8000.00", "0.00");
        doThrow(new MailSendException("smtp down")).when(mailService)
                .send(anyString(), any(ReminderMessageBuilder.Mail.class));

        JsonNode body = problem(anaSession, groupId, debt, 503);

        assertThat(body.get("detail").asString())
                .isEqualTo("No pudimos enviar el aviso. Probá de nuevo en unos minutos");
        assertThat(body.toString()).doesNotContain("smtp down");
        assertThat(whistleStatuses(debt)).containsExactly("FAILED");
        assertThat(eventCount("DEBT_WHISTLED")).isZero();

        doCallRealMethod().when(mailService).send(anyString(), any(ReminderMessageBuilder.Mail.class));
        whistle(anaSession, groupId, debt, 201);
        assertThat(greenMail.waitForIncomingEmail(5_000, 1)).isTrue();
    }

    // ---- US2 (T022) ----

    @Test
    void aSecondWhistleToTheSamePairIs429UntilTheWindowEnds() throws Exception {
        long cena = debt(groupId, ana, juan, "Cena", "PENDING", "8000.00", "0.00");
        long taxi = debt(groupId, ana, juan, "Taxi", "PENDING", "500.00", "0.00");
        whistle(anaSession, groupId, cena, 201);

        at(NOW.plusHours(6).plusMinutes(20));
        mockMvc.perform(post(path(groupId, taxi)).cookie(anaSession))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", String.valueOf(17 * 3600 + 40 * 60)))
                .andExpect(jsonPath("$.properties.code").value("WHISTLE_RATE_LIMITED"))
                .andExpect(jsonPath("$.properties.retryAfterSeconds").value(17 * 3600 + 40 * 60))
                .andExpect(jsonPath("$.detail").value("Ya le silbaste a Juan Pérez en las últimas 24 horas"));
        assertThat(greenMail.waitForIncomingEmail(2_000, 2)).isFalse();

        at(NOW.plusHours(24));
        whistle(anaSession, groupId, taxi, 201);
        assertThat(greenMail.waitForIncomingEmail(5_000, 2)).isTrue();
    }

    @Test
    void theLimitIsPerPairAndPerGroup() throws Exception {
        whistle(anaSession, groupId, debt(groupId, ana, juan, "Cena", "PENDING", "8000.00", "0.00"), 201);
        whistle(anaSession, groupId, debt(groupId, ana, carla, "Cena", "PENDING", "8000.00", "0.00"), 201);
        long otherGroup = group("Depto");
        member(ana, otherGroup, "ADMIN");
        member(juan, otherGroup, "MEMBER");
        whistle(anaSession, otherGroup, debt(otherGroup, ana, juan, "Luz", "PENDING", "500.00", "0.00"), 201);
        assertThat(greenMail.waitForIncomingEmail(5_000, 3)).isTrue();
    }

    @Test
    void twoSimultaneousWhistlesSendExactlyOneMail() throws Exception {
        long debt = debt(groupId, ana, juan, "Cena", "PENDING", "8000.00", "0.00");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return mockMvc.perform(post(path(groupId, debt)).cookie(anaSession))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 429);
        }
        finally {
            pool.shutdownNow();
        }
        assertThat(greenMail.waitForIncomingEmail(5_000, 1)).isTrue();
        assertThat(greenMail.waitForIncomingEmail(1_000, 2)).isFalse();
        assertThat(whistleStatuses(debt)).containsExactly("SENT");
    }

    // ---- US3 (T026) ----

    @Test
    void aDeliveredWhistleIsInTheHistoryWithTheCreditorAsActor() throws Exception {
        long debt = debt(groupId, ana, juan, "Cena", "PARTIALLY_PAID", "8000.00", "1000.00");
        JsonNode body = whistle(anaSession, groupId, debt, 201);

        assertThat(eventCount("DEBT_WHISTLED")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT actor_user_id FROM group_activity WHERE group_id = ? AND event_type = 'DEBT_WHISTLED'",
                Long.class, groupId)).isEqualTo(ana);
        JsonNode details = objectMapper.readTree(jdbc.queryForObject(
                "SELECT details_json FROM group_activity WHERE group_id = ? AND event_type = 'DEBT_WHISTLED'",
                String.class, groupId));
        assertThat(details.get("whistleId").asLong()).isEqualTo(body.get("whistleId").asLong());
        assertThat(details.get("debtId").asLong()).isEqualTo(debt);
        assertThat(details.get("amount").asString()).isEqualTo("7000.00");
        assertThat(details.get("currency").asString()).isEqualTo("ARS");
        assertThat(details.get("fromName").asString()).isEqualTo("Juan Pérez");
        assertThat(details.get("toName").asString()).isEqualTo("Ana López");
        assertThat(details.get("description").asString()).isEqualTo("Cena");
    }

    @Test
    void rejectedAttemptsLeaveNoEvent() throws Exception {
        long debt = debt(groupId, ana, juan, "Cena", "PENDING", "8000.00", "0.00");
        whistle(anaSession, groupId, debt, 201);
        problem(anaSession, groupId, debt, 429);
        problem(juanSession, groupId, debt, 403);
        assertThat(eventCount("DEBT_WHISTLED")).isEqualTo(1);
    }

    // ---- helpers ----

    private JsonNode whistle(Cookie session, long group, long debt, int expectedStatus) throws Exception {
        String content = mockMvc.perform(post(path(group, debt)).cookie(session))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content);
    }

    private JsonNode problem(Cookie session, long group, long debt, int expectedStatus) throws Exception {
        return whistle(session, group, debt, expectedStatus);
    }

    private static String path(long group, long debt) {
        return "/api/groups/" + group + "/debts/" + debt + "/whistle";
    }

    private void at(ZonedDateTime instant) {
        when(clock.instant()).thenReturn(instant.toInstant());
    }

    private Cookie session(long userId) {
        return new Cookie("amano_auth", jwtService.issueToken(userId));
    }

    private long user(String first, String last) {
        return jdbc.queryForObject("""
                INSERT INTO users (first_name, last_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES (?, ?, ?, 'x', now(), now()) RETURNING id
                """, Long.class, first, last, "whistle" + UUID.randomUUID() + "@example.com");
    }

    private String emailOf(long userId) {
        return jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, userId);
    }

    private long group(String name) {
        return jdbc.queryForObject("""
                INSERT INTO groups (name, primary_currency, debt_due_days) VALUES (?, 'ARS', 7) RETURNING id
                """, Long.class, name);
    }

    private void member(long userId, long group, String role) {
        jdbc.update("INSERT INTO group_members (user_id, group_id, role) VALUES (?, ?, ?)", userId, group, role);
    }

    private long expense(long group, long payerId, String description) {
        return jdbc.queryForObject("""
                INSERT INTO group_expenses (group_id, created_by_user_id, payer_user_id, amount, currency,
                    expense_date, description, idempotency_key)
                VALUES (?, ?, ?, 10000.00, 'ARS', DATE '2026-11-01', ?, gen_random_uuid()) RETURNING id
                """, Long.class, group, payerId, payerId, description);
    }

    private long debt(long group, long creditorId, long debtorId, String description, String status,
            String amount, String paid) {
        long expense = expense(group, creditorId, description);
        long share = jdbc.queryForObject("""
                INSERT INTO group_expense_shares (expense_id, position, participant_type, user_id,
                    participant_name, amount)
                VALUES (?, 0, 'REGISTERED', ?, 'Deudor', ?) RETURNING id
                """, Long.class, expense, debtorId, new BigDecimal(amount));
        String debtorName = jdbc.queryForObject(
                "SELECT first_name || ' ' || last_name FROM users WHERE id = ?", String.class, debtorId);
        return jdbc.queryForObject("""
                INSERT INTO debts (group_id, expense_id, share_id, debtor_type, debtor_user_id, debtor_name,
                    creditor_user_id, currency, amount, paid_amount, status)
                VALUES (?, ?, ?, 'REGISTERED', ?, ?, ?, 'ARS', ?, ?, ?) RETURNING id
                """, Long.class, group, expense, share, debtorId, debtorName, creditorId,
                new BigDecimal(amount), new BigDecimal(paid), status);
    }

    private List<String> whistleStatuses(long debt) {
        return jdbc.queryForList("SELECT status FROM debt_whistles WHERE debt_id = ? ORDER BY id", String.class, debt);
    }

    private int eventCount(String eventType) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM group_activity WHERE group_id = ? AND event_type = ?",
                Integer.class, groupId, eventType);
    }
}
