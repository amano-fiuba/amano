package com.amano.backend.groupexpense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.amano.backend.common.FieldValidationException;

/**
 * 017 T015 (US1): the controller branches that the HTTP tests cannot reach
 * because Spring Security answers first — the defensive 401 without a numeric
 * principal — and the idempotency race resolved after the unique index fails
 * (plan.md §Alta de un gasto, paso 9).
 */
class GroupExpenseControllerTest {

    private static final String KEY = "6f1c2a3e-9d84-4b8a-a1c0-6d5e4f3a2b10";

    private GroupExpenseService service;
    private GroupExpenseController controller;
    private final CreateGroupExpenseRequest request = new CreateGroupExpenseRequest();

    @BeforeEach
    void setUp() {
        service = mock(GroupExpenseService.class);
        controller = new GroupExpenseController(service);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAs(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private static GroupExpenseResponse expense(long id) {
        return new GroupExpenseResponse(id, 3L, "100.00", "ARS", "2026-11-05", null, null, null, null,
                List.of(), List.of());
    }

    @Test
    void withoutAuthenticationBothEndpointsAnswer401WithoutCallingTheService() {
        ResponseEntity<?> create = controller.create(3L, KEY, request);
        ResponseEntity<?> get = controller.get(3L, 12L);

        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get.getBody()).isEqualTo(Map.of("detail", "Debés iniciar sesión para continuar"));
        verifyNoInteractions(service);
    }

    @Test
    void aPrincipalThatIsNotAUserIdIsTreatedAsUnauthenticated() {
        authenticateAs("anonymousUser");

        assertThat(controller.get(3L, 12L).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(service);
    }

    @Test
    void theUniqueIndexRaceIsResolvedByReReadingTheWinner() {
        authenticateAs(7L);
        UUID key = UUID.fromString(KEY);
        when(service.create(7L, 3L, key, request)).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(service.resolveConcurrentRegistration(7L, 3L, key, request))
                .thenReturn(new GroupExpenseService.Registration(expense(12L), false));

        ResponseEntity<?> response = controller.create(3L, KEY, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expense(12L));
        verify(service).resolveConcurrentRegistration(eq(7L), eq(3L), eq(key), any());
    }

    @Test
    void aNewExpenseAnswers201WithItsLocation() {
        authenticateAs(7L);
        when(service.create(eq(7L), eq(3L), any(), any()))
                .thenReturn(new GroupExpenseService.Registration(expense(12L), true));

        ResponseEntity<?> response = controller.create(3L, KEY, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).hasToString("/api/groups/3/expenses/12");
    }

    @Test
    void anInvalidIdempotencyKeyIsAFieldError() {
        authenticateAs(7L);

        assertThatThrownBy(() -> controller.create(3L, "not-a-uuid", request))
                .isInstanceOf(FieldValidationException.class);
        verifyNoInteractions(service);
    }
}
