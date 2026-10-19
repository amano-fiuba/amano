package com.amano.backend.config;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cookie-based authentication filter: any request carrying a valid {@code amano_auth}
 * JWT cookie is authenticated with the user id as principal; requests without a
 * valid cookie stay anonymous and fall through to the 401 ProblemDetail entry point.
 * Task T011 (FR-007, FR-016, SC-005).
 */
public class JwtCookieAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtCookieAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        readCookie(request)
                .flatMap(jwtService::parseSubject)
                .ifPresent(userId -> {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userId, null, List.of());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });

        try {
            filterChain.doFilter(request, response);
        }
        finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Optional<String> readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (AuthCookie.NAME.equals(cookie.getName())) {
                return Optional.ofNullable(cookie.getValue());
            }
        }
        return Optional.empty();
    }
}
