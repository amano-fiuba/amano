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

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.common.ProblemDetailExceptionHandler;

/**
 * Group expense endpoints (017 US1, contracts/group-expenses-api.md). The
 * creator always comes from the session principal placed by
 * {@code JwtCookieAuthenticationFilter}. A new expense answers {@code 201} with
 * {@code Location}, an idempotent replay {@code 200} (same as 007). Errors
 * (400/403/404/422) are mapped by {@code ProblemDetailExceptionHandler}.
 */
@RestController
public class GroupExpenseController {

    private static final String INVALID_IDEMPOTENCY_KEY =
            "La cabecera Idempotency-Key debe ser un UUID válido";

    private final GroupExpenseService service;

    public GroupExpenseController(GroupExpenseService service) {
        this.service = service;
    }

    @PostMapping(path = "/api/groups/{id}/expenses", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(@PathVariable Long id,
            @RequestHeader(ProblemDetailExceptionHandler.IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @RequestBody CreateGroupExpenseRequest request) {
        Long callerId = currentUserId();
        if (callerId == null) {
            return unauthorized();
        }
        UUID key = parseIdempotencyKey(idempotencyKey);
        GroupExpenseService.Registration registration;
        try {
            registration = service.create(callerId, id, key, request);
        }
        catch (DataIntegrityViolationException race) {
            registration = service.resolveConcurrentRegistration(callerId, id, key, request);
        }
        GroupExpenseResponse expense = registration.expense();
        if (!registration.created()) {
            return ResponseEntity.ok(expense);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/groups/" + id + "/expenses/" + expense.id()))
                .body(expense);
    }

    @GetMapping(path = "/api/groups/{id}/expenses/{expenseId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get(@PathVariable Long id, @PathVariable Long expenseId) {
        Long callerId = currentUserId();
        if (callerId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(service.get(callerId, id, expenseId));
    }

    private static UUID parseIdempotencyKey(String raw) {
        try {
            return UUID.fromString(raw);
        }
        catch (IllegalArgumentException | NullPointerException exception) {
            throw new FieldValidationException(Map.of("idempotencyKey", INVALID_IDEMPOTENCY_KEY));
        }
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }

    /** Same body as the 010 group endpoints. */
    private static ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                Map.of("detail", "Debés iniciar sesión para continuar"));
    }
}
