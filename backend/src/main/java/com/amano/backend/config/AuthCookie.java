package com.amano.backend.config;

import java.time.Duration;
import org.springframework.http.ResponseCookie;

/**
 * Session cookie contract (contracts/auth-api.md, tool Set-Cookie section):
 * cookie {@code amano_auth}, HttpOnly, SameSite=Strict, Max-Age=86400 (variable
 * {@code JWT_EXPIRATION}); {@code Secure} only when deployed over HTTPS
 * (not on local http://localhost, compose Mode A). Task T011.
 */
public final class AuthCookie {

    public static final String NAME = "amano_auth";
    public static final String PATH = "/";

    private AuthCookie() {
    }

    public static ResponseCookie sessionCookie(String jwt, Duration maxAge, boolean secure) {
        return ResponseCookie.from(AuthCookie.NAME, jwt)
                .httpOnly(true)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .secure(secure)
                .build();
    }
}
