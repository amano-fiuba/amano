package com.amano.backend.auth;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.amano.backend.config.AuthCookie;
import com.amano.backend.user.User;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Public auth endpoints under /api/auth.
 * - POST /register (feature 003): atomic multipart submit; the 201 carries only
 *   the normalized email, with no session cookie (FR-001 of 003).
 * - POST /logout (feature 004): expires the HttpOnly session cookie, which the
 *   SPA cannot delete itself. Public and idempotent: without a session it is a
 *   harmless 204.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegistrationService registrationService;

    public AuthController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(
            @ModelAttribute RegisterRequest request,
            @RequestPart(value = "avatar", required = false) MultipartFile avatar) {
        User user = registrationService.register(request, avatar);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("email", user.getEmail()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        ResponseCookie expired = AuthCookie.expiredCookie(httpRequest.isSecure());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expired.toString())
                .build();
    }
}
