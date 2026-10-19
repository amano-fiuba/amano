package com.amano.backend.common;

/**
 * Bad credentials on the provisional login stub of feature 003 (FR-014): the
 * account does not exist or the password is wrong, and both cases are
 * deliberately indistinguishable. The handler answers 401 with the very same
 * "No autorizado" ProblemDetail the session filter of 001 produces, so no
 * existence is disclosed.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Necesitas iniciar sesión para continuar");
    }
}
