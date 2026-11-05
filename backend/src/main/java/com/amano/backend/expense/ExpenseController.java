package com.amano.backend.expense;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.common.ProblemDetailExceptionHandler;

/**
 * Expense endpoints (US1, contracts/expenses-api.md). {@code POST /api/expenses}
 * always takes the owner from the session principal placed by
 * {@code JwtCookieAuthenticationFilter}; the payload cannot choose it (FR-003).
 * A valid operation answers {@code 201}, an idempotent replay {@code 200}.
 *
 * <p>Feature 009 (specs/009-pause-recurring-expense/contracts/recurring-expenses-api.md):
 * {@code GET /api/expenses/recurring} lists the caller's series and
 * {@code PATCH /api/expenses/{id}/status} pauses, resumes or finishes one. Errors
 * (400/404/409) are mapped by {@code ProblemDetailExceptionHandler}.
 */
@RestController
public class ExpenseController {

    private static final String INVALID_IDEMPOTENCY_KEY =
            "La cabecera Idempotency-Key debe ser un UUID válido";

    private final ExpenseService expenseService;
    private final RecurringExpenseService recurringExpenseService;

    public ExpenseController(ExpenseService expenseService,
            RecurringExpenseService recurringExpenseService) {
        this.expenseService = expenseService;
        this.recurringExpenseService = recurringExpenseService;
    }

    /** Body of the state change (009): {@code ACTIVE}, {@code PAUSED} or {@code FINISHED}. */
    public record ChangeStatusRequest(String status) {
    }

    @PostMapping(path = "/api/expenses", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(
            @RequestHeader(ProblemDetailExceptionHandler.IDEMPOTENCY_KEY_HEADER)
            String idempotencyKey,
            @RequestBody RegisterExpenseRequest request) {
        Long userId = currentUserId();
        if (userId == null) {
            return unauthorized();
        }
        UUID key = parseIdempotencyKey(idempotencyKey);
        ExpenseService.Registration registration;
        try {
            registration = expenseService.registerExpense(userId, key, request);
        }
        catch (DataIntegrityViolationException race) {
            registration = expenseService.resolveConcurrentRegistration(userId, key, request);
        }
        return registration.created()
                ? ResponseEntity.status(HttpStatus.CREATED).body(registration.expense())
                : ResponseEntity.ok(registration.expense());
    }

    @GetMapping(path = "/api/expenses/recurring", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listRecurring() {
        Long userId = currentUserId();
        if (userId == null) {
            return unauthorized();
        }
        List<ExpenseResponse> series = recurringExpenseService.listSeries(userId);
        return ResponseEntity.ok(series);
    }

    /** Latest expenses of the session user for the main screen (015 FR-008). */
    @GetMapping(path = "/api/expenses/recent", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listRecent() {
        Long userId = currentUserId();
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(expenseService.listRecent(userId));
    }

    @PatchMapping(path = "/api/expenses/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> changeStatus(@PathVariable Long id,
            @RequestBody(required = false) ChangeStatusRequest request) {
        Long userId = currentUserId();
        if (userId == null) {
            return unauthorized();
        }
        String status = request == null ? null : request.status();
        return ResponseEntity.ok(recurringExpenseService.changeStatus(userId, id, status));
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
