package com.amano.backend.auth;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.amano.backend.user.User;

/**
 * POST /api/auth/register (task T021, feature 002): single atomic multipart
 * submit. Since the registration no longer starts a session (FR-001, replaces
 * FR-016 of 001) the 201 response carries ONLY the normalized email and neither
 * {@code Set-Cookie} nor {@code Location} (contracts/email-verification-api.md
 * "Cambio sobre contrato de 001"). The session cookie is now issued by the
 * verification flow (US2).
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
}
