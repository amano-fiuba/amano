package com.amano.backend.user;

/**
 * Shared password policy of 001 (FR-006 + Q2): 8–64 chars, at least one
 * uppercase, one lowercase, one digit and one special char, Spanish messages.
 * The edit feature of 005 reuses it verbatim (spec 005 reuses the field rules
 * of 001 without modifications). Returns null when the password is valid.
 */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    /** Policy message, or null when the password complies (FR-006/Q2). */
    public static String validationError(String password) {
        if (password == null || password.length() < 8) {
            return "La contraseña debe tener al menos 8 caracteres";
        }
        if (password.length() > 64) {
            return "La contraseña no puede superar los 64 caracteres";
        }
        boolean missingRequirements =
                !password.matches(".*[A-Z].*")
                        || !password.matches(".*[a-z].*")
                        || !password.matches(".*[0-9].*")
                        || !password.matches(".*[^A-Za-z0-9].*");
        if (missingRequirements) {
            return "La contraseña debe incluir una mayúscula, una minúscula, un número y un símbolo";
        }
        return null;
    }
}
