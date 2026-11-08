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
 * Settlement proposal endpoint (022 T027, contracts/liquidation-proposal.md).
 * It is a GET without body and never writes: the caller always comes from the
 * session principal and non-members receive the neutral 404.
 */
@RestController
public class GroupSettlementController {

    private final GroupSettlementService service;

    public GroupSettlementController(GroupSettlementService service) {
        this.service = service;
    }

    @GetMapping(path = "/api/groups/{id}/settlement-proposal", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> proposal(@PathVariable Long id) {
        Long callerId = currentUserId();
        if (callerId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(service.proposal(callerId, id));
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
