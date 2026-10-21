package com.amano.backend.passwordreset;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public password recovery endpoints under /api/auth (feature 006). Both live in
 * {@link PasswordResetService}:
 * - POST /forgot-password (FR-001/FR-002): always 200 with the neutral message,
 *   identical whether the account exists, is verified or not.
 * - POST /reset-password (FR-010..FR-015): redeems the emailed token and sets the
 *   new password. 200 on success (no session is opened); 400/410 are mapped by
 *   ProblemDetailExceptionHandler.
 */
@RestController
@RequestMapping("/api/auth")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    /** Body of the request: the address the user typed (normalized server-side). */
    public record ForgotPasswordRequest(String email) {
    }

    /** Body of the redemption: the clear token of the emailed link and the new password twice. */
    public record ResetPasswordRequest(String token, String password, String confirmPassword) {
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @RequestBody(required = false) ForgotPasswordRequest request) {
        String message = passwordResetService.requestReset(request == null ? null : request.email());
        return ResponseEntity.ok(Map.of("message", message));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @RequestBody(required = false) ResetPasswordRequest request) {
        ResetPasswordRequest body = request == null ? new ResetPasswordRequest(null, null, null) : request;
        String message = passwordResetService.resetPassword(body.token(), body.password(), body.confirmPassword());
        return ResponseEntity.ok(Map.of("message", message));
    }
}
