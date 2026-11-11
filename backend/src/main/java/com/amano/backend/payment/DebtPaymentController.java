package com.amano.backend.payment;

import java.net.URI;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/groups/{groupId}/debts/{debtId}/payments} (022 contract
 * {@code payment-validation.md}): registers a full payment of a debt. Who
 * registers is always the session principal. A new payment answers {@code 201}
 * with {@code Location}; an idempotent replay answers {@code 200} (same pattern
 * as 007/017/018). Errors are mapped by {@code ProblemDetailExceptionHandler}.
 */
@RestController
public class DebtPaymentController {

    private final PaymentValidationService service;

    public DebtPaymentController(PaymentValidationService service) {
        this.service = service;
    }

    @PostMapping(path = "/api/groups/{groupId}/debts/{debtId}/payments",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> register(@PathVariable Long groupId, @PathVariable Long debtId,
            @RequestBody RegisterDebtPaymentRequest request) {
        Long callerId = currentUserId();
        if (callerId == null) {
            return unauthorized();
        }
        PaymentValidationService.Registration registration;
        try {
            registration = service.register(callerId, groupId, debtId, request);
        }
        catch (DataIntegrityViolationException race) {
            // The partial unique index uq_payments_active_debt is the database
            // backstop: another payment won the race for this debt.
            throw new DebtAlreadyHasPaymentException();
        }
        PaymentRegistrationResponse payment = registration.payment();
        if (!registration.created()) {
            return ResponseEntity.ok(payment);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/groups/" + groupId + "/payments/" + payment.paymentId()))
                .body(payment);
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
