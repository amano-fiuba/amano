package com.amano.backend.groupexpense;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/groups/{groupId}/debts/pending} (022 US7,
 * contracts/balances-and-debts.md). The caller always comes from the session
 * principal placed by {@code JwtCookieAuthenticationFilter}; non-members get
 * the neutral 404, exactly like the balances endpoint.
 */
@RestController
public class PendingDebtController {

    private final PendingDebtService service;

    public PendingDebtController(PendingDebtService service) {
        this.service = service;
    }

    @GetMapping(path = "/api/groups/{groupId}/debts/pending", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> pending(@PathVariable Long groupId) {
        Long callerId = currentUserId();
        if (callerId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(service.pending(callerId, groupId));
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }

    /** Same body as the other group endpoints. */
    private static ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                Map.of("detail", "Debés iniciar sesión para continuar"));
    }
}
