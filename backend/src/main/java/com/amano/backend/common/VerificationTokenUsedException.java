package com.amano.backend.common;

/**
 * Raised when the redemption finds a token whose {@code used_at} is already set
 * (feature 003, FR-007): a link can only ever be consumed once. The handler
 * answers 409 without issuing a session, pointing the user to the login screen.
 */
public class VerificationTokenUsedException extends RuntimeException {

    public VerificationTokenUsedException() {
        super("Este enlace de verificación ya ha sido utilizado. Por favor, inicia sesión");
    }
}
