package com.amano.backend.friendship;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Friendship request endpoints (feature 011, contracts/friendship-requests-api.md).
 * Both live under /api/users/**, so they inherit the verified-session rule of 003
 * from SecurityConfig. The sender/recipient is always the session principal; the
 * request never carries the sender id.
 */
@RestController
public class FriendshipRequestController {

    private final FriendshipRequestService friendshipRequestService;

    public FriendshipRequestController(FriendshipRequestService friendshipRequestService) {
        this.friendshipRequestService = friendshipRequestService;
    }

    /** POST /api/users/me/friendship-requests: creates a PENDING request (US1). */
    @PostMapping(path = "/api/users/me/friendship-requests",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> send(@RequestBody(required = false) SendFriendshipRequestRequest request) {
        Long userId = currentUserId();
        if (userId == null) {
            return unauthorized();
        }
        Long recipientId = request == null ? null : request.recipientId();
        FriendshipRequestResponse created = friendshipRequestService.send(userId, recipientId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** GET /api/users/me/friendship-requests/received: pending inbox (US3). */
    @GetMapping("/api/users/me/friendship-requests/received")
    public ResponseEntity<?> received() {
        Long userId = currentUserId();
        if (userId == null) {
            return unauthorized();
        }
        List<ReceivedFriendshipRequestResponse> received =
                friendshipRequestService.listReceivedPending(userId);
        return ResponseEntity.ok(received);
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
