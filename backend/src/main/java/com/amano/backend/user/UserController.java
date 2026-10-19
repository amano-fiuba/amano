package com.amano.backend.user;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.amano.backend.user.UserAccountResponse;

/**
 * Authenticated user endpoints (tasks T020 + T034): the principal is the user
 * id placed by JwtCookieAuthenticationFilter. GET /api/users/me returns the
 * owner's own profile (FR-009/FR-021: cbuCvu is private to the session owner by
 * design); GET /api/users/{id}/avatar serves the session user's own avatar
 * binaries — any other id is 404 (this feature only exposes the owner, users-api).
 */
@RestController
public class UserController {

    private final UserMapper userMapper;
    private final UserAvatarRepository userAvatarRepository;

    public UserController(UserMapper userMapper, UserAvatarRepository userAvatarRepository) {
        this.userMapper = userMapper;
        this.userAvatarRepository = userAvatarRepository;
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

    @GetMapping("/api/users/{id}/avatar")
    public ResponseEntity<?> avatar(@PathVariable("id") Long requestedId) {
        Long sessionUserId = currentUserId();
        // Only the owner's own avatar exists in this feature (users-api.md).
        if (sessionUserId == null || requestedId == null || !sessionUserId.equals(requestedId)) {
            return notFoundProblem();
        }
        return userAvatarRepository.findById(requestedId)
                .<ResponseEntity<?>>map(stored -> {
                    // D6: the stored content type matches the validated magic bytes.
                    MediaType mediaType = MediaType.valueOf(stored.getContentType());
                    return ResponseEntity.ok()
                            .contentType(mediaType)                                   // exact MIME (FR-010)
                            .header("X-Content-Type-Options", "nosniff")              // mandated (FR-010/D5)
                            .header("Cache-Control", "private, max-age=3600")         // avoid repeated downloads
                            .body(stored.getData());
                })
                .orElseGet(this::notFoundProblem); // 0 rows → client uses /avatar-default.png (D5)
    }

    private ResponseEntity<ProblemDetail> notFoundProblem() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "El usuario no tiene un avatar propio");
        problem.setTitle("Recurso no encontrado");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        return principal instanceof Long id ? id : null;
    }
}
