package com.amano.backend.groupexpense.whistle;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/groups/{groupId}/debts/{debtId}/whistle} (030
 * contracts/debt-whistle.md). The caller always comes from the session
 * principal; every rule is enforced by {@link DebtWhistleService}, never only
 * by the UI.
 */
@RestController
public class DebtWhistleController {

    private final DebtWhistleService service;

    public DebtWhistleController(DebtWhistleService service) {
        this.service = service;
    }

    @PostMapping(path = "/api/groups/{groupId}/debts/{debtId}/whistle", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> whistle(@PathVariable Long groupId, @PathVariable Long debtId) {
        Long callerId = currentUserId();
        if (callerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("detail", "Debés iniciar sesión para continuar"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.whistle(callerId, groupId, debtId));
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }
}
