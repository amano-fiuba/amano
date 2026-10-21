package com.amano.backend.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import com.amano.backend.common.FieldValidationException;

/**
 * Global REST error handling following RFC 7807 ProblemDetail, as defined in
 * specs/001-user-registration/contracts/auth-api.md ("Errores").
 * Never exposes stacktraces, SQL or credentials (constitution VIII).
 */
@RestControllerAdvice
public class ProblemDetailExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailExceptionHandler.class);

    /** Message shown when the unique index uq_users_email detects an account race (FR-005, SC-004). */
    private static final String EMAIL_ALREADY_REGISTERED = "El correo electrónico ya se encuentra registrado";

    /** Properties key carrying the per-field validation messages map (contract "Errores"). */
    public static final String FIELDS_PROPERTY = "fields";

    /**
     * Machine-readable indicator of the 403 raised when a pending account tries to
     * log in (feature 003, FR-014): the real login feature uses it to offer the
     * resend link (contracts/email-verification-api.md).
     */
    public static final String RESEND_VERIFICATION_ACTION = "RESEND_VERIFICATION";

    @ExceptionHandler({ MethodArgumentNotValidException.class, HandlerMethodValidationException.class })
    public ProblemDetail handleValidation(Exception exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (exception instanceof MethodArgumentNotValidException notValid) {
            for (FieldError fieldError : notValid.getBindingResult().getFieldErrors()) {
                fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
            }
        }
        else if (exception instanceof HandlerMethodValidationException methodValidation) {
            methodValidation.getParameterValidationResults().forEach(result ->
                    result.getResolvableErrors().forEach(error -> fields.putIfAbsent(
                            result.getMethodParameter().getParameterName(),
                            error.getDefaultMessage() == null ? "" : error.getDefaultMessage())));
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "La solicitud contiene campos inválidos");
        problem.setTitle("Validación fallida");
        problem.setProperty("properties", Map.of(FIELDS_PROPERTY, fields));
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException exception) {
        log.warn("Data integrity violation: {}", exception.getMostSpecificCause().getMessage());
        return emailConflict();
    }

    /**
     * Service-level duplicate detection: a verified account already owns the email
     * (feature 003, FR-016). Same 409 shape as the unique-index race above.
     */
    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ProblemDetail handleEmailAlreadyRegistered(EmailAlreadyRegisteredException exception) {
        log.info("Registration attempt for an already verified email (FR-016)");
        return emailConflict();
    }

    private static ProblemDetail emailConflict() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, EMAIL_ALREADY_REGISTERED);
        problem.setTitle("Conflicto");
        problem.setProperty("properties", Map.of(FIELDS_PROPERTY, Map.of("email", EMAIL_ALREADY_REGISTERED)));
        return problem;
    }

    /**
     * Redemption of an unknown/tampered link (feature 003, FR-008): 400 with the
     * neutral message that never reveals whether the account exists.
     */
    @ExceptionHandler(VerificationTokenInvalidException.class)
    public ProblemDetail handleVerificationTokenInvalid(VerificationTokenInvalidException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Enlace no válido");
        return problem;
    }

    /** Redemption of an already consumed link (feature 003, FR-007): 409, no session. */
    @ExceptionHandler(VerificationTokenUsedException.class)
    public ProblemDetail handleVerificationTokenUsed(VerificationTokenUsedException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Enlace ya utilizado");
        return problem;
    }

    /** Redemption past the 24 h window (feature 003, FR-006): 410, leads to resend. */
    @ExceptionHandler(VerificationTokenExpiredException.class)
    public ProblemDetail handleVerificationTokenExpired(VerificationTokenExpiredException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.GONE, exception.getMessage());
        problem.setTitle("Enlace expirado");
        return problem;
    }

    /**
     * Provisional login stub with bad credentials (feature 003, FR-014): wrong
     * password or unknown account share the 401 of the session filter of 001, so
     * the existence of the account is never disclosed.
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, exception.getMessage());
        problem.setTitle("No autorizado");
        return problem;
    }

    /**
     * Provisional login stub with valid credentials on a PENDING account (feature
     * 003, FR-004/FR-014): 403 with the exact message plus the machine-readable
     * resend indicator the real login feature will consume. No session is issued.
     */
    @ExceptionHandler(UnverifiedAccountException.class)
    public ProblemDetail handleUnverifiedAccount(UnverifiedAccountException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, exception.getMessage());
        problem.setTitle("Cuenta no verificada");
        problem.setProperty("properties", Map.of("action", RESEND_VERIFICATION_ACTION));
        return problem;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.PAYLOAD_TOO_LARGE, "El archivo es demasiado grande (máximo 2 MB)");
        problem.setTitle("Payload demasiado grande");
        return problem;
    }

    /** Service-level validations after normalization (Q3/Q4): same 400 shape (FR-018). */
    @ExceptionHandler(FieldValidationException.class)
    public ProblemDetail handleFieldValidation(FieldValidationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Validación fallida");
        problem.setProperty("properties", Map.of(FIELDS_PROPERTY, exception.getFields()));
        return problem;
    }

    /** Framework 404s must not be swallowed by the generic 500 handler (contract "Errores"). */
    @ExceptionHandler({ NoHandlerFoundException.class, NoResourceFoundException.class })
    public ProblemDetail handleNoHandlerFound(Exception exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "No existe el recurso solicitado");
        problem.setTitle("Recurso no encontrado");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unexpected error", exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado. Intenta nuevamente.");
        problem.setTitle("Error interno");
        return problem;
    }
}
