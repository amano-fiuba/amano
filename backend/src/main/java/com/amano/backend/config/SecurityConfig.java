package com.amano.backend.config;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.amano.backend.user.UserRepository;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Security and session configuration for the registration feature (task T011).
 * CSRF is disabled consciously: the deployment is same-origin (nginx proxies
 * /api/* in production, the Vite dev proxy mirrors it — plan.md Decision).
 *
 * Authorization layout:
 * - {@code POST /api/auth/register} and {@code /api/health} are public;
 * - {@code POST /api/auth/verify-email} is public too: the emailed link is the
 *   credential and the request carries no session yet (feature 003, FR-005, D7);
 * - {@code POST /api/auth/resend-verification} is public: it is the neutral,
 *   always-200 resend endpoint of feature 003 (FR-011..FR-013);
 * - {@code POST /api/auth/login} and {@code POST /api/auth/logout} are public
 *   (feature 004): login cannot require a session, and logout only expires the
 *   cookie, so it is harmless (and idempotent) without one;
 * - {@code POST /api/auth/forgot-password} and {@code POST /api/auth/reset-password}
 *   are public (feature 006): whoever forgot the password has no session, the
 *   request always answers a neutral 200 and, on redemption, the emailed link is
 *   the credential;
 * - {@code /api/users/**} and {@code /api/expenses/**} require a VERIFIED session
 *   (feature 003, FR-004, SC-002; feature 007): a cookie issued before verifying
 *   is no session at all.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
            ObjectMapper objectMapper, UserRepository userRepository) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(new JwtCookieAuthenticationFilter(jwtService),
                        UsernamePasswordAuthenticationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/api/auth/register", "/api/auth/verify-email",
                                "/api/auth/login", "/api/auth/logout", "/api/auth/resend-verification",
                                "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                        .requestMatchers("/api/users/**", "/api/expenses/**", "/api/groups/**", "/api/payments/**",
                                "/api/currencies/**", "/api/incomes/**", "/api/balance/**", "/api/stats/**",
                                "/api/v1/notifications/**")
                                .access(verifiedAccount(userRepository))
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new UnauthorizedEntryPoint(objectMapper))
                        .accessDeniedHandler(new UnauthorizedAccessDeniedHandler(objectMapper)))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /**
     * Verified-account rule (task T040, FR-004/SC-002): an authenticated principal
     * only passes when its account has {@code email_verified_at} set. The check runs
     * on the server, against the database — never trusting the client or the mere
     * possession of a cookie (constitution IX) — and covers every endpoint of the
     * {@code /api/users/**} and {@code /api/expenses/**} scopes, present or future,
     * without route enumeration.
     * A pending (or deleted) account is denied; the {@link UnauthorizedAccessDeniedHandler}
     * turns that into the 401 ProblemDetail of 001.
     */
    @Bean
    public AuthorizationManager<RequestAuthorizationContext> verifiedAccount(UserRepository userRepository) {
        return (authentication, context) -> {
            Object principal = authentication.get() == null ? null : authentication.get().getPrincipal();
            if (!(principal instanceof Long userId)) {
                // Anonymous: the entry point answers the 401.
                return new AuthorizationDecision(false);
            }
            boolean verified = userRepository.findById(userId)
                    .map(user -> user.getEmailVerifiedAt() != null)
                    .orElse(false);
            return new AuthorizationDecision(verified);
        };
    }

    @Bean
    public JwtService jwtService(JwtProperties properties) {
        return new JwtService(properties);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Returns a 401 ProblemDetail when no valid session exists (contracts/users-api.md). */
    record UnauthorizedEntryPoint(ObjectMapper objectMapper) implements AuthenticationEntryPoint {

        @Override
        public void commence(HttpServletRequest request, HttpServletResponse response,
                org.springframework.security.core.AuthenticationException authenticationException)
                throws java.io.IOException {
            writeUnauthorized(response, objectMapper);
        }
    }

    /**
     * Denies an AUTHENTICATED but unverified principal with the very same 401 as the
     * entry point (FR-004, contracts/email-verification-api.md): a pending account's
     * cookie is no session for the protected scope, so it is indistinguishable from
     * "not logged in".
     */
    record UnauthorizedAccessDeniedHandler(ObjectMapper objectMapper) implements AccessDeniedHandler {

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response,
                AccessDeniedException accessDeniedException) throws IOException {
            writeUnauthorized(response, objectMapper);
        }
    }

    /** Single 401 body shared by the entry point and the denied-handler (contract 001). */
    private static void writeUnauthorized(HttpServletResponse response, ObjectMapper objectMapper)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "Necesitas iniciar sesión para continuar");
        problem.setTitle("No autorizado");
        StringWriter json = new StringWriter();
        objectMapper.writeValue(json, problem);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(json.toString());
    }
}
