package com.amano.backend.common;

/**
 * Raised when a verification link carries a token that does not match any stored
 * hash (feature 003, FR-008): tampered, truncated or invented. The handler
 * answers 400 with the neutral "El enlace de verificación no es válido" message.
 */
public class VerificationTokenInvalidException extends RuntimeException {

    public VerificationTokenInvalidException() {
        super("El enlace de verificación no es válido");
    }
}
