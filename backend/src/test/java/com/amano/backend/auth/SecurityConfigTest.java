package com.amano.backend.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.amano.backend.config.JwtCookieAuthenticationFilter;
import com.amano.backend.config.JwtProperties;
import com.amano.backend.config.JwtService;
import com.amano.backend.config.SecurityConfig;
import com.amano.backend.health.HealthController;

/**
 * Contract tests of the security layer (task T012; FR-007, FR-016, SC-005):
 * {@code GET /api/users/me} without a session cookie returns 401 ProblemDetail,
 * and the registration endpoint stays publicly reachable (permitAll).
 * Depends on T011 (SecurityConfig/JwtService must exist): deliberately not [P].
 */
@WebMvcTest(HealthController.class)
@Import({ SecurityConfig.class, JwtCookieAuthenticationFilter.class })
@TestPropertySource(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void registerEndpointIsReachableWithoutSession() throws Exception {
        // permitAll: the request reaches the dispatcher — 404 because this @WebMvcTest
        // slice does not load AuthController (a security block would be 401/403).
        mockMvc.perform(post("/api/auth/register"))
                .andExpect(status().isNotFound());
    }

    @Test
    void verifyEmailEndpointIsReachableWithoutSession() throws Exception {
        // permitAll (feature 003, FR-005): the link is the credential, so no cookie
        // is required. 404 = no EmailVerificationController in this slice.
        mockMvc.perform(post("/api/auth/verify-email"))
                .andExpect(status().isNotFound());
    }

    @Test
    void usersEndpointsWithoutSessionReturn401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("No autorizado"));
    }

    @Test
    void usersEndpointsWithInvalidCookieReturn401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/users/me").cookie(new jakarta.servlet.http.Cookie("amano_auth", "tampered.token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
