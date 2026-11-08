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
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/currencies}: catalog for the "Ver más" dialog (027, FR-012,
 * contracts/currencies-api.md). A BCRA failure without cache is a 503 handled
 * by {@code ProblemDetailExceptionHandler}.
 */
@RestController
public class CurrencyController {

    private final CurrencyCatalogProvider catalogProvider;

    public CurrencyController(CurrencyCatalogProvider catalogProvider) {
        this.catalogProvider = catalogProvider;
    }

    @GetMapping(path = "/api/currencies", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list() {
        if (currentUserId() == null) {
            return unauthorized();
        }
        List<CurrencyCatalogEntry> currencies = catalogProvider.getCatalog();
        return ResponseEntity.ok(Map.of("currencies", currencies));
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
