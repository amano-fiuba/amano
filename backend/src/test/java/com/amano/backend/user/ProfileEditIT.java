package com.amano.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;


import com.amano.backend.config.JwtService;
import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;

/**
 * Feature 005 contract integration tests (T002–T004): replicates
 * specs/005-edit-user-profile/contracts/users-api.md with a real PostgreSQL.
 * All mutation endpoints operate EXCLUSIVELY on the session principal
 * (FR-017); the session cookie is the only identity ever consulted, so the
 * tests verify the account through {@link #verifiedSessionOf(String)} like
 * RegistrationProfileIT.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class ProfileEditIT {

    /** Contract key carrying the per-field messages map (ProblemDetailExceptionHandler). */
    private static final String FIELDS = "fields";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private static final byte[] JPEG_BYTES = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10,
            0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x02, 0x01, 0x00, 0x00 };

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52 };

    /** Declared image/jpeg but real GIF magic bytes (lying MIME, D6). */
    private static final byte[] GIF_BYTES_LYING_AS_JPEG = {
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00 };

    private long userIdOf(String normalizedEmail) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class,
                normalizedEmail);
    }

    /** Registration of feature 003 → account is verified directly in the DB. */
    private Cookie verifiedSessionOf(String normalizedEmail) {
        jdbcTemplate.update(
                "UPDATE users SET email_verified_at = CURRENT_TIMESTAMP WHERE email = ?",
                normalizedEmail);
        return new Cookie("amano_auth", jwtService.issueToken(userIdOf(normalizedEmail)));
    }

    private void registerAccount(String rawEmail, String password, String firstName,
            String lastName) throws Exception {
        org.springframework.mock.web.MockPart firstNamePart = new org.springframework.mock.web.MockPart(
                "firstName", firstName.getBytes(StandardCharsets.UTF_8));
        org.springframework.mock.web.MockPart lastNamePart = lastName == null ? null :
                new org.springframework.mock.web.MockPart("lastName",
                        lastName.getBytes(StandardCharsets.UTF_8));
        org.springframework.mock.web.MockPart emailPart = new org.springframework.mock.web.MockPart(
                "email", rawEmail.getBytes(StandardCharsets.UTF_8));
        org.springframework.mock.web.MockPart passwordPart = new org.springframework.mock.web.MockPart(
                "password", password.getBytes(StandardCharsets.UTF_8));
        org.springframework.mock.web.MockPart confirmPart = new org.springframework.mock.web.MockPart(
                "confirmPassword", password.getBytes(StandardCharsets.UTF_8));
        org.springframework.mock.web.MockPart termsPart = new org.springframework.mock.web.MockPart(
                "acceptTerms", "true".getBytes(StandardCharsets.UTF_8));

        var builder = multipart("/api/auth/register")
                .part(firstNamePart, emailPart, passwordPart, confirmPart, termsPart);
        if (lastNamePart != null) {
            builder.part(lastNamePart);
        }
        mockMvc.perform(builder).andExpect(status().isCreated());
    }

    @Test
    void putMeUpdatesEditableFieldsAndNormalizesLikeRegistration() throws Exception {
        String email = "ana.edit1@example.com";
        registerAccount(email, "S3gura!2026", "Ana", "Pérez");
        Cookie session = verifiedSessionOf(email);

        mockMvc.perform(put("/api/users/me").cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "  Anabel  ",
                                "lastName", "Gómez",
                                "currencies", java.util.List.of("USD", "ARS"),
                                "cbuCvu", "285-05910-0123 4567890123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Anabel")) // trim persists (Q4)
                .andExpect(jsonPath("$.lastName").value("Gómez"))
                .andExpect(jsonPath("$.currencies.length()").value(2))
                .andExpect(jsonPath("$.cbuCvu").value("2850591001234567890123")) // Q3
                .andExpect(jsonPath("$.email").value(email));
    }

    /** FR-007/SC-003: the email NEVER changes, even when the client sends it. */
    @Test
    void putMeIgnoresAnyEmailSentByTheClient() throws Exception {
        String email = "ana.edit2@example.com";
        registerAccount(email, "S3gura!2026", "Ana", null);
        Cookie session = verifiedSessionOf(email);

        mockMvc.perform(put("/api/users/me").cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Anabel",
                                "email", "hackeada@example.com"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    /** FR-004: cleaning the optional fields persists NULLs / empty currencies. */
    @Test
    void putMeClearingOptionalsPersistsNullAndEmptyCurrencies() throws Exception {
        String email = "ana.edit3@example.com";
        registerAccount(email, "S3gura!2026", "Ana", "Pérez");
        jdbcTemplate.update("UPDATE users SET cbu = '2850591001234567890123' WHERE email = ?", email);
        jdbcTemplate.update(
                "INSERT INTO user_currencies (user_id, currency_code) VALUES (?, 'EUR')",
                userIdOf(email));
        Cookie session = verifiedSessionOf(email);

        mockMvc.perform(put("/api/users/me").cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Ana",
                                "lastName", "",
                                "currencies", java.util.List.of(),
                                "cbuCvu", " "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").doesNotExist())
                .andExpect(jsonPath("$.cbuCvu").doesNotExist())
                .andExpect(jsonPath("$.currencies").isEmpty());

        Integer currencyRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_currencies WHERE user_id = ?", Integer.class,
                userIdOf(email));
        assertThat(currencyRows).isZero();
        String cbu = jdbcTemplate.queryForObject("SELECT cbu FROM users WHERE email = ?",
                String.class, email);
        assertThat(cbu).isNull();
    }

    /** FR-006/FR-010: server-side validation, per-field ProblemDetail, atomic. */
    @Test
    void putMeValidationReturnsFieldMessagesAndAppliesNoPartialChange() throws Exception {
        String email = "ana.edit4@example.com";
        registerAccount(email, "S3gura!2026", "Ana", null);
        Cookie session = verifiedSessionOf(email);

        mockMvc.perform(put("/api/users/me").cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(objectMapper.createObjectNode()
                                .put("firstName", "A")
                                .put("lastName", "X")
                                .set("currencies", objectMapper.createArrayNode().add("QWE"))
                                .put("cbuCvu", "123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validación fallida"))
                .andExpect(jsonPath("$.properties." + FIELDS + ".firstName")
                        .value("El nombre debe tener entre 2 y 50 caracteres"))
                .andExpect(jsonPath("$.properties." + FIELDS + ".lastName")
                        .value("El apellido debe tener entre 2 y 50 caracteres"))
                .andExpect(jsonPath("$.properties." + FIELDS + ".currencies")
                        .value("Moneda no soportada"))
                .andExpect(jsonPath("$.properties." + FIELDS + ".cbuCvu")
                        .value("El CBU/CVU debe tener 22 dígitos"));

        // Nothing was applied despite the request touching other fields.
        mockMvc.perform(get("/api/users/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ana"))
                .andExpect(jsonPath("$.lastName").doesNotExist());
    }

    /** 027 T014 (FR-014): preferences come from the BCRA catalog, not only ARS/USD/EUR. */
    @Test
    void putMeAcceptsCatalogCurrenciesAndRejectsUnsupportedOnes() throws Exception {
        String email = "ana.edit027@example.com";
        registerAccount(email, "S3gura!2026", "Ana", null);
        Cookie session = verifiedSessionOf(email);

        mockMvc.perform(put("/api/users/me").cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Ana",
                                "currencies", java.util.List.of("ARS", "BRL")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencies.length()").value(2));
        assertThat(jdbcTemplate.queryForList(
                "SELECT currency_code FROM user_currencies WHERE user_id = ?", String.class, userIdOf(email)))
                .containsExactlyInAnyOrder("ARS", "BRL");

        mockMvc.perform(put("/api/users/me").cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Ana",
                                "currencies", java.util.List.of("XAU")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties." + FIELDS + ".currencies").value("Moneda no soportada"));
    }

    /** FR-010: duplicated currency values are rejected (contract table). */
    @Test
    void putMeRejectsDuplicatedCurrencies() throws Exception {
        String email = "ana.edit5@example.com";
        registerAccount(email, "S3gura!2026", "Ana", null);
        Cookie session = verifiedSessionOf(email);

        mockMvc.perform(put("/api/users/me").cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Ana",
                                "currencies", java.util.List.of("ARS", "USD", "USD")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties." + FIELDS + ".currencies")
                        .value("Las monedas no deben repetirse"));

        Integer currencyRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_currencies WHERE user_id = ?", Integer.class,
                userIdOf(email));
        assertThat(currencyRows).isZero();
    }

    @Test
    void putMeWithoutSessionIsUnauthorized() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("firstName", "Ana"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("No autorizado"));
    }

    /** FR-013: correct current credit → new credential persisted, session survives. */
    @Test
    void putPasswordPersistsNewCredentialAndKeepsTheSession() throws Exception {
        String email = "ana.edit6@example.com";
        String oldPassword = "S3gura!2026";
        registerAccount(email, oldPassword, "Ana", null);
        Cookie session = verifiedSessionOf(email);

        mockMvc.perform(put("/api/users/me/password").cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", oldPassword,
                                "newPassword", "N3uva#Clave",
                                "confirmPassword", "N3uva#Clave"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        String hash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?", String.class, email);
        assertThat(passwordEncoder.matches("N3uva#Clave", hash)).isTrue();
        assertThat(passwordEncoder.matches(oldPassword, hash)).isFalse();

        // The session is NOT rotated: the same cookie keeps granting access.
        mockMvc.perform(get("/api/users/me").cookie(session))
                .andExpect(status().isOk());
    }

    /** FR-014: wrong current password → specific 400, credential intact. */
    @Test
    void putPasswordWithWrongCurrentIsRejectedAndCredentialStays() throws Exception {
        String email = "ana.edit7@example.com";
        registerAccount(email, "S3gura!2026", "Ana", null);
        Cookie session = verifiedSessionOf(email);
        String hashBefore = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?", String.class, email);

        mockMvc.perform(put("/api/users/me/password").cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "Incorrecta!123",
                                "newPassword", "N3uva#Clave",
                                "confirmPassword", "N3uva#Clave"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La contraseña actual no es correcta"))
                .andExpect(jsonPath("$.properties." + FIELDS + ".currentPassword")
                        .value("La contraseña actual no es correcta"));

        String hashAfter = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?", String.class, email);
        assertThat(hashAfter).isEqualTo(hashBefore);
    }

    /** FR-013: the new password must satisfy the policy of 001 (FR-006). */
    @Test
    void putPasswordRejectsWeakOrMismatchingNewPast() throws Exception {
        String email = "ana.edit8@example.com";
        registerAccount(email, "S3gura!2026", "Ana", null);
        Cookie session = verifiedSessionOf(email);

        mockMvc.perform(put("/api/users/me/password").cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "S3gura!2026",
                                "newPassword", "corta",
                                "confirmPassword", "corta"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties." + FIELDS + ".newPassword")
                        .value("La contraseña debe tener al menos 8 caracteres"));

        mockMvc.perform(put("/api/users/me/password").cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "S3gura!2026",
                                "newPassword", "N3uva#Clave",
                                "confirmPassword", "Otra#Distinta"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties." + FIELDS + ".confirmPassword")
                        .value("Las contraseñas no coinciden"));
    }

    @Test
    void putPasswordWithoutSessionIsUnauthorized() throws Exception {
        mockMvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "x", "newPassword", "x", "confirmPassword", "x"))))
                .andExpect(status().isUnauthorized());
    }

    /** FR-015: a valid image replaces the own avatar (or creates the first one). */
    @Test
    void putAvatarReplacesTheOwnBinary() throws Exception {
        String email = "ana.edit9@example.com";
        registerAccount(email, "S3gura!2026", "Ana", null);
        Cookie session = verifiedSessionOf(email);
        long userId = userIdOf(email);

        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/users/me/avatar").cookie(session)
                        .file(new MockMultipartFile("avatar", "ana.png", "image/png", PNG_BYTES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasCustomAvatar").value(true))
                .andExpect(jsonPath("$.avatarUrl").value(
                        org.hamcrest.Matchers.matchesPattern(
                                "/api/users/" + userId + "/avatar\\?v=\\d+")));

        mockMvc.perform(get("/api/users/" + userId + "/avatar").cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PNG_BYTES))
                .andExpect(content().contentType("image/png"));

        // Replacing with a JPEG: the new binary becomes the served one.
        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/users/me/avatar").cookie(session)
                        .file(new MockMultipartFile("avatar", "ana.jpg", "image/jpeg", JPEG_BYTES)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/" + userId + "/avatar").cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().bytes(JPEG_BYTES))
                .andExpect(content().contentType("image/jpeg"));
    }

    /** FR-010 of 001 holds for the edit path too: lying MIME → 400, atomic. */
    @Test
    void putAvatarWithLyingMimeIsRejectedAndKeepsTheCurrentBinary() throws Exception {
        String email = "ana.edit10@example.com";
        registerAccount(email, "S3gura!2026", "Ana", null);
        Cookie session = verifiedSessionOf(email);
        long userId = userIdOf(email);

        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/users/me/avatar").cookie(session)
                        .file(new MockMultipartFile("avatar", "ana.jpg", "image/jpeg", JPEG_BYTES)))
                .andExpect(status().isOk());

        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/users/me/avatar").cookie(session)
                        .file(new MockMultipartFile("avatar", "trampa.jpg", "image/jpeg",
                                GIF_BYTES_LYING_AS_JPEG)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties." + FIELDS + ".avatar")
                        .value("El archivo debe ser una imagen JPG o PNG válida"));

        mockMvc.perform(get("/api/users/" + userId + "/avatar").cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().bytes(JPEG_BYTES));
    }

    /** Contract: >2 MB → 413 (same handler as registration, T026). */
    @Test
    void oversizedAvatarIsRejectedAs413() throws Exception {
        String email = "ana.edit11@example.com";
        registerAccount(email, "S3gura!2026", "Ana", null);
        Cookie session = verifiedSessionOf(email);

        byte[] oversized = new byte[2 * 1024 * 1024 + 1];
        System.arraycopy(PNG_BYTES, 0, oversized, 0, PNG_BYTES.length);

        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/users/me/avatar").cookie(session)
                        .file(new MockMultipartFile("avatar", "grande.png", "image/png", oversized)))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.title").value("Payload demasiado grande"));
    }

    /** FR-016: deleting the own avatar returns to the default representation. */
    @Test
    void deleteAvatarRemovesTheRowAndTheReadBecomesDefault() throws Exception {
        String email = "ana.edit12@example.com";
        registerAccount(email, "S3gura!2026", "Ana", null);
        Cookie session = verifiedSessionOf(email);
        long userId = userIdOf(email);

        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/users/me/avatar").cookie(session)
                        .file(new MockMultipartFile("avatar", "ana.jpg", "image/jpeg", JPEG_BYTES)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/users/me/avatar").cookie(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/" + userId + "/avatar").cookie(session))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/users/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasCustomAvatar").value(false))
                .andExpect(jsonPath("$.avatarUrl").doesNotExist());

        // Idempotency: deleting with no row is still 204.
        mockMvc.perform(delete("/api/users/me/avatar").cookie(session))
                .andExpect(status().isNoContent());
    }

    /** Extra guard for US4: the avatar endpoint never serves/writes other users. */
    @Test
    void avatarEndpointNeverServesAnotherUsersBinary() throws Exception {
        String emailA = "ana.edit13@example.com";
        registerAccount(emailA, "S3gura!2026", "Ana", null);
        registerAccount("beto.edit13@example.com", "Beto#clave1", "Beto", null);
        Cookie sessionA = verifiedSessionOf(emailA);
        jdbcTemplate.update(
                "UPDATE users SET email_verified_at = CURRENT_TIMESTAMP WHERE email = ?",
                "beto.edit13@example.com");

        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/users/me/avatar").cookie(sessionA)
                        .file(new MockMultipartFile("avatar", "ana.jpg", "image/jpeg", JPEG_BYTES)))
                .andExpect(status().isOk());
        long idB = userIdOf("beto.edit13@example.com");

        // A requests B's avatar binary: 404 (feature 005 keeps the 001 rule).
        mockMvc.perform(get("/api/users/" + idB + "/avatar").cookie(sessionA))
                .andExpect(status().isNotFound());
    }
}
