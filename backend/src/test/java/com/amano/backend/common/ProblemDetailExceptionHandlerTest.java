package com.amano.backend.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Direct unit checks of the RFC 7807 mappings (contracts/auth-api.md "Errores").
 * The 400/409 shapes with properties.fields are also exercised end-to-end by
 * RegistrationIT; this suite covers the remaining mappings in isolation.
 */
class ProblemDetailExceptionHandlerTest {

    private final ProblemDetailExceptionHandler handler = new ProblemDetailExceptionHandler();

    private static MethodParameter dummyParameter() throws NoSuchMethodException {
        var method = DummyEndpoint.class.getDeclaredMethod("endpoint", String.class);
        return new MethodParameter(method, 0);
    }

    static class DummyEndpoint {
        @SuppressWarnings("unused")
        void endpoint(String email) {
            // intentionally empty: only used for MethodParameter metadata
        }
    }

    @Test
    void fieldValidationExceptionMapsTo400WithFields() {
        ProblemDetail problem = handler.handleFieldValidation(
                new FieldValidationException(java.util.Map.of("email", "Ingresa un correo electrónico válido")));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Validación fallida");
        assertThat(problem.getProperties()).containsKey("properties");
        assertThat(problem.getProperties().get("properties")).isEqualTo(
                java.util.Map.of("fields", java.util.Map.of("email", "Ingresa un correo electrónico válido")));
    }

    @Test
    void dataIntegrityViolationMapsTo409WithExactEmailMessage() {
        ProblemDetail problem = handler.handleDataIntegrity(
                new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"uq_users_email\""));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getTitle()).isEqualTo("Conflicto");
        assertThat(problem.getDetail()).isEqualTo("El correo electrónico ya se encuentra registrado");
        assertThat(problem.getProperties()).isEqualTo(java.util.Map.of(
                "properties", java.util.Map.of(
                        "fields", java.util.Map.of("email", "El correo electrónico ya se encuentra registrado"))));
    }

    @Test
    void severalInvalidFieldsAreReportedInOne400Response() throws Exception {
        BindingHolder holder = new BindingHolder();
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(holder, "registerRequest");
        bindingResult.addError(new org.springframework.validation.FieldError(
                "registerRequest", "firstName", "", true, null, null,
                "El nombre debe tener entre 2 y 50 caracteres"));
        bindingResult.addError(new org.springframework.validation.FieldError(
                "registerRequest", "password", "debil", true, null, null,
                "La contraseña debe tener al menos 8 caracteres"));

        ProblemDetail problem = handler.handleValidation(new MethodArgumentNotValidException(
                dummyParameter(), bindingResult));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTitle()).isEqualTo("Validación fallida");
        assertThat(problem.getProperties().get("properties")).isEqualTo(
                java.util.Map.of("fields", java.util.Map.of(
                        "firstName", "El nombre debe tener entre 2 y 50 caracteres",
                        "password", "La contraseña debe tener al menos 8 caracteres")));
    }

    @Test
    void unexpectedErrorNeverExposesInternalDetailsOrFieldProperties() {
        ProblemDetail problem = handler.handleUnexpected(
                new RuntimeException("org.postgresql: relation users does not exist; SQL: insert into"));

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getTitle()).isEqualTo("Error interno");
        assertThat(problem.getDetail()).isEqualTo("Ocurrió un error inesperado. Intenta nuevamente.");
        assertThat(String.valueOf(problem)).doesNotContain("SQL").doesNotContain("postgresql");
        assertThat(problem.getProperties()).isNullOrEmpty(); // Constitution VIII: no leaks
    }

    @Test
    void maxUploadSizeMapsTo413() {
        ProblemDetail problem = handler.handleMaxUploadSize(new MaxUploadSizeExceededException(2_097_153L));

        assertThat(problem.getStatus()).isEqualTo(413);
        assertThat(problem.getTitle()).isEqualTo("Payload demasiado grande");
        // Exact contract text (FR-010/FR-013/T028 backend limit alignment).
        assertThat(problem.getDetail()).isEqualTo("El archivo es demasiado grande (máximo 2 MB)");
    }

    @Test
    void unknownResourceMapsTo404() {
        ProblemDetail problem = handler.handleNoHandlerFound(
                new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "GET", "/nope"));

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getTitle()).isEqualTo("Recurso no encontrado");
        assertThat(problem.getDetail()).isEqualTo("No existe el recurso solicitado");
    }

    @Test
    void unexpectedErrorsMapToGeneric500WithoutInternalDetails() {
        ProblemDetail problem = handler.handleUnexpected(new RuntimeException("SQL: internal leak"));

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getTitle()).isEqualTo("Error interno");
        assertThat(problem.getDetail()).doesNotContain("SQL");
        assertThat(String.valueOf(problem)).doesNotContain("internal leak");
    }

    @Test
    void beanValidationErrorsMapToPerFieldMessages() throws Exception {
        BindingHolder holder = new BindingHolder();
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(holder, "registerRequest");
        bindingResult.addError(new org.springframework.validation.FieldError(
                "registerRequest", "firstName", "A", true, null, null,
                "El nombre debe tener entre 2 y 50 caracteres"));

        ProblemDetail problem = handler.handleValidation(new MethodArgumentNotValidException(
                dummyParameter(), bindingResult));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTitle()).isEqualTo("Validación fallida");
        assertThat(problem.getProperties().get("properties"))
                .isEqualTo(java.util.Map.of("fields",
                        java.util.Map.of("firstName", "El nombre debe tener entre 2 y 50 caracteres")));
    }

    /** Minimal target object for a concrete BindingResult. */
    static class BindingHolder {
        private String firstName;

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }
    }

    @Test
    void handlerMethodValidationMapsParameterNameToMessage() throws Exception {
        MethodParameter parameter = dummyParameter();
        ParameterValidationResult result = new ParameterValidationResult(
                parameter, "",
                List.<MessageSourceResolvable>of(new org.springframework.context.support.DefaultMessageSourceResolvable(
                        null, "Ingresa un correo electrónico válido")),
                null, null, null, null);
        MethodValidationResult validation = org.mockito.Mockito.mock(MethodValidationResult.class);
        org.mockito.Mockito.when(validation.getParameterValidationResults()).thenReturn(List.of(result));

        ProblemDetail problem = handler.handleValidation(
                new HandlerMethodValidationException(validation));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getProperties().get("properties")).isEqualTo(
                java.util.Map.of("fields",
                        java.util.Map.of("email", "Ingresa un correo electrónico válido")));
    }
}
