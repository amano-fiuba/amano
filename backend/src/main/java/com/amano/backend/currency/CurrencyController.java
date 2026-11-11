package com.amano.backend.currency;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.amano.backend.groupexpense.GroupExpenseService;
import com.amano.backend.user.Currency;

/**
 * {@code GET /api/currencies}: catalog for the "Ver más" dialog (027, FR-012,
 * contracts/currencies-api.md), and {@code GET /api/currencies/quote}: preview of
 * an amount in pesos (FR-035). BCRA failures are 503s handled by
 * {@code ProblemDetailExceptionHandler}.
 */
@RestController
public class CurrencyController {

    private final CurrencyCatalogProvider catalogProvider;
    private final GroupExpenseService groupExpenseService;

    public CurrencyController(CurrencyCatalogProvider catalogProvider, GroupExpenseService groupExpenseService) {
        this.catalogProvider = catalogProvider;
        this.groupExpenseService = groupExpenseService;
    }

    @GetMapping(path = "/api/currencies", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list() {
        if (currentUserId() == null) {
            return unauthorized();
        }
        List<CurrencyCatalogEntry> currencies = catalogProvider.getCatalog();
        return ResponseEntity.ok(Map.of("currencies", currencies));
    }

    /**
     * {@code GET /api/currencies/quote}: preview of the personal expense amount in
     * pesos (027, FR-035). Same validations and errors as the 022 group quote.
     */
    @GetMapping(path = "/api/currencies/quote", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> quote(@RequestParam(name = "currency", required = false) String currency,
            @RequestParam(name = "date", required = false) String date) {
        if (currentUserId() == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(groupExpenseService.quote(currency, date, Currency.ARS));
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        return principal instanceof Long id ? id : null;
    }

    private static ResponseEntity<ProblemDetail> unauthorized() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Necesitas iniciar sesión para continuar");
        problem.setTitle("No autorizado");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }
}
