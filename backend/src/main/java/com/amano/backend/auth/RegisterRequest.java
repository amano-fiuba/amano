package com.amano.backend.auth;

import java.util.List;

/**
 * Multipart parts of POST /api/auth/register (contracts/auth-api.md). Only the
 * mandatory parts carry bean-level required messages: FR-006/FR-004 and the
 * length rules (Q4: trim first) are enforced in RegistrationService. The
 * avatar part binds separately as @RequestPart (MultipartFile) in
 * AuthController.
 */
public record RegisterRequest(
        String firstName,
        String lastName,
        String email,
        String password,
        String confirmPassword,
        Boolean acceptTerms,
        List<String> currencies,
        String cbuCvu) {
}
