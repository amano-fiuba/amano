package com.amano.backend.payment;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.common.ProblemDetailExceptionHandler;

/**
 * {@code POST /api/payments} (018 plan.md §Contrato REST). Who registers is
 * always the session principal. A new payment answers {@code 201} with
 * {@code Location}, an idempotent replay {@code 200} (as 007/017). Errors
 * (400/403/404/409/422) are mapped by {@code ProblemDetailExceptionHandler}.
 */
@RestController
public class PaymentController {

    private static final String INVALID_IDEMPOTENCY_KEY =
            "La cabecera Idempotency-Key debe ser un UUID válido";

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping(path = "/api/payments", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> register(
            @RequestHeader(ProblemDetailExceptionHandler.IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @RequestBody RegisterPaymentRequest request) {
        Long callerId = currentUserId();
        if (callerId == null) {
            return unauthorized();
        }
        UUID key = parseIdempotencyKey(idempotencyKey);
        PaymentService.Registration registration;
        try {
            registration = service.register(callerId, key, request);
        }
        catch (DataIntegrityViolationException race) {
            registration = service.resolveConcurrentRegistration(callerId, key, request);
        }
        PaymentResponse payment = registration.payment();
        if (!registration.created()) {
            return ResponseEntity.ok(payment);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/payments/" + payment.id()))
                .body(payment);
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

    /** Same body as the 010/017 group endpoints. */
    private static ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                Map.of("detail", "Debés iniciar sesión para continuar"));
    }
}
