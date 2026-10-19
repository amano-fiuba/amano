package com.amano.backend.verification;

import java.util.Locale;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.amano.backend.common.InvalidCredentialsException;
import com.amano.backend.common.UnverifiedAccountException;
import com.amano.backend.config.AuthCookie;
import com.amano.backend.config.JwtService;
import com.amano.backend.user.User;
import com.amano.backend.user.UserAccountResponse;
import com.amano.backend.user.UserMapper;
import com.amano.backend.user.UserRepository;

import jakarta.servlet.http.HttpServletRequest;

/**
 * <strong>STUB PROVISORIO</strong> — {@code POST /api/auth/login} (task T041,
 * contracts/email-verification-api.md, research.md D7).
 *
 * <p>This controller is <em>not</em> the real login feature: it exists only so
 * that user story 3 can prove FR-014 on the server — a valid account that has
 * not verified its email must not open a session — without pulling the whole
 * login UI/feature (out of scope) into this one. It validates the credentials
 * with the same {@link PasswordEncoder} of the registration and, only for a
 * VERIFIED account, issues the session cookie of 001 ({@link JwtService} +
 * {@link AuthCookie}) and returns the profile with the shape of
 * {@code GET /api/users/me}.
 *
 * <p>Behaviour contract:
 * <ul>
 *   <li>200 OK — valid credentials and verified account: session + profile.</li>
 *   <li>403 Forbidden — valid credentials and PENDING account (FR-004/FR-014):
 *       {@code title "Cuenta no verificada"}, the exact Spanish detail and
 *       {@code properties.action = "RESEND_VERIFICATION"}; never a cookie.</li>
 *   <li>401 Unauthorized — wrong password or unknown account: same
 *       {@code "No autorizado"} ProblemDetail of the session filter, so the
 *       existence of the account is not disclosed.</li>
 * </ul>
 *
 * @deprecated PROVISIONAL STUB — to be REPLACED by the real login feature. It is
 *     deliberately marked {@code @Deprecated(forRemoval = true)} and must not be
 *     extended: it carries no rate limiting, no password reset, no lockout and no
 *     login UI. Remove it together with its tests (SimulatedLoginIT) once the real
 *     feature lands.
 */
@Deprecated(forRemoval = true)
@RestController
@RequestMapping("/api/auth")
public class SimulatedLoginController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    public SimulatedLoginController(UserRepository userRepository, PasswordEncoder passwordEncoder,
            JwtService jwtService, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
    }

    /** Body of the stub login: the same JSON credentials the real feature will take. */
    public record LoginRequest(String email, String password) {
    }

    @PostMapping("/login")
    public ResponseEntity<UserAccountResponse> login(
            @RequestBody(required = false) LoginRequest request, HttpServletRequest httpRequest) {
        User user = authenticate(request);

        String jwt = jwtService.issueToken(user.getId());
        ResponseCookie cookie = AuthCookie.sessionCookie(
                jwt, jwtService.expiration(), httpRequest.isSecure());
        UserAccountResponse profile = userMapper.summaryOf(user.getId());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(profile);
    }

    /**
     * Resolves the credentials to a VERIFIED account or fails with the contract's
     * error. The email is normalized as in registration (trim + lowercase) so the
     * lookup matches the stored identity, and the two failure modes are
     * deliberately collapsed into the same 401 (FR-014).
     */
    private User authenticate(LoginRequest request) {
        String email = request == null || request.email() == null
                ? "" : request.email().trim().toLowerCase(Locale.ROOT);
        String password = request == null ? null : request.password();

        User user = email.isEmpty() ? null : userRepository.findByEmail(email).orElse(null);
        if (user == null || password == null
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (user.getEmailVerifiedAt() == null) {
            // FR-014: valid credentials are still not enough for a PENDING account.
            throw new UnverifiedAccountException();
        }
        return user;
    }
}
