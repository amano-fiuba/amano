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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.common.ProblemDetailExceptionHandler;

/**
 * Personal income endpoints (specs/033-income-management, contracts/personal-incomes-api.md).
 * {@code POST /api/incomes} takes the owner from the session principal placed by
 * {@code JwtCookieAuthenticationFilter}; the payload cannot choose it. A valid
 * operation answers {@code 201}, an idempotent replay {@code 200}.
 *
 * <p>Incomes are immutable (FR-006): this controller deliberately maps no
 * {@code PATCH}, {@code PUT} or {@code DELETE}, so any such request already answers
 * {@code 405} through {@code ProblemDetailExceptionHandler}. The reads are
 * {@code GET /api/incomes/{id}} (a missing, foreign or non-numeric id is the same
 * neutral 404), {@code GET /api/incomes/search} and {@code GET /api/incomes/years}.
 */
@RestController
public class IncomeController {

    private static final String INVALID_IDEMPOTENCY_KEY =
            "La cabecera Idempotency-Key debe ser un UUID válido";

    private final IncomeService incomeService;
    private final IncomeSearchService incomeSearchService;

    public IncomeController(IncomeService incomeService, IncomeSearchService incomeSearchService) {
        this.incomeService = incomeService;
        this.incomeSearchService = incomeSearchService;
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

    /** Search inside the personal incomes (contracts/personal-incomes-api.md). */
    @GetMapping(path = "/api/incomes/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> search(@RequestParam(name = "q", required = false) String term,
            @RequestParam(name = "year", required = false) String year,
            @RequestParam(name = "month", required = false) String month,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size) {
        Long userId = currentUserId();
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(incomeSearchService.search(userId,
                new IncomeSearchService.Query(term, year, month, from, to, page, size)));
    }

    @GetMapping(path = "/api/incomes/years", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listYears() {
        Long userId = currentUserId();
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(new IncomeYearsResponse(incomeSearchService.years(userId)));
    }

    /** Detail of one own income; a non-numeric id is the same neutral 404 (FR-007). */
    @GetMapping(path = "/api/incomes/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> detail(@PathVariable String id) {
        Long userId = currentUserId();
        if (userId == null) {
            return unauthorized();
        }
        long incomeId;
        try {
            incomeId = Long.parseLong(id);
        }
        catch (NumberFormatException invalid) {
            throw new IncomeNotFoundException();
        }
        return ResponseEntity.ok(incomeService.getIncome(userId, incomeId));
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
