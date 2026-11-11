package com.amano.backend.groupexpense;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.amano.backend.common.ProblemDetailExceptionHandler;

/**
 * 027 US4 (contracts/shared-expenses-api.md): expenses shared with a friend. Under
 * /api/users/** so they inherit the verified-session rule; the caller always comes
 * from the session.
 */
@RestController
public class FriendExpenseController {

    private final FriendExpenseService service;

    public FriendExpenseController(FriendExpenseService service) {
        this.service = service;
    }

    @PostMapping(path = "/api/users/me/friends/{friendId}/expenses", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(@PathVariable Long friendId,
            @RequestHeader(ProblemDetailExceptionHandler.IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @RequestBody CreateGroupExpenseRequest request) {
        Long callerId = currentUserId();
        if (callerId == null) {
            return unauthorized();
        }
        UUID key = GroupExpenseController.parseIdempotencyKey(idempotencyKey);
        GroupExpenseService.Registration registration;
        try {
            registration = service.create(callerId, friendId, key, request);
        }
        catch (DataIntegrityViolationException race) {
            registration = service.resolveConcurrentRegistration(callerId, friendId, key, request);
        }
        GroupExpenseResponse expense = registration.expense();
        if (!registration.created()) {
            return ResponseEntity.ok(expense);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/users/me/friends/" + friendId + "/expenses/" + expense.id()))
                .body(expense);
    }

    @GetMapping(path = "/api/users/me/friends/{friendId}/expenses/{expenseId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get(@PathVariable Long friendId, @PathVariable Long expenseId) {
        Long callerId = currentUserId();
        if (callerId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(service.get(callerId, friendId, expenseId));
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }

    private static ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                Map.of("detail", "Debés iniciar sesión para continuar"));
    }
}
