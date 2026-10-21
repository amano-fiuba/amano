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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;

import jakarta.mail.Address;
import jakarta.mail.internet.MimeMessage;

/**
 * Contract tests of {@code POST /api/auth/reset-password} with a real PostgreSQL
 * and an in-process SMTP (feature 006, US2). GreenMail ONLY — never real Gmail
 * (FR-009). The token is always obtained the way a user would: requesting
 * {@code /forgot-password} and reading the mailed link. Covers FR-010..FR-015 end
 * to end, including the login with the new password.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class ResetPasswordIT {

    private static final String OLD_PASSWORD = "Password123!";
    private static final String NEW_PASSWORD = "NuevaClave456?";

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
                        part("password", OLD_PASSWORD),
                        part("confirmPassword", OLD_PASSWORD),
                        part("acceptTerms", "true")))
                .andExpect(status().isCreated());
        jdbcTemplate.update("UPDATE users SET email_verified_at = CURRENT_TIMESTAMP WHERE email = ?", email);
    }

    /** Requests the link and returns the clear token of the {@code n}-th reset mail (1-based). */
    private String requestToken(String email, int n) throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());
        return extractClearToken(awaitResetMailsFor(email, n).get(n - 1));
    }

    private ResultActions reset(String token, String password, String confirmPassword) throws Exception {
        return mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"password\":\"" + password
                        + "\",\"confirmPassword\":\"" + confirmPassword + "\"}"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private int resetTokensOf(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM password_reset_tokens t JOIN users u ON u.id = t.user_id WHERE u.email = ?",
                Integer.class, email);
    }

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

    /** FR-015 + US2 scenarios 1-3: new password works, old one fails, the link dies. */
    @Test
    void validLinkChangesThePasswordAndCannotBeReused() throws Exception {
        String email = "cambio@ejemplo.com";
        registerVerified(email);
        String token = requestToken(email, 1);

        reset(token, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tu contraseña fue actualizada. Ya puedes iniciar sesión"));

        assertThat(resetTokensOf(email)).isZero(); // FR-015: tokens deleted
        String storedHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?", String.class, email);
        assertThat(storedHash).startsWith("$2").doesNotContain(NEW_PASSWORD); // BCrypt

        login(email, NEW_PASSWORD).andExpect(status().isOk());
        login(email, OLD_PASSWORD).andExpect(status().isUnauthorized());

        // FR-012: the very same link is now invalid.
        reset(token, "OtraClave789!", "OtraClave789!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El enlace para restablecer la contraseña no es válido"));
        login(email, NEW_PASSWORD).andExpect(status().isOk());
    }

    /** FR-007/FR-012/FR-015: a superseded link is invalid; the newest works and clears both. */
    @Test
    void supersededLinkIsInvalidAndTheNewestWorks() throws Exception {
        String email = "reemplazo@ejemplo.com";
        registerVerified(email);
        String first = requestToken(email, 1);
        String second = requestToken(email, 2);

        reset(first, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isBadRequest());
        reset(second, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isOk());

        assertThat(resetTokensOf(email)).isZero(); // the superseded row goes too
    }

    /** FR-013: an expired link answers 410, is removed and the password does not change. */
    @Test
    void expiredLinkAnswers410AndIsRemoved() throws Exception {
        String email = "vencido@ejemplo.com";
        registerVerified(email);
        String token = requestToken(email, 1);
        // Move the whole window to the past (keeps CHECK expires_at > created_at).
        jdbcTemplate.update("UPDATE password_reset_tokens SET created_at = now() - interval '2 hours', "
                + "expires_at = now() - interval '1 hour' WHERE token_hash = ?", sha256Hex(token));

        reset(token, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.detail").value("El enlace para restablecer la contraseña ha expirado"));

        assertThat(resetTokensOf(email)).isZero();
        login(email, OLD_PASSWORD).andExpect(status().isOk());
    }

    /** FR-014: invalid fields answer 400 per field and the link survives for a retry. */
    @Test
    void invalidFieldsAnswer400AndTheLinkSurvives() throws Exception {
        String email = "reintento@ejemplo.com";
        registerVerified(email);
        String token = requestToken(email, 1);

        reset(token, "corta", "corta")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.password")
                        .value("La contraseña debe tener al menos 8 caracteres"));
        reset(token, NEW_PASSWORD, "NoCoincide123!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.confirmPassword").value("Las contraseñas no coinciden"));

        assertThat(resetTokensOf(email)).isEqualTo(1);
        reset(token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isOk());
    }

    /** FR-010/FR-012: public endpoint; an invented token or an empty body is a neutral 400. */
    @Test
    void inventedTokenAndEmptyBodyAreInvalid() throws Exception {
        reset("token-inventado", NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El enlace para restablecer la contraseña no es válido"));
        mockMvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Enlace no válido"));
    }
}
