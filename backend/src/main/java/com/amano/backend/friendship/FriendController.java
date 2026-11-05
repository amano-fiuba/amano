package com.amano.backend.friendship;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Friends endpoints (features 013 and 016,
 * contracts/friends-api.md and contracts/delete-friend-api.md). All live under
 * /api/users/** so they inherit the verified-session rule of 003 from
 * SecurityConfig; the user always comes from the session.
 */
@RestController
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    /** GET /api/users/me/friends: friends of the session user, alphabetically. */
    @GetMapping("/api/users/me/friends")
    public ResponseEntity<?> list() {
        Long userId = currentUserId();
        if (userId == null) {
            return unauthorized();
        }
        List<FriendResponse> friends = friendService.listFriends(userId);
        return ResponseEntity.ok(friends);
    }

    /**
     * GET /api/users/me/friends/{friendId}/deletion-eligibility: whether the
     * session user can delete the friendship and, when not, the reason (016,
     * FR-022). The id is a raw string so a non-numeric value is a 400 before any
     * query.
     */
    @GetMapping("/api/users/me/friends/{friendId}/deletion-eligibility")
    public ResponseEntity<?> deletionEligibility(@PathVariable("friendId") String friendId) {
        Long userId = currentUserId();
        if (userId == null) {
            return unauthorized();
        }
        DeletionEligibilityResponse eligibility =
                friendService.checkDeletionEligibility(userId, friendId);
        return ResponseEntity.ok(eligibility);
    }

    /**
     * DELETE /api/users/me/friends/{friendId}: removes the ACCEPTED friendship
     * row and answers 204 (016, FR-023). A pending direct debt is a 403 and a
     * missing/foreign/already-deleted friendship a neutral 404.
     */
    @DeleteMapping("/api/users/me/friends/{friendId}")
    public ResponseEntity<?> delete(@PathVariable("friendId") String friendId) {
        Long userId = currentUserId();
        if (userId == null) {
            return unauthorized();
        }
        friendService.deleteFriend(userId, friendId);
        return ResponseEntity.noContent().build();
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        return principal instanceof Long id ? id : null;
    }

    private static ResponseEntity<ProblemDetail> unauthorized() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Necesitas iniciar sesión para continuar");
        problem.setTitle("No autorizado");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }
}
