package com.amano.backend.friendship;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Friends list endpoint (feature 013, contracts/friends-api.md). Lives under
 * /api/users/** so it inherits the verified-session rule of 003 from
 * SecurityConfig (FR-013); the user always comes from the session (FR-014).
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
