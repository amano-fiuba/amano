package com.amano.backend.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
 * 018 T027 (US1): the controller branches the HTTP tests cannot reach (the
 * defensive 401 without a numeric principal) and the idempotency race resolved
 * after the unique index fails (plan.md §Flujo, paso 12).
 */
class PaymentControllerTest {

    private static final String KEY = "7c1d2e3f-4a5b-4c6d-8e7f-9a0b1c2d3e4f";

    private PaymentService service;
    private PaymentController controller;
    private final RegisterPaymentRequest request = new RegisterPaymentRequest();

    @BeforeEach
    void setUp() {
        service = mock(PaymentService.class);
        controller = new PaymentController(service);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAs(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private static PaymentResponse payment(long id) {
        return new PaymentResponse(id, 3L, 12L, 31L, "1000.00", "ARS", "2026-11-05", null, null, null, null, null,
                "CONFIRMED");
    }

    @Test
    void withoutAuthenticationItAnswers401WithoutCallingTheService() {
        ResponseEntity<?> response = controller.register(KEY, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo(Map.of("detail", "Debés iniciar sesión para continuar"));
        verifyNoInteractions(service);
    }

    @Test
    void aPrincipalThatIsNotAUserIdIsTreatedAsUnauthenticated() {
        authenticateAs("anonymousUser");

        assertThat(controller.register(KEY, request).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(service);
    }

    @Test
    void aNewPaymentAnswers201WithItsLocation() {
        authenticateAs(15L);
        when(service.register(eq(15L), any(), any())).thenReturn(new PaymentService.Registration(payment(54L), true));

        ResponseEntity<?> response = controller.register(KEY, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).hasToString("/api/payments/54");
        assertThat(response.getBody()).isEqualTo(payment(54L));
    }

    @Test
    void aReplayAnswers200() {
        authenticateAs(15L);
        when(service.register(eq(15L), any(), any())).thenReturn(new PaymentService.Registration(payment(54L), false));

        assertThat(controller.register(KEY, request).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void theUniqueIndexRaceIsResolvedByReReadingTheWinner() {
        authenticateAs(15L);
        UUID key = UUID.fromString(KEY);
        when(service.register(15L, key, request)).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(service.resolveConcurrentRegistration(15L, key, request))
                .thenReturn(new PaymentService.Registration(payment(54L), false));

        ResponseEntity<?> response = controller.register(KEY, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(payment(54L));
    }

    @Test
    void anInvalidOrMissingIdempotencyKeyIsAFieldError() {
        authenticateAs(15L);

        assertThatThrownBy(() -> controller.register("not-a-uuid", request))
                .isInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> controller.register(null, request))
                .isInstanceOf(FieldValidationException.class);
        verifyNoInteractions(service);
    }
}
