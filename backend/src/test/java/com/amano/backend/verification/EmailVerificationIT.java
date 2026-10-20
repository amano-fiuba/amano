package com.amano.backend.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;

import com.amano.backend.config.JwtService;

import jakarta.mail.Address;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;

/**
 * Contract tests of {@code POST /api/auth/verify-email} with a real PostgreSQL
 * and an in-process SMTP (task T028, TDD: written BEFORE the implementation).
 * GreenMail ONLY — never real Gmail (research.md D9, team rule). Covers the
 * contract of contracts/email-verification-api.md: 200 with the session cookie
 * and the profile (the link is the session bearer, Q1), second redemption 409,
 * tampered token 400 and expired token 410 with the exact Spanish messages,
 * plus SC-003 (no reuse, no use after 24 h).
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class EmailVerificationIT {

    private static final Pattern TOKEN_IN_LINK = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    /** In-process SMTP on a random port; lifecycle by CLASS so the port is ready at context load. */
    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(
            new ServerSetup(0, "localhost", ServerSetup.PROTOCOL_SMTP))
            .withPerMethodLifecycle(false);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> greenMail.getSmtp().getPort());
        // GreenMail speaks plain SMTP: the STARTTLS/AUTH required by the real
        // Gmail (application.properties) is turned off here (research.md D9).
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

    /** Registers the account and returns the clear token delivered by GreenMail (US1 + US2 seam). */
    private String registerAndToken(String email) throws Exception {
        mockMvc.perform(multipart("/api/auth/register").part(mandatoryFields(email)))
                .andExpect(status().isCreated());

        long deadline = System.currentTimeMillis() + 5_000;
        List<MimeMessage> received = List.of();
        while (System.currentTimeMillis() < deadline && received.isEmpty()) {
            received = mailsFor(email);
            if (received.isEmpty()) {
                Thread.sleep(50);
            }
        }
        assertThat(received).as("verification mail for %s", email).isNotEmpty();

        Matcher matcher = TOKEN_IN_LINK.matcher(decodedBody(received.get(0)));
        assertThat(matcher.find()).as("the mail must carry the verification link").isTrue();
        return matcher.group(1);
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

    private static String redeemBody(String token) {
        return "{\"token\":\"" + token + "\"}";
    }

    /** FR-005, Q1, SC-008: a valid token verifies, opens the session and returns the profile. */
    @Test
    void validTokenVerifiesAccountIssuesSessionAndReturnsProfile() throws Exception {
        String email = "verifica@ejemplo.com";
        String token = registerAndToken(email);

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemBody(token)))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("amano_auth=")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.firstName").value("Ana"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        Integer verified = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ? AND email_verified_at IS NOT NULL",
                Integer.class, email);
        assertThat(verified).isEqualTo(1);
        Integer used = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_verification_tokens evt JOIN users u ON u.id = evt.user_id "
                        + "WHERE u.email = ? AND evt.used_at IS NOT NULL",
                Integer.class, email);
        assertThat(used).isEqualTo(1);
    }

    /**
     * The link is the session bearer (Q1): opening it from a device that never
     * registered nor held a cookie still verifies and signs that device in.
     */
    @Test
    void linkWorksFromADeviceWithoutAnyPreviousSession() throws Exception {
        String email = "otro-dispositivo@ejemplo.com";
        String token = registerAndToken(email);

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemBody(token)))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("amano_auth=")))
                .andExpect(jsonPath("$.email").value(email));
    }

    /** FR-007/SC-003: redeeming the same token twice is a 409 without a session. */
    @Test
    void secondRedemptionIsConflictWithoutCookie() throws Exception {
        String email = "reutilizado@ejemplo.com";
        String token = registerAndToken(email);

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemBody(token)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemBody(token)))
                .andExpect(status().isConflict())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.title").value("Enlace ya utilizado"))
                .andExpect(jsonPath("$.detail").value(
                        "Este enlace de verificación ya ha sido utilizado. Por favor, inicia sesión"));
    }

    /** FR-008/SC-003: a tampered token is a 400 with the exact message. */
    @Test
    void tamperedTokenIsBadRequest() throws Exception {
        String token = registerAndToken("alterado@ejemplo.com");

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemBody(token + "x")))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.title").value("Enlace no válido"))
                .andExpect(jsonPath("$.detail").value("El enlace de verificación no es válido"));
    }

    /** FR-006/FR-021/SC-003: past the 24 h window the redemption is a 410 and records the use. */
    @Test
    void expiredTokenIsGoneAndForfeitsItsUse() throws Exception {
        String email = "vencido@ejemplo.com";
        String token = registerAndToken(email);

        // Shorten the deadline in the database (ITs override deadlines, research.md
        // section 4). Both timestamps move back so the CHECK expires_at > created_at
        // keeps holding: the token is one hour past its window.
        jdbcTemplate.update(
                "UPDATE email_verification_tokens SET created_at = now() - interval '25 hours', "
                        + "expires_at = now() - interval '1 hour' "
                        + "WHERE user_id = (SELECT id FROM users WHERE email = ?)",
                email);

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemBody(token)))
                .andExpect(status().isGone())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.title").value("Enlace expirado"))
                .andExpect(jsonPath("$.detail").value("El enlace de verificación ha expirado"));

        Integer unverified = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ? AND email_verified_at IS NULL",
                Integer.class, email);
        assertThat(unverified).isEqualTo(1);
        Integer used = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_verification_tokens evt JOIN users u ON u.id = evt.user_id "
                        + "WHERE u.email = ? AND evt.used_at IS NOT NULL",
                Integer.class, email);
        assertThat(used).isEqualTo(1);
    }

    private long userIdOf(String normalizedEmail) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class,
                normalizedEmail);
    }

    /**
     * FR-004/SC-002 (task T037): a cookie forged for a PENDING account (the
     * resulting session of a bygone rule, or a fabricated one) is no session for
     * {@code /api/users/**}: the 401 ProblemDetail of 001 comes back.
     */
    @Test
    void unverifiedAccountCookieIsRejectedOnProtectedResources() throws Exception {
        String email = "sin-verificar@ejemplo.com";
        mockMvc.perform(multipart("/api/auth/register").part(mandatoryFields(email)))
                .andExpect(status().isCreated());

        Cookie forgedSession = new Cookie("amano_auth", jwtService.issueToken(userIdOf(email)));

        mockMvc.perform(get("/api/users/me").cookie(forgedSession))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("No autorizado"));
    }

    /** FR-004/SC-002 (task T037): the same cookie is a session once the account is verified. */
    @Test
    void verifiedAccountCookieReachesProtectedResources() throws Exception {
        String email = "ya-verificada@ejemplo.com";
        String token = registerAndToken(email);
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemBody(token)))
                .andExpect(status().isOk());

        Cookie session = new Cookie("amano_auth", jwtService.issueToken(userIdOf(email)));

        mockMvc.perform(get("/api/users/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }
}
