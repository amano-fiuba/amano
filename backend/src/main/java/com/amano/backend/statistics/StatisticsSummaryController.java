package com.amano.backend.statistics;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Statistics summary of the session user for one month
 * (specs/034-income-statistics/contracts/statistics-summary-api.md). Under
 * {@code /api/stats/**}, which {@code SecurityConfig} restricts to verified accounts;
 * validation errors (400) are mapped by {@code ProblemDetailExceptionHandler}.
 */
@RestController
public class StatisticsSummaryController {

    private final StatisticsSummaryService summaryService;

    public StatisticsSummaryController(StatisticsSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping(path = "/api/stats/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> summary(@RequestParam(name = "year", required = false) String year,
            @RequestParam(name = "month", required = false) String month) {
        Long userId = currentUserId();
        if (userId == null) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.UNAUTHORIZED, "Necesitas iniciar sesión para continuar");
            problem.setTitle("No autorizado");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
        }
        return ResponseEntity.ok(summaryService.summary(userId, year, month));
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        return principal instanceof Long id ? id : null;
    }
}
