package com.amano.backend.expense;

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
 * Statistics of the session user's expenses for a month or a whole year
 * (specs/032-estadisticas-gastos/contracts/expense-statistics-api.md). Under
 * {@code /api/expenses/**}, so {@code SecurityConfig} already requires a verified
 * account (FR-019, plan D1); validation errors (400) are mapped by
 * {@code ProblemDetailExceptionHandler}.
 */
@RestController
public class ExpenseStatisticsController {

    private final ExpenseStatisticsService statisticsService;

    public ExpenseStatisticsController(ExpenseStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping(path = "/api/expenses/statistics", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> statistics(@RequestParam(name = "year", required = false) String year,
            @RequestParam(name = "month", required = false) String month) {
        Long userId = currentUserId();
        if (userId == null) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.UNAUTHORIZED, "Necesitas iniciar sesión para continuar");
            problem.setTitle("No autorizado");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
        }
        return ResponseEntity.ok(statisticsService.statistics(userId, year, month));
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        return principal instanceof Long id ? id : null;
    }
}
