package com.amano.backend.auth;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * Multipart parts of POST /api/auth/register (contracts/auth-api.md).
 * Only the mandatory parts carry bean-level required messages: FR-006/FR-004
 * and the length rules (Q4: trim first) are enforced in RegistrationService,
 * which is where normalization happens.
 */
public record RegisterRequest(
        String firstName,
        String lastName,
        String email,
        String password,
        String confirmPassword,
        Boolean acceptTerms,
        List<String> currencies,
        String cbuCvu,
        MultipartFile avatar) {
}
