package com.amano.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import jakarta.servlet.http.Cookie;

/**
 * Contract tests of the real login (feature 004), end to end against PostgreSQL.
 * Replaces SimulatedLoginIT of feature 003 and keeps every rule it proved.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class LoginIT {

    private static final String BAD_CREDENTIALS = "El correo electrónico o la contraseña son incorrectos";

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

    /** Creates the PENDING account through the real registration endpoint. */
    private void register(String email) throws Exception {
        mockMvc.perform(multipart("/api/auth/register").part(
                        part("firstName", "Ana"),
                        part("email", email),
                        part("password", "Password123!"),
                        part("confirmPassword", "Password123!"),
                        part("acceptTerms", "true")))
                .andExpect(status().isCreated());
    }

    private void markVerified(String normalizedEmail) {
        jdbcTemplate.update(
                "UPDATE users SET email_verified_at = CURRENT_TIMESTAMP WHERE email = ?",
                normalizedEmail);
    }

    private static String loginBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void verifiedCredentialsOpenTheSessionWithCookieAndProfile() throws Exception {
        String email = "login.verificada@example.com";
        register(email);
        markVerified(email);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("  Login.Verificada@Example.COM ", "Password123!")))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("amano_auth=")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.firstName").value("Ana"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        // The issued cookie is a real session: it opens the protected scope.
        Cookie session = result.getResponse().getCookie("amano_auth");
        assertThat(session).isNotNull();
        mockMvc.perform(get("/api/users/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void unverifiedCredentialsAreForbiddenWithTheResendIndicator() throws Exception {
        String email = "login.pendiente@example.com";
        register(email);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "Password123!")))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.title").value("Cuenta no verificada"))
                .andExpect(jsonPath("$.detail").value(
                        "Tu cuenta aún no ha sido verificada. Por favor, revisa tu correo electrónico"))
                .andExpect(jsonPath("$.properties.action").value("RESEND_VERIFICATION"));
    }

    @Test
    void unverifiedAccountWithWrongPasswordDoesNotRevealItsState() throws Exception {
        String email = "login.pendiente.mal@example.com";
        register(email);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "OtraClave123!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(BAD_CREDENTIALS));
    }

    @Test
    void wrongPasswordIsUnauthorizedWithoutCookie() throws Exception {
        String email = "login.clave@example.com";
        register(email);
        markVerified(email);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "OtraClave123!")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.title").value("No autorizado"))
                .andExpect(jsonPath("$.detail").value(BAD_CREDENTIALS));
    }

    @Test
    void unknownAccountIsUnauthorizedWithTheSameShape() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("no.existe@example.com", "Password123!")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.title").value("No autorizado"))
                .andExpect(jsonPath("$.detail").value(BAD_CREDENTIALS));
    }

    @Test
    void missingFieldsAreA400WithFieldMessages() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("   ", "")))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.properties.fields.email").value("El correo electrónico es obligatorio"))
                .andExpect(jsonPath("$.properties.fields.password").value("La contraseña es obligatoria"));
    }
}
