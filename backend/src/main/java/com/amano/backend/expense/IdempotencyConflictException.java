package com.amano.backend.expense;

/**
 * The same {@code Idempotency-Key} was already used by this user with a different
 * normalized payload (FR-027, US3-4). The row of the original operation is kept
 * untouched and the request is answered with {@code 422}.
 */
public class IdempotencyConflictException extends RuntimeException {

    public static final String MESSAGE =
            "La operación ya fue registrada con datos distintos. Reintentá el guardado.";

    public IdempotencyConflictException() {
        super(MESSAGE);
    }
}
