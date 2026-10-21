package com.amano.backend.verification;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.amano.backend.config.AuthCookie;
import com.amano.backend.config.JwtService;
import com.amano.backend.user.User;
import com.amano.backend.user.UserAccountResponse;
import com.amano.backend.user.UserMapper;

import jakarta.servlet.http.HttpServletRequest;

/**
 * {@code POST /api/auth/verify-email} (task T032, contracts/email-verification-api.md):
 * redeems the token and, on success, opens the session for the calling device.
 *
 * <p>POST (not GET) is what keeps generic mailbox scanners from consuming the
 * link (D3). The emitted cookie is exactly the one of 001 — {@code amano_auth},
 * HttpOnly, SameSite=Strict, {@code Secure} only over HTTPS (D7) — and the body
 * has the same shape as {@code GET /api/users/me}, so the SPA can drop it into
 * the shared session store. Errors are mapped centrally by
 * {@code ProblemDetailExceptionHandler}.
 *
 * <p>Also hosts {@code POST /api/auth/resend-verification} (task T049,
 * FR-011..FR-013): a public, always-200 endpoint that never answers 400/404/429,
 * so the response is identical whether the account exists, is verified, is
 * pending or has an active term (SC-005). The neutral body and its internal
 * effects live in {@link EmailResendService}.
 */
@RestController
@RequestMapping("/api/auth")
public class EmailVerificationController {

    private final EmailVerificationService verificationService;
    private final EmailResendService resendService;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    public EmailVerificationController(EmailVerificationService verificationService,
            EmailResendService resendService, JwtService jwtService, UserMapper userMapper) {
        this.verificationService = verificationService;
        this.resendService = resendService;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
    }

    /** Body of the redemption: the clear token taken from the emailed link. */
    public record VerifyEmailRequest(String token) {
    }

    /** Body of the resend: the address the user typed (normalized server-side). */
    public record ResendVerificationRequest(String email) {
    }

    @PostMapping("/verify-email")
    public ResponseEntity<UserAccountResponse> verifyEmail(
            @RequestBody(required = false) VerifyEmailRequest request,
            HttpServletRequest httpRequest) {
        User user = verificationService.redeem(request == null ? null : request.token());

        String jwt = jwtService.issueToken(user.getId());
        ResponseCookie cookie = AuthCookie.sessionCookie(
                jwt, jwtService.expiration(), httpRequest.isSecure());
        UserAccountResponse profile = userMapper.summaryOf(user.getId());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(profile);
    }

    /**
     * Resends the verification link with the neutral, always-200 answer of
     * FR-012/SC-005: the message never varies and {@code remainingSeconds} is the
     * server's source of truth for the visible countdown (FR-010/FR-013).
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<EmailResendService.ResendResult> resendVerification(
            @RequestBody(required = false) ResendVerificationRequest request) {
        EmailResendService.ResendResult result =
                resendService.resend(request == null ? null : request.email());
        return ResponseEntity.ok(result);
    }
}
