package com.amano.backend.verification;

import static org.hamcrest.Matchers.containsString;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Contract tests of the PROVISIONAL login stub (task T038, TDD: written BEFORE
 * the implementation; contracts/email-verification-api.md, D7). It exists only
 * to prove FR-014 server-side: valid credentials on a VERIFIED account open the
 * session (001 cookie + profile), valid credentials on a PENDING account are a
 * 403 with the exact message and the machine-readable resend indicator, and bad
 * credentials (wrong password or unknown account) are the same 401 so the
 * existence of the account is never disclosed. The real login feature will
 * replace {@code SimulatedLoginController}; the UI of login is out of scope.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class SimulatedLoginIT {

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
                part("firstName", "Ana"),
                part("email", email),
                part("password", "Password123!"),
                part("confirmPassword", "Password123!"),
                part("acceptTerms", "true"),
        };
    }

    /** Creates the PENDING account through the real registration endpoint. */
    private void register(String email) throws Exception {
        mockMvc.perform(multipart("/api/auth/register").part(mandatoryFields(email)))
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

    /** FR-014: verified credentials start the session with the 001 cookie and the profile. */
    @Test
    void verifiedCredentialsOpenTheSessionWithCookieAndProfile() throws Exception {
        String email = "login.verificada@example.com";
        register(email);
        markVerified(email);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("  Login.Verificada@Example.COM ", "Password123!")))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("amano_auth=")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andExpect(jsonPath("$.email").value(email)) // normalized email in the profile
                .andExpect(jsonPath("$.firstName").value("Ana"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    /** FR-004/FR-014: valid credentials on a PENDING account never log in (403, no cookie). */
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

    /** FR-014: a wrong password is a 401 that does not disclose the account exists. */
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
                .andExpect(jsonPath("$.title").value("No autorizado"));
    }

    /** FR-014: an unknown account answers the very same 401 as a wrong password. */
    @Test
    void unknownAccountIsUnauthorizedWithTheSameShape() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("no.existe@example.com", "Password123!")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.title").value("No autorizado"));
    }
}
