package com.amano.backend.user;

import java.util.List;

import org.springframework.http.CacheControl;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.amano.backend.user.UserAccountResponse;

/**
 * Authenticated user endpoints (tasks T020 + T034 of 001, T006 of 005, 008): the
 * principal is the user id placed by JwtCookieAuthenticationFilter.
 *
 * Reads (001/002): GET /api/users/me returns the owner's own profile
 * (FR-009/FR-021: cbuCvu is private to the session owner by design);
 * GET /api/users/{id}/avatar serves the avatar of the session user, of a friend
 * or of someone sharing an active group ({@link AvatarVisibility}) — any other
 * id is 404, the same answer as an account without a custom avatar.
 *
 * Search (008): GET /api/users/search?q= returns verified accounts matching the
 * term (minimal DTO, never the caller, at most 20).
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
    private final UserSearchService userSearchService;
    private final AvatarVisibility avatarVisibility;

    public UserController(UserMapper userMapper, UserAvatarRepository userAvatarRepository,
            UserAccountService userAccountService, UserSearchService userSearchService,
            AvatarVisibility avatarVisibility) {
        this.userMapper = userMapper;
        this.userAvatarRepository = userAvatarRepository;
        this.userAccountService = userAccountService;
        this.userSearchService = userSearchService;
        this.avatarVisibility = avatarVisibility;
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

    /**
     * GET /api/users/search?q= (feature 008): verified accounts matching the term,
     * excluding the caller, at most 20. The verified-session rule is enforced by
     * SecurityConfig on /api/users/**; an invalid term is a 400 (FR-004/FR-019).
     */
    @GetMapping("/api/users/search")
    public ResponseEntity<?> search(@RequestParam(value = "q", required = false) String q) {
        Long userId = currentUserId();
        if (userId == null) {
            return unauthorized();
        }
        List<UserSearchResult> results = userSearchService.search(userId, q);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/api/users/{id}/avatar")
    public ResponseEntity<?> avatar(@PathVariable("id") Long requestedId) {
        Long sessionUserId = currentUserId();
        if (sessionUserId == null || requestedId == null
                || !avatarVisibility.canView(sessionUserId, requestedId)) {
            return notFoundProblem();
        }
        return userAvatarRepository.findById(requestedId)
                .<ResponseEntity<?>>map(stored -> {
                    // D6: the stored content type matches the validated magic bytes.
                    MediaType mediaType = MediaType.valueOf(stored.getContentType());
                    // The URL is fixed but the content is mutable (005 replaces/
                    // deletes the binary), so long max-age would serve STALE
                    // images for up to an hour. no-cache + ETag instead: the
                    // browser must revalidate every time, and the ETag (content
                    // fingerprint) keeps unchanged avatars cheap (304, no byte
                    // re-download — the intent of the old max-age=3600 header).
                    return ResponseEntity.ok()
                            .contentType(mediaType)                                // exact MIME (FR-010)
                            .header("X-Content-Type-Options", "nosniff")           // mandated (FR-010/D5)
                            .cacheControl(CacheControl.noCache()
                                    .cachePrivate()
                                    .mustRevalidate())
                            .eTag(Long.toHexString(UserAccountResponse.versionOf(stored.getData())))
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
