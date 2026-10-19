package com.amano.backend.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockPart;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;

import jakarta.mail.internet.MimeMessage;

/**
 * Full US1 flow against a real SMTP server in process (task T016, TDD: written
 * BEFORE the implementation). GreenMail ONLY — never real Gmail (research.md D9,
 * team rule). Covers FR-001 (unverified account, no session), FR-003/SC-001
 * (Spanish mail with the verification link), FR-004 (GET /me stays closed),
 * FR-009/FR-015 (re-registration over pending invalidates the first token) and
 * D2 (only the SHA-256 hash reaches the database, never the clear token).
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class RegistrationVerificationIT {

    /** Link base asserted in the mail body (research.md section 4: the Vite dev server). */
    private static final String BASE_URL = "http://localhost:5173";

    private static final Pattern TOKEN_IN_LINK = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    /**
     * In-process SMTP on a random port: the tests never touch real Gmail (D9).
     * Lifecycle by CLASS (not the constructor' s default per method): the port
     * must be resolved when the Spring context loads, before the first test.
     */
    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(
            new ServerSetup(0, "localhost", ServerSetup.PROTOCOL_SMTP))
            .withPerMethodLifecycle(false);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> greenMail.getSmtp().getPort());
        registry.add("app.base-url", () -> BASE_URL);
    }

    @BeforeEach
    void cleanMailbox() throws Exception {
        greenMail.purgeEmailFromAllMailboxes();
    }

    private static MockPart part(String name, String value) {
        return new MockPart(name, value.getBytes(StandardCharsets.UTF_8));
    }

    private static MockPart[] mandatoryFields(String email) {
        return new MockPart[] {
                part("firstName", "Ana"),
                part("lastName", "Pérez"),
                part("email", email),
                part("password", "Password123!"),
                part("confirmPassword", "Password123!"),
                part("acceptTerms", "true"),
        };
    }

    private static String extractClearToken(MimeMessage message) throws Exception {
        Matcher matcher = TOKEN_IN_LINK.matcher(decodedBody(message));
        assertThat(matcher.find())
                .withFailMessage("the mail body must carry the verification link with a token")
                .isTrue();
        return matcher.group(1);
    }

    /**
     * Waits (up to 5 s, SC-001) for at least {@code expected} messages addressed to
     * the recipient and returns them in arrival order. The sends are asynchronous
     * (FR-003), so every test awaits its own mail: that keeps a late delivery from
     * one test out of the next one's mailbox.
     */
    private java.util.List<MimeMessage> awaitMailsFor(String recipient, int expected) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        java.util.List<MimeMessage> received = java.util.List.of();
        while (System.currentTimeMillis() < deadline) {
            received = mailsFor(recipient);
            if (received.size() >= expected) {
                return received;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("expected at least " + expected + " verification mail(s) for "
                + recipient + " but got " + received.size());
    }

    private java.util.List<MimeMessage> mailsFor(String recipient) throws Exception {
        java.util.List<MimeMessage> received = new java.util.ArrayList<>();
        for (MimeMessage message : greenMail.getReceivedMessages()) {
            for (jakarta.mail.Address address : message.getAllRecipients()) {
                if (recipient.equals(address.toString())) {
                    received.add(message);
                }
            }
        }
        return received;
    }

    /**
     * JavaMail transfers non-ASCII text as quoted-printable; decode it the way a
     * mail client would so the assertions read the real Spanish body (and the
     * link is whole again after the soft line breaks).
     */
    private static String decodedBody(MimeMessage message) {
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

    /** Independent SHA-256 (D2): the test never reuses production hashing. */
    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    /** FR-001, FR-003, FR-004, D2, SC-001, SC-002: register → unverified account + delivered link. */
    @Test
    void registrationLeavesAccountUnverifiedAndDeliversTheVerificationLink() throws Exception {
        mockMvc.perform(multipart("/api/auth/register")
                        .part(mandatoryFields("  Ana.Verifica@Example.COM ")))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Set-Cookie")) // FR-001: no auto-login
                .andExpect(jsonPath("$.email").value("ana.verifica@example.com"));

        MimeMessage message = awaitMailsFor("ana.verifica@example.com", 1).get(0);
        assertThat(message.getSubject()).isEqualTo("Verifica tu correo electrónico - A Mano");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("ana.verifica@example.com");
        String body = decodedBody(message);
        assertThat(body).contains(BASE_URL + "/verify-email?token=");
        String clearToken = extractClearToken(message);

        // FR-001/FR-004: the account exists and is blocked.
        Integer unverified = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ? AND email_verified_at IS NULL",
                Integer.class, "ana.verifica@example.com");
        assertThat(unverified).isEqualTo(1);

        // D2: only the SHA-256 hash is persisted; the clear token is nowhere in the database.
        String storedHash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM email_verification_tokens evt "
                        + "JOIN users u ON u.id = evt.user_id WHERE u.email = ?",
                String.class, "ana.verifica@example.com");
        assertThat(storedHash).isEqualTo(sha256Hex(clearToken));
        Integer clearInDb = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_verification_tokens WHERE token_hash = ?",
                Integer.class, clearToken);
        assertThat(clearInDb).isZero();

        // No session was issued: protected resources stay closed (SC-002).
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    /** FR-009 + FR-015 end to end: a new registration over pending invalidates the first token. */
    @Test
    void reRegistrationOverPendingInvalidatesTheFirstTokenAndIssuesANewLink() throws Exception {
        mockMvc.perform(multipart("/api/auth/register")
                        .part(mandatoryFields("re@ejemplo.com")))
                .andExpect(status().isCreated());
        String firstToken = extractClearToken(awaitMailsFor("re@ejemplo.com", 1).get(0));

        // Same pending address, another password: still one account, new token, new mail.
        mockMvc.perform(multipart("/api/auth/register")
                        .part(new MockPart[] {
                                part("firstName", "Otra"),
                                part("email", "RE@EJEMPLO.COM"),
                                part("password", "OtraClave123!"),
                                part("confirmPassword", "OtraClave123!"),
                                part("acceptTerms", "true"),
                        }))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Set-Cookie"));
        String secondToken = extractClearToken(awaitMailsFor("re@ejemplo.com", 2).get(1));
        assertThat(secondToken).isNotEqualTo(firstToken);

        Integer firstUsed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_verification_tokens WHERE token_hash = ? AND used_at IS NOT NULL",
                Integer.class, sha256Hex(firstToken));
        assertThat(firstUsed).isEqualTo(1); // FR-009: the previous token is invalidated
        Integer secondCurrent = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_verification_tokens WHERE token_hash = ? AND used_at IS NULL",
                Integer.class, sha256Hex(secondToken));
        assertThat(secondCurrent).isEqualTo(1);
        Integer accounts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, "re@ejemplo.com");
        assertThat(accounts).isEqualTo(1); // FR-015: no second account is created
    }

    /** Sanity of the flow for the remaining ITs: the response body of the new contract. */
    @Test
    void registerAnswersOnlyTheNormalizedEmail() throws Exception {
        MvcResult created = mockMvc.perform(multipart("/api/auth/register")
                        .part(mandatoryFields("solo-correo@ejemplo.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("solo-correo@ejemplo.com"))
                .andReturn();

        assertThat(created.getResponse().getContentAsString())
                .doesNotContain("firstName")
                .doesNotContain("hasCustomAvatar")
                .doesNotContain("passwordHash");

        // Await the async send so no delivery is left in flight for the next test.
        awaitMailsFor("solo-correo@ejemplo.com", 1);
    }
}
