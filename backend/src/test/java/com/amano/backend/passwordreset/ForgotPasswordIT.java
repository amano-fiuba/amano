package com.amano.backend.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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

import jakarta.mail.Address;
import jakarta.mail.internet.MimeMessage;

/**
 * Contract tests of {@code POST /api/auth/forgot-password} with a real
 * PostgreSQL and an in-process SMTP (feature 006). GreenMail ONLY — never real
 * Gmail (FR-009). Covers FR-001/FR-002 (public, always the same 200), FR-004
 * (only verified accounts get the mail), FR-005 (the mailed token's hash is the
 * stored one) and FR-007 (a new request voids the previous token).
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class ForgotPasswordIT {

    private static final String NEUTRAL_MESSAGE =
            "Si el correo existe, recibirás un enlace para restablecer tu contraseña";

    private static final String RESET_SUBJECT = "Restablece tu contraseña - A Mano";

    private static final Pattern TOKEN_IN_LINK = Pattern.compile("/reset-password\\?token=([A-Za-z0-9_-]+)");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

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
        registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");
        registry.add("spring.mail.properties.mail.smtp.auth", () -> "false");
        registry.add("app.base-url", () -> "http://localhost:5173");
    }

    @BeforeEach
    void cleanMailbox() throws Exception {
        greenMail.purgeEmailFromAllMailboxes();
    }

    private static MockPart part(String name, String value) {
        return new MockPart(name, value.getBytes(StandardCharsets.UTF_8));
    }

    /** Registers through the real endpoint and marks the account as verified. */
    private void registerVerified(String email) throws Exception {
        mockMvc.perform(multipart("/api/auth/register").part(
                        part("firstName", "Ana"),
                        part("lastName", "Pérez"),
                        part("email", email),
                        part("password", "Password123!"),
                        part("confirmPassword", "Password123!"),
                        part("acceptTerms", "true")))
                .andExpect(status().isCreated());
        jdbcTemplate.update("UPDATE users SET email_verified_at = CURRENT_TIMESTAMP WHERE email = ?", email);
    }

    /** Requests the reset link and asserts the always-200 neutral contract (FR-002). */
    private MvcResult forgotPassword(String body) throws Exception {
        return mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(NEUTRAL_MESSAGE))
                .andReturn();
    }

    private MvcResult forgotPasswordFor(String email) throws Exception {
        return forgotPassword("{\"email\":\"" + email + "\"}");
    }

    /** Waits (up to 5 s) for at least {@code expected} reset mails addressed to the recipient. */
    private List<MimeMessage> awaitResetMailsFor(String recipient, int expected) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        List<MimeMessage> received = List.of();
        while (System.currentTimeMillis() < deadline) {
            received = resetMailsFor(recipient);
            if (received.size() >= expected) {
                return received;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("expected at least " + expected + " reset mail(s) for " + recipient
                + " but got " + received.size());
    }

    /** Only the reset mails: the registration also sends a verification mail. */
    private List<MimeMessage> resetMailsFor(String recipient) throws Exception {
        List<MimeMessage> received = new ArrayList<>();
        for (MimeMessage message : greenMail.getReceivedMessages()) {
            if (!RESET_SUBJECT.equals(message.getSubject())) {
                continue;
            }
            for (Address address : message.getAllRecipients()) {
                if (recipient.equals(address.toString())) {
                    received.add(message);
                }
            }
        }
        return received;
    }

    private static String extractClearToken(MimeMessage message) {
        Matcher matcher = TOKEN_IN_LINK.matcher(decodedBody(message));
        assertThat(matcher.find())
                .withFailMessage("the mail body must carry the reset link with a token")
                .isTrue();
        return matcher.group(1);
    }

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
        return bytes.toString(StandardCharsets.UTF_8);
    }

    /** Independent SHA-256: the test never reuses production hashing. */
    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    /** FR-003/FR-004/FR-005: a verified account receives the link and only its hash is stored. */
    @Test
    void verifiedAccountReceivesTheLinkAndOnlyTheHashIsStored() throws Exception {
        String email = "recupero@ejemplo.com";
        registerVerified(email);

        forgotPasswordFor("  Recupero@Ejemplo.COM ");

        String clearToken = extractClearToken(awaitResetMailsFor(email, 1).get(0));
        Integer current = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM password_reset_tokens t JOIN users u ON u.id = t.user_id "
                        + "WHERE u.email = ? AND t.token_hash = ? AND t.used_at IS NULL "
                        + "AND t.expires_at > now()",
                Integer.class, email, sha256Hex(clearToken));
        assertThat(current).isEqualTo(1);
        Integer clearStored = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM password_reset_tokens WHERE token_hash = ?", Integer.class, clearToken);
        assertThat(clearStored).isZero();
    }

    /** FR-007: a second request voids the first token. */
    @Test
    void aNewRequestInvalidatesThePreviousToken() throws Exception {
        String email = "doble@ejemplo.com";
        registerVerified(email);

        forgotPasswordFor(email);
        String first = extractClearToken(awaitResetMailsFor(email, 1).get(0));
        forgotPasswordFor(email);
        String second = extractClearToken(awaitResetMailsFor(email, 2).get(1));

        assertThat(second).isNotEqualTo(first);
        Integer firstUsed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM password_reset_tokens WHERE token_hash = ? AND used_at IS NOT NULL",
                Integer.class, sha256Hex(first));
        assertThat(firstUsed).isEqualTo(1);
    }

    /** FR-002/FR-004: unknown, verified and malformed requests answer byte-for-byte the same. */
    @Test
    void everyCaseAnswersIdenticallyAndUnknownAddressesGetNoMail() throws Exception {
        String verified = "existe@ejemplo.com";
        registerVerified(verified);

        String known = forgotPasswordFor(verified).getResponse().getContentAsString();
        String unknown = forgotPasswordFor("no.existe@ejemplo.com").getResponse().getContentAsString();
        String empty = forgotPassword("{}").getResponse().getContentAsString();

        assertThat(unknown).isEqualTo(known);
        assertThat(empty).isEqualTo(known);
        awaitResetMailsFor(verified, 1);
        assertThat(resetMailsFor("no.existe@ejemplo.com")).isEmpty();
    }
}
