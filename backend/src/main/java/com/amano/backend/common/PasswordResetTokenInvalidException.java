package com.amano.backend.common;

/**
 * Raised when a password reset link carries a token that does not match any
 * current stored hash (feature 006, FR-012): invented, tampered, already redeemed
 * or superseded by a newer request. The handler answers 400 with a neutral
 * message that never reveals whether the account exists.
 */
public class PasswordResetTokenInvalidException extends RuntimeException {

    public PasswordResetTokenInvalidException() {
        super("El enlace para restablecer la contraseña no es válido");
    }
}
