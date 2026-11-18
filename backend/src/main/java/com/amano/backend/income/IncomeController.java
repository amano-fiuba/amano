package com.amano.backend.income;

import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
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
 * Personal income endpoints (specs/032-income-management, contracts/personal-incomes-api.md).
 * {@code POST /api/incomes} takes the owner from the session principal placed by
 * {@code JwtCookieAuthenticationFilter}; the payload cannot choose it. A valid
 * operation answers {@code 201}, an idempotent replay {@code 200}.
 *
 * <p>Incomes are immutable (FR-006): this controller deliberately maps no
 * {@code PATCH}, {@code PUT} or {@code DELETE}, so any such request already answers
 * {@code 405} through {@code ProblemDetailExceptionHandler}.
 */
@RestController
public class IncomeController {

    private static final String INVALID_IDEMPOTENCY_KEY =
            "La cabecera Idempotency-Key debe ser un UUID válido";

    private final IncomeService incomeService;

    public IncomeController(IncomeService incomeService) {
        this.incomeService = incomeService;
    }

    @PostMapping(path = "/api/incomes", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(
            @RequestHeader(ProblemDetailExceptionHandler.IDEMPOTENCY_KEY_HEADER)
            String idempotencyKey,
            @RequestBody RegisterIncomeRequest request) {
        Long userId = currentUserId();
        if (userId == null) {
            return unauthorized();
        }
        UUID key = parseIdempotencyKey(idempotencyKey);
        IncomeService.Registration registration;
        try {
            registration = incomeService.registerIncome(userId, key, request);
        }
        catch (DataIntegrityViolationException race) {
            registration = incomeService.resolveConcurrentRegistration(userId, key, request);
        }
        return registration.created()
                ? ResponseEntity.status(HttpStatus.CREATED).body(registration.income())
                : ResponseEntity.ok(registration.income());
    }

    private static UUID parseIdempotencyKey(String raw) {
        try {
            return UUID.fromString(raw);
        }
        catch (IllegalArgumentException | NullPointerException exception) {
            throw new FieldValidationException(Map.of("idempotencyKey", INVALID_IDEMPOTENCY_KEY));
        }
    }

    private static ResponseEntity<ProblemDetail> unauthorized() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Necesitas iniciar sesión para continuar");
        problem.setTitle("No autorizado");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        return principal instanceof Long id ? id : null;
    }
}
