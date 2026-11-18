package com.amano.backend.balance;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.amano.backend.income.IncomePeriod;

/**
 * {@code GET /api/balance}: the personal balance of the session user
 * (specs/032-income-management, contracts/personal-incomes-api.md). The period comes
 * from the optional {@code year}, {@code month}, {@code from} and {@code to}
 * parameters, validated by {@link IncomePeriod} (400 with {@code properties.fields}).
 * Read only: the balance is calculated, never stored or edited.
 */
@RestController
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping(path = "/api/balance", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> balance(
            @RequestParam(name = "year", required = false) String year,
            @RequestParam(name = "month", required = false) String month,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to) {
        Long userId = currentUserId();
        if (userId == null) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.UNAUTHORIZED, "Necesitas iniciar sesión para continuar");
            problem.setTitle("No autorizado");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
        }
        return ResponseEntity.ok(
                balanceService.balance(userId, IncomePeriod.of(year, month, from, to)));
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        return principal instanceof Long id ? id : null;
    }
}
