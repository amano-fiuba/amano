package com.amano.backend.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Contract integration tests for POST /api/auth/register after feature 002
 * (task T010, TDD: written BEFORE the implementation). Replicates the "Cambio
 * sobre contrato de 001" of contracts/email-verification-api.md with a real
 * PostgreSQL (Testcontainers): FR-001 (account created unverified, no session,
 * no Location), FR-004, FR-012, FR-014, FR-015 (duplicate over PENDING is
 * replaced, not rejected), FR-016 (409 only for a VERIFIED duplicate),
 * FR-018, FR-021, FR-022, SC-002. The GreenMail delivery is covered by
 * RegistrationVerificationIT (task T016).
 */
@SpringBootTest(properties = {
        // SecurityConfig (T011) requires a valid JWT_SECRET to bootstrap JwtService.
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class RegistrationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static MockPart part(String name, String value) {
        return new MockPart(name, value.getBytes(StandardCharsets.UTF_8));
    }

    private static MockPart[] mandatoryFields(String email) {
        return new MockPart[] {
                part("firstName", "  Ana  "),
                part("lastName", "Pérez"),
                part("email", email),
                part("password", "Password123!"),
                part("confirmPassword", "Password123!"),
                part("acceptTerms", "true"),
        };
    }

    /** Marks the account verified straight in the database (the token redemption endpoint lands in US2). */
    private void markVerified(String normalizedEmail) {
        jdbcTemplate.update(
                "UPDATE users SET email_verified_at = CURRENT_TIMESTAMP WHERE email = ?",
                normalizedEmail);
    }

    /** 201 contract (FR-001, SC-001, SC-002): unverified account, NO session, only {email}. */
    @Test
    void registrationCreatesUnverifiedAccountWithoutSession() throws Exception {
        mockMvc.perform(multipart("/api/auth/register")
                        .part(mandatoryFields("Ana.Perez@Example.COM ")))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Set-Cookie"))     // no auto-login (FR-001, replaces FR-016 of 001)
                .andExpect(header().doesNotExist("Location"))       // there is no session resource to point at
                .andExpect(jsonPath("$.email").value("ana.perez@example.com")) // FR-004 normalized
                .andExpect(jsonPath("$.id").doesNotExist())         // the profile is NOT exposed here anymore
                .andExpect(jsonPath("$.firstName").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist()); // FR-007: never exposed

        // The account exists and is blocked (email_verified_at NULL, FR-001/FR-004).
        Integer unverified = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ? AND email_verified_at IS NULL",
                Integer.class, "ana.perez@example.com");
        org.assertj.core.api.Assertions.assertThat(unverified).isEqualTo(1);

        // No cookie was issued: protected resources stay closed (SC-002).
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("No autorizado"));
    }

    /** Duplicate email over a VERIFIED account, another casing/spacing (FR-016, SC-004): 409 with the exact message. */
    @Test
    void duplicateOverVerifiedEmailReturnsConflictWithExactMessage() throws Exception {
        mockMvc.perform(multipart("/api/auth/register")
                        .part(mandatoryFields("Duplicada.Example@Example.COM")))
                .andExpect(status().isCreated());
        markVerified("duplicada.example@example.com");

        mockMvc.perform(multipart("/api/auth/register")
                        .part(mandatoryFields("  DUPLICADA.EXAMPLE@EXAMPLE.COM ")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflicto"))
                .andExpect(jsonPath("$.detail").value("El correo electrónico ya se encuentra registrado"))
                .andExpect(jsonPath("$.properties.fields.email")
                        .value("El correo electrónico ya se encuentra registrado"));
    }

    /** Duplicate email over a PENDING account (FR-015): replaced, never a second account. */
    @Test
    void duplicateOverPendingEmailIsReplacedNotRejected() throws Exception {
        mockMvc.perform(multipart("/api/auth/register")
                        .part(mandatoryFields("Pendiente.Example@Example.COM")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("pendiente.example@example.com"));

        // Same address, different casing and another password: still 201, still one row.
        mockMvc.perform(multipart("/api/auth/register")
                        .part(new MockPart[] {
                                part("firstName", "Otro"),
                                part("lastName", "Nombre"),
                                part("email", "  PENDIENTE.EXAMPLE@EXAMPLE.COM "),
                                part("password", "OtraClave123!"),
                                part("confirmPassword", "OtraClave123!"),
                                part("acceptTerms", "true"),
                        }))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.email").value("pendiente.example@example.com"));

        Integer accounts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?", Integer.class,
                "pendiente.example@example.com");
        org.assertj.core.api.Assertions.assertThat(accounts).isEqualTo(1);
    }

    /** Field-level validation failures (FR-018): 400 ProblemDetail with properties.fields per field. */
    @Test
    void invalidMultipartFieldsReturn400WithPerFieldMessages() throws Exception {
        mockMvc.perform(multipart("/api/auth/register")
                        .part(
                                part("firstName", "A"),
                                part("email", "no-es-un-email"),
                                part("password", "debil"),
                                part("confirmPassword", "debil"),
                                part("acceptTerms", "false")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validación fallida"))
                .andExpect(jsonPath("$.properties.fields.firstName")
                        .value("El nombre debe tener entre 2 y 50 caracteres"))
                .andExpect(jsonPath("$.properties.fields.email")
                        .value("Ingresa un correo electrónico válido"))
                .andExpect(jsonPath("$.properties.fields.password")
                        .value("La contraseña debe tener al menos 8 caracteres"))
                .andExpect(jsonPath("$.properties.fields.acceptTerms")
                        .value("Debes aceptar los Términos y Condiciones para registrarte"));
    }

    /** Missing session on the authenticated endpoint (users-api contract): 401 ProblemDetail. */
    @Test
    void meWithoutSessionReturns401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("No autorizado"));
    }
}
