package com.amano.backend.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.amano.backend.support.AbstractIntegrationIT;
import com.icegreen.greenmail.util.GreenMailUtil;

import jakarta.mail.internet.MimeMessage;

/**
 * Full US1 flow against a real SMTP server in process (task T016, TDD: written
 * BEFORE the implementation). GreenMail ONLY — never real Gmail (research.md D9,
 * team rule). Covers FR-001 (unverified account, no session), FR-003/SC-001
 * (Spanish mail with the verification link), FR-004 (GET /me stays closed),
 * FR-009/FR-015 (re-registration over pending invalidates the first token) and
 * D2 (only the SHA-256 hash reaches the database, never the clear token).
 */
class RegistrationVerificationIT extends AbstractIntegrationIT {

    /** Link base asserted in the mail body (research.md section 4: the Vite dev server). */
    private static final String BASE_URL = "http://localhost:5173";

    private static final Pattern TOKEN_IN_LINK = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
            Thread.sleep(10);
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

    /**
     * Q2 (FR-015, task T044): a re-registration over a PENDING account respects an
     * active resend term — the data and the token are replaced, but no mail goes
     * out until the term expires.
     */
    @Test
    void reRegistrationOverPendingRespectsAnActiveResendLimit() throws Exception {
        String email = "q2@ejemplo.com";
        mockMvc.perform(multipart("/api/auth/register").part(mandatoryFields(email)))
                .andExpect(status().isCreated());
        awaitMailsFor(email, 1);

        // A resend click sends (no term yet) and starts the 5-minute window.
        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());
        String resentToken = extractClearToken(awaitMailsFor(email, 2).get(1));

        // Re-registration inside the term: data replaced, new token issued, no mail.
        mockMvc.perform(multipart("/api/auth/register").part(new MockPart[] {
                        part("firstName", "Nueva"),
                        part("email", email),
                        part("password", "OtraClave123!"),
                        part("confirmPassword", "OtraClave123!"),
                        part("acceptTerms", "true"),
                }))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Set-Cookie"));

        assertThat(mailsFor(email)).hasSize(2); // Q2: no third mail while the term is in force
        String name = jdbcTemplate.queryForObject(
                "SELECT first_name FROM users WHERE email = ?", String.class, email);
        assertThat(name).isEqualTo("Nueva"); // FR-015: the pending data was replaced

        // FR-009/FR-015: the token of the resend was invalidated and a fresh one is current.
        Integer resentUsed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_verification_tokens WHERE token_hash = ? AND used_at IS NOT NULL",
                Integer.class, sha256Hex(resentToken));
        assertThat(resentUsed).isEqualTo(1);
        String current = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM email_verification_tokens evt JOIN users u ON u.id = evt.user_id "
                        + "WHERE u.email = ? AND evt.used_at IS NULL",
                String.class, email);
        assertThat(current).isNotEqualTo(sha256Hex(resentToken));
    }

    /**
     * Q3 (FR-011, task T044): the mail of the FIRST registration always goes out
     * immediately, even when the address already carries an active resend term.
     */
    @Test
    void firstRegistrationSendsEvenWhenTheAddressHasAnActiveResendLimit() throws Exception {
        String email = "q3@ejemplo.com";

        // A resend click for an address with no account starts the term and sends nothing.
        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());
        assertThat(mailsFor(email)).isEmpty();

        mockMvc.perform(multipart("/api/auth/register").part(mandatoryFields(email)))
                .andExpect(status().isCreated());

        // FR-011/Q3: the first registration ignores the prior term and sends at once.
        assertThat(extractClearToken(awaitMailsFor(email, 1).get(0))).isNotBlank();
    }
}
