package com.amano.backend.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
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

import jakarta.mail.Address;
import jakarta.mail.internet.MimeMessage;

/**
 * Contract tests of {@code POST /api/auth/resend-verification} with a real
 * PostgreSQL and an in-process SMTP (task T043, TDD: written BEFORE the
 * implementation). GreenMail ONLY — never real Gmail (research.md D9). Covers
 * FR-011/SC-004 (one resend per 5 minutes per address), FR-012/FR-013/SC-005 (the
 * response is identical in every case, only {@code remainingSeconds} varies and
 * the active term never sends nor extends itself), FR-009 (the new token
 * invalidates the previous one) and SC-006 (the remaining time survives a
 * reload because the server is the source of truth).
 */
class EmailResendIT extends AbstractIntegrationIT {

    private static final String NEUTRAL_MESSAGE =
            "Si el correo existe y no ha sido verificado, se enviará un nuevo enlace de confirmación";

    private static final long COOLDOWN_SECONDS = 300;

    private static final Pattern TOKEN_IN_LINK = Pattern.compile("token=([A-Za-z0-9_-]+)");

    private static final Pattern REMAINING_SECONDS = Pattern.compile("\"remainingSeconds\"\\s*:\\s*(\\d+)");

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

    private void register(String email) throws Exception {
        mockMvc.perform(multipart("/api/auth/register").part(mandatoryFields(email)))
                .andExpect(status().isCreated());
    }

    /** Clicks the resend button and asserts the always-200 neutral contract (FR-012). */
    private MvcResult clickResend(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(NEUTRAL_MESSAGE))
                .andExpect(jsonPath("$.remainingSeconds").isNumber())
                .andReturn();
    }

    private static long remainingSecondsOf(MvcResult result) throws Exception {
        Matcher matcher = REMAINING_SECONDS.matcher(result.getResponse().getContentAsString());
        assertThat(matcher.find()).as("the body must carry the remaining seconds").isTrue();
        return Long.parseLong(matcher.group(1));
    }

    /** Waits (up to 5 s) for at least {@code expected} messages addressed to the recipient. */
    private List<MimeMessage> awaitMailsFor(String recipient, int expected) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        List<MimeMessage> received = List.of();
        while (System.currentTimeMillis() < deadline) {
            received = mailsFor(recipient);
            if (received.size() >= expected) {
                return received;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("expected at least " + expected + " mail(s) for " + recipient
                + " but got " + received.size());
    }

    private List<MimeMessage> mailsFor(String recipient) throws Exception {
        List<MimeMessage> received = new ArrayList<>();
        for (MimeMessage message : greenMail.getReceivedMessages()) {
            for (Address address : message.getAllRecipients()) {
                if (recipient.equals(address.toString())) {
                    received.add(message);
                }
            }
        }
        return received;
    }

    private static String extractClearToken(MimeMessage message) throws Exception {
        Matcher matcher = TOKEN_IN_LINK.matcher(decodedBody(message));
        assertThat(matcher.find())
                .withFailMessage("the mail body must carry the verification link with a token")
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

    /** Independent SHA-256 (D2/D4): the test never reuses production hashing. */
    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    /** FR-009/FR-011: a fresh click sends a new link and the new token voids the previous one. */
    @Test
    void resendSendsANewTokenThatInvalidatesThePreviousOne() throws Exception {
        String email = "reenvio@ejemplo.com";
        register(email);
        String firstToken = extractClearToken(awaitMailsFor(email, 1).get(0));

        MvcResult click = clickResend("  Reenvio@Ejemplo.COM "); // normalized like registration
        assertThat(remainingSecondsOf(click)).isBetween(1L, COOLDOWN_SECONDS);

        String secondToken = extractClearToken(awaitMailsFor(email, 2).get(1));
        assertThat(secondToken).isNotEqualTo(firstToken);

        Integer firstUsed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_verification_tokens WHERE token_hash = ? AND used_at IS NOT NULL",
                Integer.class, sha256Hex(firstToken));
        assertThat(firstUsed).isEqualTo(1); // FR-009
        Integer secondCurrent = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_verification_tokens WHERE token_hash = ? AND used_at IS NULL",
                Integer.class, sha256Hex(secondToken));
        assertThat(secondCurrent).isEqualTo(1);
    }

    /** FR-013/SC-004: a second click inside the term answers the same and sends nothing. */
    @Test
    void secondClickWithinTheTermSendsNothingAndKeepsTheResponse() throws Exception {
        String email = "limite@ejemplo.com";
        register(email);
        awaitMailsFor(email, 1);

        // Two immediate clicks: the second one happens well inside the term, so both
        // answers must be byte-for-byte identical (SC-005) and only the first sends.
        MvcResult first = clickResend(email);
        MvcResult second = clickResend(email);

        assertThat(second.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString()); // SC-005: identical shape

        awaitMailsFor(email, 2); // the mail of the first click arrived
        // Sustained negative observation plus a deterministic DB invariant: a
        // wrong second resend would create a third token, even if its mail were slow.
        assertNoNewMessages(2, Duration.ofSeconds(1));
        assertThat(mailsFor(email)).hasSize(2); // FR-013: no new mail
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM email_verification_tokens t
                JOIN users u ON u.id = t.user_id
                WHERE u.email = ?
                """, Integer.class, email)).isEqualTo(2);
    }

    /** FR-012/SC-005: an unknown and a verified address answer identically and send nothing. */
    @Test
    void unknownAndVerifiedAddressesAnswerIdenticallyWithoutSending() throws Exception {
        String verified = "ya.verificada@ejemplo.com";
        register(verified);
        awaitMailsFor(verified, 1);
        jdbcTemplate.update("UPDATE users SET email_verified_at = CURRENT_TIMESTAMP WHERE email = ?", verified);

        MvcResult unknown = clickResend("no.existe@ejemplo.com");
        MvcResult verifiedClick = clickResend(verified);

        assertThat(unknown.getResponse().getContentAsString())
                .isEqualTo(verifiedClick.getResponse().getContentAsString()); // SC-005
        assertThat(mailsFor("no.existe@ejemplo.com")).isEmpty();
        assertThat(mailsFor(verified)).hasSize(1); // only the registration mail (FR-012)
    }

    /** SC-006: after a reload the server returns the real remaining time, which decreases. */
    @Test
    void countdownReflectsTheRealRemainingTimeAfterAReload() throws Exception {
        String email = "recarga@ejemplo.com";
        register(email);
        awaitMailsFor(email, 1);

        long first = remainingSecondsOf(clickResend(email));
        awaitMailsFor(email, 2);

        // Simulate time passing while the screen was reloaded: the stored click is
        // 100 s old, so the term must have ~200 s left (SC-006, source of truth = server).
        jdbcTemplate.update(
                "UPDATE email_resend_limits SET last_resend_at = now() - interval '100 seconds' "
                        + "WHERE email_hash = ?",
                sha256Hex(email));

        long second = remainingSecondsOf(clickResend(email));

        assertThat(second).isBetween(198L, 201L);
        assertThat(second).isLessThan(first);
        assertThat(mailsFor(email)).hasSize(2); // the active term sends nothing
    }
}
