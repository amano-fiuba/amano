package com.amano.backend.groupexpense;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 028: management of the recurring series of a group. Any active member can
 * list them; the creator, the payer or an admin can pause/resume or change the
 * frequency. Errors follow the group-expenses contract.
 */
@RestController
public class GroupRecurringExpenseController {

    private final GroupRecurringExpenseService service;

    public GroupRecurringExpenseController(GroupRecurringExpenseService service) {
        this.service = service;
    }

    @GetMapping(path = "/api/groups/{id}/recurring-expenses", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(@PathVariable Long id) {
        Long callerId = currentUserId();
        if (callerId == null) {
            return unauthorized();
        }
        List<RecurringGroupExpenseResponse> series = service.listSeries(callerId, id);
        return ResponseEntity.ok(series);
    }

    @PatchMapping(path = "/api/groups/{id}/recurring-expenses/{expenseId}",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(@PathVariable Long id, @PathVariable Long expenseId,
            @RequestBody(required = false) Map<String, String> body) {
        Long callerId = currentUserId();
        if (callerId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(service.updateSeries(callerId, id, expenseId, body));
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }

    private static ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(401).body(
                Map.of("detail", "Debés iniciar sesión para continuar"));
    }
}
