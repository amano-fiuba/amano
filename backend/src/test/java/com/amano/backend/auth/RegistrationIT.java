package com.amano.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
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
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import jakarta.servlet.http.Cookie;

/**
 * Contract integration tests for POST /api/auth/register (task T016), written
 * BEFORE the implementation per TDD/US1. Replicates contracts/auth-api.md with a
 * real PostgreSQL (Testcontainers): FR-001, FR-002, FR-004, FR-005, FR-006, FR-012,
 * FR-014, FR-016, FR-018, FR-021, FR-022, SC-001, SC-002, SC-004.
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

    /** Happy path (FR-001, FR-002, FR-004, FR-014, FR-016, FR-021, SC-001, SC-002). */
    @Test
    void registrationHappyPathCreatesAccountAndLogsIn() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/auth/register")
                        .part(mandatoryFields("Ana.Perez@Example.COM ")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/users/me"))
                .andExpect(jsonPath("$.firstName").value("Ana"))               // Q4: stored trimmed
                .andExpect(jsonPath("$.lastName").value("Pérez"))
                .andExpect(jsonPath("$.email").value("ana.perez@example.com")) // FR-004 normalized
                .andExpect(jsonPath("$.currencies").value(empty()))            // FR-011/Q1: 0 filas → ARS at display
                .andExpect(jsonPath("$.hasCustomAvatar").value(false))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())          // FR-007: never exposed
                .andReturn();

        // Session contract (Set-Cookie table): HttpOnly, SameSite=Strict, Max-Age=86400.
        Cookie sessionCookie = result.getResponse().getCookie("amano_auth");
        assertThat(sessionCookie).isNotNull();
        assertThat(sessionCookie.getMaxAge()).isEqualTo(86400);
        assertThat(sessionCookie.isHttpOnly()).isTrue();
        assertThat(result.getResponse().getHeader("Set-Cookie"))
                .contains("SameSite=Strict")
                .doesNotContain("Secure"); // local HTTP deployment (compose / Vite proxy)

        // FR-016: the session is usable right away with the issued cookie (FR-021, SC-002).
        mockMvc.perform(get("/api/users/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ana"))
                .andExpect(jsonPath("$.email").value("ana.perez@example.com"))
                .andExpect(jsonPath("$.currencies").value(empty()))
                .andExpect(jsonPath("$.cbuCvu").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    /** Duplicate email, another casing/spacing (SC-004): 409 with the exact message. */
    @Test
    void duplicateEmailReturnsConflictWithExactMessage() throws Exception {
        mockMvc.perform(multipart("/api/auth/register")
                        .part(mandatoryFields("Duplicada.Example@Example.COM")))
                .andExpect(status().isCreated())
                .andReturn();

        mockMvc.perform(multipart("/api/auth/register")
                        .part(mandatoryFields("  DUPLICADA.EXAMPLE@EXAMPLE.COM ")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflicto"))
                .andExpect(jsonPath("$.detail").value("El correo electrónico ya se encuentra registrado"))
                .andExpect(jsonPath("$.properties.fields.email")
                        .value("El correo electrónico ya se encuentra registrado"));
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
