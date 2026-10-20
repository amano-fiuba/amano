package com.amano.backend.auth;

/**
 * JSON body of POST /api/auth/login (feature 004): the credentials typed by the
 * user. The email is normalized server-side (trim + lowercase, as in
 * registration); the password is compared as-is.
 */
public record LoginRequest(String email, String password) {
}
