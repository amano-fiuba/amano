package com.amano.backend.payment;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/groups/{groupId}/payments/{paymentId}/confirm} and
 * {@code .../reject} (022 contract {@code payment-validation.md}): an
 * administrator decides a payment registered as {@code REPORTED}. The caller is
 * always the session principal; errors are mapped by
 * {@code ProblemDetailExceptionHandler}.
 */
@RestController
public class PaymentDecisionController {

    private final PaymentDecisionService service;

    public PaymentDecisionController(PaymentDecisionService service) {
        this.service = service;
    }

    @PostMapping(path = "/api/groups/{groupId}/payments/{paymentId}/confirm",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> confirm(@PathVariable Long groupId, @PathVariable Long paymentId) {
        Long callerId = currentUserId();
        if (callerId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(service.confirm(callerId, groupId, paymentId));
    }

    @PostMapping(path = "/api/groups/{groupId}/payments/{paymentId}/reject",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> reject(@PathVariable Long groupId, @PathVariable Long paymentId) {
        Long callerId = currentUserId();
        if (callerId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(service.reject(callerId, groupId, paymentId));
    }

    @GetMapping(path = "/api/groups/{groupId}/payments/pending-validation",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> pendingValidation(@PathVariable Long groupId) {
        Long callerId = currentUserId();
        if (callerId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(service.pendingValidation(callerId, groupId));
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }

    /** Same body as the 010/017/018 endpoints. */
    private static ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                Map.of("detail", "Debés iniciar sesión para continuar"));
    }
}
