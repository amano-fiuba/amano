package com.amano.backend.common;

/**
 * Bad credentials on POST /api/auth/login (feature 004): the account does not
 * exist or the password is wrong, and both cases are deliberately
 * indistinguishable — same status, title and detail — so the response never
 * discloses whether an email is registered. The handler answers 401.
 */
public class InvalidCredentialsException extends RuntimeException {

    public static final String MESSAGE = "El correo electrónico o la contraseña son incorrectos";

    public InvalidCredentialsException() {
        super(MESSAGE);
    }
}
