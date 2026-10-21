package com.amano.backend.common;

/**
 * Raised when a password reset link is redeemed past its 1-hour window (feature
 * 006, FR-013). The token is deleted before throwing; the handler answers 410 and
 * the SPA offers to request a new link.
 */
public class PasswordResetTokenExpiredException extends RuntimeException {

    public PasswordResetTokenExpiredException() {
        super("El enlace para restablecer la contraseña ha expirado");
    }
}
