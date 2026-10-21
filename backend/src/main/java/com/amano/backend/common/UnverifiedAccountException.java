package com.amano.backend.common;

/**
 * Valid credentials on a not-yet-verified account (feature 003, FR-004/FR-014):
 * the handler answers 403 with the exact message and the machine-readable
 * {@code properties.action = RESEND_VERIFICATION} indicator, so the real login
 * feature can offer the resend link.
 */
public class UnverifiedAccountException extends RuntimeException {

    public UnverifiedAccountException() {
        super("Tu cuenta aún no ha sido verificada. Por favor, revisa tu correo electrónico");
    }
}
