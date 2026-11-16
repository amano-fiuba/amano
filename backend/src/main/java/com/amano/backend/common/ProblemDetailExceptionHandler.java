package com.amano.backend.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import com.amano.backend.common.FieldValidationException;
import com.amano.backend.expense.ExpenseNotFoundException;
import com.amano.backend.expense.IdempotencyConflictException;
import com.amano.backend.friendship.AlreadyFriendsException;
import com.amano.backend.friendship.FriendNotFoundException;
import com.amano.backend.friendship.FriendshipRecipientNotFoundException;
import com.amano.backend.friendship.FriendshipRequestAlreadyRespondedException;
import com.amano.backend.friendship.FriendshipRequestNotFoundException;
import com.amano.backend.friendship.PendingDirectDebtException;
import com.amano.backend.friendship.PendingFriendshipRequestExistsException;
import com.amano.backend.expense.RecurringExpenseFinishedException;
import com.amano.backend.expense.RecurringExpenseNotFoundException;
import com.amano.backend.groupexpense.whistle.DebtNotWhistleableException;
import com.amano.backend.groupexpense.whistle.SimulatedDebtorWhistleException;
import com.amano.backend.groupexpense.whistle.WhistleDeliveryException;
import com.amano.backend.groupexpense.whistle.WhistleNotCreditorException;
import com.amano.backend.groupexpense.whistle.WhistleRateLimitedException;

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

    /** Header whose absence feature 007 turns into an actionable 400 (FR-027). */
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

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

    /** Unknown, redeemed or superseded password reset link (feature 006, FR-012): 400. */
    @ExceptionHandler(PasswordResetTokenInvalidException.class)
    public ProblemDetail handlePasswordResetTokenInvalid(PasswordResetTokenInvalidException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Enlace no válido");
        return problem;
    }

    /** Password reset link past its 1-hour window (feature 006, FR-013): 410. */
    @ExceptionHandler(PasswordResetTokenExpiredException.class)
    public ProblemDetail handlePasswordResetTokenExpired(PasswordResetTokenExpiredException exception) {
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

    /** Reuse of an idempotency key with a different payload (FR-027): 422, no internals. */
    @ExceptionHandler(IdempotencyConflictException.class)
    public ProblemDetail handleIdempotencyConflict(IdempotencyConflictException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
        problem.setTitle("Operación ya registrada");
        problem.setProperty("properties", Map.of("code", "IDEMPOTENCY_KEY_REUSED"));
        return problem;
    }

    /** Missing or foreign personal expense (feature 031, FR-016): one neutral 404. */
    @ExceptionHandler(ExpenseNotFoundException.class)
    public ProblemDetail handleExpenseNotFound(ExpenseNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Gasto no encontrado");
        return problem;
    }

    /** Not a series of the caller (feature 009, FR-011): one neutral 404 for every case. */
    @ExceptionHandler(RecurringExpenseNotFoundException.class)
    public ProblemDetail handleRecurringExpenseNotFound(RecurringExpenseNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Gasto recurrente no encontrado");
        return problem;
    }

    /** Leaving a FINISHED series (feature 009, FR-009): 409 with a machine-readable code. */
    @ExceptionHandler(RecurringExpenseFinishedException.class)
    public ProblemDetail handleRecurringExpenseFinished(RecurringExpenseFinishedException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Gasto recurrente finalizado");
        problem.setProperty("properties", Map.of("code", RecurringExpenseFinishedException.CODE));
        return problem;
    }

    /** Unknown or unverified recipient (feature 011, FR-007): neutral 404. */
    @ExceptionHandler(FriendshipRecipientNotFoundException.class)
    public ProblemDetail handleFriendshipRecipientNotFound(
            FriendshipRecipientNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Recurso no encontrado");
        return problem;
    }

    /** Both accounts are already friends (feature 011, FR-006): 409. */
    @ExceptionHandler(AlreadyFriendsException.class)
    public ProblemDetail handleAlreadyFriends(AlreadyFriendsException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Conflicto");
        return problem;
    }

    /** Pending request already exists between the pair (feature 011, FR-005): 409. */
    @ExceptionHandler(PendingFriendshipRequestExistsException.class)
    public ProblemDetail handlePendingFriendshipRequestExists(
            PendingFriendshipRequestExistsException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Conflicto");
        return problem;
    }

    /** Unknown request or not directed to the session user (feature 012, FR-007/FR-009): neutral 404. */
    @ExceptionHandler(FriendshipRequestNotFoundException.class)
    public ProblemDetail handleFriendshipRequestNotFound(FriendshipRequestNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Recurso no encontrado");
        return problem;
    }

    /** Already answered request, outcome not revealed (feature 012, FR-008): 409. */
    @ExceptionHandler(FriendshipRequestAlreadyRespondedException.class)
    public ProblemDetail handleFriendshipRequestAlreadyResponded(
            FriendshipRequestAlreadyRespondedException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Conflicto");
        return problem;
    }

    /** Not friends or friendship already deleted (feature 016, FR-025): neutral 404. */
    @ExceptionHandler(FriendNotFoundException.class)
    public ProblemDetail handleFriendNotFound(FriendNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Recurso no encontrado");
        return problem;
    }

    /** Pending direct debt (feature 016, FR-011..FR-013): 403, friendship unchanged. */
    @ExceptionHandler(PendingDirectDebtException.class)
    public ProblemDetail handlePendingDirectDebt(PendingDirectDebtException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, exception.getMessage());
        problem.setTitle("Operación bloqueada por deuda pendiente");
        problem.setProperty("properties", Map.of("code", PendingDirectDebtException.CODE));
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

    /**
     * Password change without the right accreditation (feature 005, FR-014): 400
     * with the exact message as detail AND as the currentPassword field message,
     * so the UI highlights the offending input; the stored hash was not touched.
     */
    @ExceptionHandler(WrongCurrentPasswordException.class)
    public ProblemDetail handleWrongCurrentPassword(WrongCurrentPasswordException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Validación fallida");
        problem.setProperty("properties",
                Map.of(FIELDS_PROPERTY, Map.of("currentPassword", exception.getMessage())));
        return problem;
    }

    /** Spec motive 2 — deudas pendientes (FR-010, US4): 403, member remains. */
    @ExceptionHandler(com.amano.backend.groups.PendingDebtsException.class)
    public ProblemDetail handlePendingDebts(com.amano.backend.groups.PendingDebtsException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, exception.getMessage());
        problem.setTitle("Operación bloqueada por deudas pendientes");
        problem.setProperty("properties", Map.of("code", "PENDING_DEBTS"));
        return problem;
    }

    /** 018 FR-003: debt that does not exist or belongs to a group the caller is not in — neutral 404. */
    @ExceptionHandler(com.amano.backend.payment.DebtNotFoundException.class)
    public ProblemDetail handleDebtNotFound(com.amano.backend.payment.DebtNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Deuda no encontrada");
        return problem;
    }

    /** 018 FR-006: payment on a PAID debt — 409 with a machine-readable code. */
    @ExceptionHandler(com.amano.backend.payment.DebtAlreadyPaidException.class)
    public ProblemDetail handleDebtAlreadyPaid(com.amano.backend.payment.DebtAlreadyPaidException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("La deuda ya está pagada");
        problem.setProperty("properties", Map.of("code", com.amano.backend.payment.DebtAlreadyPaidException.CODE));
        return problem;
    }

    /** 022 D6: the debt already has a reported or settled payment — 409. */
    @ExceptionHandler(com.amano.backend.payment.DebtAlreadyHasPaymentException.class)
    public ProblemDetail handleDebtAlreadyHasPayment(
            com.amano.backend.payment.DebtAlreadyHasPaymentException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("La deuda ya tiene un pago registrado");
        problem.setProperty("properties",
                Map.of("code", com.amano.backend.payment.DebtAlreadyHasPaymentException.CODE));
        return problem;
    }

    /** 022 US5: unknown payment, payment of another group or caller outside the group — neutral 404. */
    @ExceptionHandler(com.amano.backend.payment.PaymentNotFoundException.class)
    public ProblemDetail handlePaymentNotFound(com.amano.backend.payment.PaymentNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Pago no encontrado");
        return problem;
    }

    /** 022 US5: already decided, legacy or debt no longer reported — 409 with a code. */
    @ExceptionHandler(com.amano.backend.payment.PaymentNotDecidableException.class)
    public ProblemDetail handlePaymentNotDecidable(
            com.amano.backend.payment.PaymentNotDecidableException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("El pago no se puede decidir");
        problem.setProperty("properties",
                Map.of("code", com.amano.backend.payment.PaymentNotDecidableException.CODE));
        return problem;
    }

    /** 022 D8/US9: BCRA exchange rate unavailable within the 7-day lookback window — 503. */
    @ExceptionHandler(com.amano.backend.groupexpense.GroupExpenseConflictException.class)
    public ProblemDetail handleGroupExpenseConflict(
            com.amano.backend.groupexpense.GroupExpenseConflictException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Conflicto");
        problem.setDetail(exception.getMessage());
        problem.setProperty("properties", Map.of("code", exception.getCode()));
        return problem;
    }

    @ExceptionHandler(com.amano.backend.groupexpense.exchange.ExchangeRateUnavailableException.class)
    public ProblemDetail handleExchangeRateUnavailable(
            com.amano.backend.groupexpense.exchange.ExchangeRateUnavailableException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        problem.setTitle("Cotización no disponible");
        problem.setProperty("properties",
                Map.of("code", com.amano.backend.groupexpense.exchange.ExchangeRateUnavailableException.CODE));
        return problem;
    }

    /** 027 (contracts/currencies-api.md): BCRA catalog unavailable and nothing cached. */
    @ExceptionHandler(com.amano.backend.currency.CurrencyCatalogUnavailableException.class)
    public ProblemDetail handleCurrencyCatalogUnavailable(
            com.amano.backend.currency.CurrencyCatalogUnavailableException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        problem.setTitle("Catálogo de monedas no disponible");
        problem.setProperty("properties",
                Map.of("code", com.amano.backend.currency.CurrencyCatalogUnavailableException.CODE));
        return problem;
    }

    /** Spec motive 5 — admin: primero quitar el rol (US4): 403, exact message. */
    @ExceptionHandler(com.amano.backend.groups.AdminRemovalBlockedException.class)
    public ProblemDetail handleAdminRemovalBlocked(
            com.amano.backend.groups.AdminRemovalBlockedException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, exception.getMessage());
        problem.setTitle("Operación bloqueada");
        problem.setProperty("properties", Map.of("code", "REVOKE_ROLE_FIRST"));
        return problem;
    }

    /** Spec motive 4 — último admin (US5/US10): 403, operation blocked. */
    @ExceptionHandler(com.amano.backend.groups.LastAdminException.class)
    public ProblemDetail handleLastAdmin(com.amano.backend.groups.LastAdminException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, exception.getMessage());
        problem.setTitle("Operación bloqueada");
        problem.setProperty("properties", Map.of("code", "LAST_ADMIN"));
        return problem;
    }

    /** Spec motive 1 — ya es miembro (FR-007, US3): 400 with the exact reason. */
    @ExceptionHandler(com.amano.backend.groups.AlreadyMemberException.class)
    public ProblemDetail handleAlreadyMember(com.amano.backend.groups.AlreadyMemberException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, com.amano.backend.groups.AlreadyMemberException.MESSAGE);
        problem.setTitle("Validación fallida");
        problem.setProperty("properties", Map.of(FIELDS_PROPERTY, exception.getFields()));
        return problem;
    }

    /** Service-level authorization rejection (US2/US3): 403 with the reason. */
    /** 030 FR-003: someone other than the creditor tried to whistle — 403. */
    @ExceptionHandler(WhistleNotCreditorException.class)
    public ProblemDetail handleWhistleNotCreditor(WhistleNotCreditorException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
        problem.setTitle("Operación no permitida");
        problem.setProperty("properties", Map.of("code", WhistleNotCreditorException.CODE));
        return problem;
    }

    /** 030 FR-002/FR-009: settled debt, payment waiting for validation or debtor out of the group — 409. */
    @ExceptionHandler(DebtNotWhistleableException.class)
    public ProblemDetail handleDebtNotWhistleable(DebtNotWhistleableException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("No se puede avisar esta deuda");
        problem.setProperty("properties", Map.of("code", exception.getCode()));
        return problem;
    }

    /** 030 FR-004 (Q3): simulated debtors never receive whistles — 422. */
    @ExceptionHandler(SimulatedDebtorWhistleException.class)
    public ProblemDetail handleSimulatedDebtorWhistle(SimulatedDebtorWhistleException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
        problem.setTitle("No se puede avisar esta deuda");
        problem.setProperty("properties", Map.of("code", SimulatedDebtorWhistleException.CODE));
        return problem;
    }

    /**
     * 030 FR-007: the 24 h limit of the pair was reached — 429 with the
     * {@code Retry-After} header and the same seconds in the body.
     */
    @ExceptionHandler(WhistleRateLimitedException.class)
    public ResponseEntity<ProblemDetail> handleWhistleRateLimited(WhistleRateLimitedException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
        problem.setTitle("Límite de avisos alcanzado");
        problem.setProperty("properties", Map.of("code", WhistleRateLimitedException.CODE,
                "retryAfterSeconds", exception.getRetryAfterSeconds()));
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()))
                .body(problem);
    }

    /** 030 Q10: the mail could not be delivered — 503, without internals. */
    @ExceptionHandler(WhistleDeliveryException.class)
    public ProblemDetail handleWhistleDelivery(WhistleDeliveryException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        problem.setTitle("Aviso no enviado");
        problem.setProperty("properties", Map.of("code", WhistleDeliveryException.CODE));
        return problem;
    }

    @ExceptionHandler(com.amano.backend.groups.GroupPermissionDeniedException.class)
    public ProblemDetail handleGroupPermissionDenied(
            com.amano.backend.groups.GroupPermissionDeniedException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, exception.getMessage());
        problem.setTitle("Operación no permitida");
        return problem;
    }

    /** Adding a registered member outside the friendship relation (US1/US3): one neutral 404. */
    @ExceptionHandler(com.amano.backend.groups.NotFriendException.class)
    public ProblemDetail handleNotFriend(com.amano.backend.groups.NotFriendException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("No encontrado");
        return problem;
    }

    /** The group does not exist or the caller is not a member (US1 detail): one neutral 404. */
    @ExceptionHandler(com.amano.backend.groups.GroupNotFoundException.class)
    public ProblemDetail handleGroupNotFound(com.amano.backend.groups.GroupNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Grupo no encontrado");
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

    /**
     * A path that exists only for other methods (e.g. {@code GET /api/expenses},
     * removed in 031, while {@code POST} stays) answers 405, not the generic 500.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.METHOD_NOT_ALLOWED, "La operación no está disponible para este recurso");
        problem.setTitle("Método no permitido");
        return problem;
    }

    /**
     * Malformed JSON body (feature 007, FR-026/SC-006): a predictable 400 instead
     * of falling through to the generic 500. Generic Spanish message, no internals.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMessageNotReadable(HttpMessageNotReadableException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "La solicitud no es válida");
        problem.setTitle("Validación fallida");
        return problem;
    }

    /**
     * Unsupported request Content-Type: the contract expects Spring's standard 415,
     * so it must not fall through to the generic 500 handler.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "El tipo de contenido de la solicitud no es soportado");
        problem.setTitle("Tipo de contenido no soportado");
        return problem;
    }

    /**
     * Missing required request header (FR-027). Only {@code Idempotency-Key} gets
     * the actionable feature-007 message; any other required header keeps a generic
     * 400 — the same status Spring would produce — instead of a 500. No internal
     * detail is exposed.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingRequestHeader(MissingRequestHeaderException exception) {
        String detail = IDEMPOTENCY_KEY_HEADER.equals(exception.getHeaderName())
                ? "Falta la cabecera Idempotency-Key en la solicitud"
                : "Falta una cabecera obligatoria en la solicitud";
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Validación fallida");
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
