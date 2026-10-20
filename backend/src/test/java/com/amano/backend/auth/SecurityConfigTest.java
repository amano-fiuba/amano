package com.amano.backend.auth;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.amano.backend.config.JwtProperties;
import com.amano.backend.config.JwtService;
import com.amano.backend.config.SecurityConfig;
import com.amano.backend.health.HealthController;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

import jakarta.servlet.http.Cookie;

/**
 * Contract tests of the security layer (task T012; FR-007, FR-016, SC-005).
 * Feature 003 adds the rules of user story 3 (task T039): the login stub is
 * public and {@code /api/users/**} only accepts a session whose account is
 * VERIFIED (FR-004/SC-002). Depends on T011 (SecurityConfig/JwtService must
 * exist): deliberately not [P].
 *
 * <p>The JWT filter is NOT imported here: {@code SecurityConfig} already adds it
 * to the security chain, and registering it again as a context Filter bean would
 * run it OUTSIDE that chain, where the stateless {@code SecurityContextHolderFilter}
 * replaces the authentication it had set (a valid cookie would be seen as anonymous).
 */
@WebMvcTest(HealthController.class)
@Import({ SecurityConfig.class })
@TestPropertySource(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    /** The verified-account rule (T040) reads the account through this repository. */
    @MockitoBean
    private UserRepository userRepository;

    private Cookie userCookie(long userId) {
        return new Cookie("amano_auth", jwtService.issueToken(userId));
    }

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
    void loginStubEndpointIsReachableWithoutSession() throws Exception {
        // permitAll (feature 003, FR-014): the login stub cannot require a session.
        // 404 = no SimulatedLoginController in this slice.
        mockMvc.perform(post("/api/auth/login"))
                .andExpect(status().isNotFound());
    }

    @Test
    void resendVerificationEndpointIsReachableWithoutSession() throws Exception {
        // permitAll (feature 003, FR-011): the resend is a public, neutral endpoint.
        // 404 = no EmailVerificationController in this slice.
        mockMvc.perform(post("/api/auth/resend-verification"))
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

    /** FR-004/SC-002 (T039): a valid cookie of a PENDING account is still 401. */
    @Test
    void usersEndpointsWithUnverifiedCookieReturn401ProblemDetail() throws Exception {
        User pending = new User();
        pending.setEmailVerifiedAt(null);
        given(userRepository.findById(7L)).willReturn(Optional.of(pending));

        mockMvc.perform(get("/api/users/me").cookie(userCookie(7L)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("No autorizado"));
    }

    /** FR-004/SC-002 (T039): once verified, the same cookie passes the rule. */
    @Test
    void usersEndpointsWithVerifiedCookiePassTheRule() throws Exception {
        User verified = new User();
        verified.setEmailVerifiedAt(OffsetDateTime.now());
        given(userRepository.findById(8L)).willReturn(Optional.of(verified));

        // The rule lets the request through to the dispatcher; this slice has no
        // UserController, so the reached-but-unmapped path is a 404 (a denied
        // request would be 401 instead).
        mockMvc.perform(get("/api/users/me").cookie(userCookie(8L)))
                .andExpect(status().isNotFound());
    }

    /** FR-004/SC-002 (T039): an authenticated id with no account is not a session. */
    @Test
    void usersEndpointsWithCookieOfMissingAccountReturn401() throws Exception {
        given(userRepository.findById(9L)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/users/me").cookie(userCookie(9L)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("No autorizado"));
    }
}
