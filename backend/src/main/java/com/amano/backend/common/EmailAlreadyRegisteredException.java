package com.amano.backend.common;

/**
 * Raised when registration finds a <strong>verified</strong> account with the same
 * email (feature 003, FR-016): the conflict behavior of 001 is kept. An email that
 * only has a <strong>pending</strong> (unverified) account is replaced instead —
 * see {@code RegistrationService}. The handler answers 409 with the same
 * ProblemDetail used for the unique-index race (SC-004).
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("El correo electrónico ya se encuentra registrado");
    }
}
