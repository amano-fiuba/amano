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
 */
@RestController
@RequestMapping("/api/auth")
public class EmailVerificationController {

    private final EmailVerificationService verificationService;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    public EmailVerificationController(EmailVerificationService verificationService,
            JwtService jwtService, UserMapper userMapper) {
        this.verificationService = verificationService;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
    }

    /** Body of the redemption: the clear token taken from the emailed link. */
    public record VerifyEmailRequest(String token) {
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
}
