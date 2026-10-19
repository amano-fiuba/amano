package com.amano.backend.common;

/**
 * Raised when the redemption finds a token past its 24 h window (feature 003,
 * FR-006). The use is recorded before throwing, so the same link cannot be
 * retried; the handler answers 410 and leads to the resend screen.
 */
public class VerificationTokenExpiredException extends RuntimeException {

    public VerificationTokenExpiredException() {
        super("El enlace de verificación ha expirado");
    }
}
