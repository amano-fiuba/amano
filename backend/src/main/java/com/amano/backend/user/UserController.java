package com.amano.backend.user;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.amano.backend.user.UserAccountResponse;

/**
 * Authenticated user endpoints (tasks T020 + T034 of 001, T006 of 005): the
 * principal is the user id placed by JwtCookieAuthenticationFilter.
 *
 * Reads (001/002): GET /api/users/me returns the owner's own profile
 * (FR-009/FR-021: cbuCvu is private to the session owner by design);
 * GET /api/users/{id}/avatar serves the session user's own avatar binaries —
 * any other id is 404.
 *
 * Mutations (feature 005, contracts/users-api.md): every endpoint resolves the
 * session principal AGAIN (FR-017) — the request NEVER carries a user id of
 * destination, so third-party data is unreachable. None of them can change the
 * email (FR-007): it is not part of any mutation contract.
 */
@RestController
public class UserController {

    private final UserMapper userMapper;
    private final UserAvatarRepository userAvatarRepository;
    private final UserAccountService userAccountService;

    public UserController(UserMapper userMapper, UserAvatarRepository userAvatarRepository,
            UserAccountService userAccountService) {
        this.userMapper = userMapper;
        this.userAvatarRepository = userAvatarRepository;
        this.userAccountService = userAccountService;
    }

    @GetMapping("/api/users/me")
    public ResponseEntity<?> me() {
        UserAccountResponse profile = userMapper.summaryOf(currentUserId());
        if (profile == null) {
            // Session authenticated but the account no longer exists: no profile to expose.
            return unauthorized();
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

    /** PUT /api/users/me (US1): names, lastName, CBU/CVU and currencies, never email. */
    @PutMapping(path = "/api/users/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateProfile(@RequestBody UpdateProfileRequest request) {
        UserAccountResponse updated = userAccountService.updateProfile(currentUserId(), request);
        return updated == null ? unauthorized() : ResponseEntity.ok(updated);
    }

    /** PUT /api/users/me/password (US2): the session is NOT rotated (FR-013). */
    @PutMapping(path = "/api/users/me/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updatePassword(@RequestBody UpdatePasswordRequest request) {
        UserAccountResponse updated = userAccountService.changePassword(currentUserId(), request);
        return updated == null ? unauthorized() : ResponseEntity.ok(updated);
    }

    /** PUT /api/users/me/avatar (US3): a missing part is a 400, not a silent no-op. */
    @PutMapping(path = "/api/users/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateAvatar(
            @org.springframework.web.bind.annotation.RequestPart(value = "avatar", required = false) MultipartFile avatar) {
        UserAccountResponse updated = userAccountService.updateAvatar(currentUserId(), avatar);
        return updated == null ? unauthorized() : ResponseEntity.ok(updated);
    }

    /** DELETE /api/users/me/avatar (US3): always 204 (idempotent), no body. */
    @DeleteMapping("/api/users/me/avatar")
    public ResponseEntity<?> deleteAvatar() {
        UserAccountResponse updated = userAccountService.deleteAvatar(currentUserId());
        return updated == null ? unauthorized() : ResponseEntity.noContent().build();
    }

    private static ResponseEntity<ProblemDetail> unauthorized() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Necesitas iniciar sesión para continuar");
        problem.setTitle("No autorizado");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
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
