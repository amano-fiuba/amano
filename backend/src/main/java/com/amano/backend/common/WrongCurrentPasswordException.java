package com.amano.backend.common;

/**
 * Raised by PUT /api/users/me/password when the current password presented by
 * the user does not match the stored hash (feature 005, FR-014): the credit
 * must never change without its 1:1 accreditation (FR-013/SC-005). The handler
 * answers 400 with the exact message, both as detail and as per-field message
 * (currentPassword) so the UI can highlight the input.
 */
public class WrongCurrentPasswordException extends RuntimeException {

    public static final String MESSAGE = "La contraseña actual no es correcta";

    public WrongCurrentPasswordException() {
        super(MESSAGE);
    }
}
