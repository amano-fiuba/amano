package com.amano.backend.user;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated user endpoints (task T020): the principal is the user id placed
 * by JwtCookieAuthenticationFilter. GET /api/users/me returns the owner's own
 * profile (FR-009/FR-021: cbuCvu is private to the session owner by design).
 */
@RestController
public class UserController {

    private final UserMapper userMapper;

    public UserController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @GetMapping("/api/users/me")
    public ResponseEntity<UserAccountResponse> me() {
        UserAccountResponse profile = userMapper.summaryOf(currentUserId());
        if (profile == null) {
            // Session authenticated but the account no longer exists: no profile to expose.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(profile);
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        return principal instanceof Long id ? id : null;
    }
}
