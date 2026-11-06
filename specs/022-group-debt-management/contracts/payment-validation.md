# Contract: Payment Validation

**Feature**: Group Debt Management (022)
**Related Specs**: FR-004, D6, D8
**Created**: 2026-11-06

## Purpose

Define the contract for registering, confirming, and rejecting payments against group debts. All operations require an idempotency key. Payments are only accepted as complete amounts (no partial payments).

## Endpoints

### `POST /groups/{groupId}/debts/{debtId}/payments`

**Description**: Register a payment against a debt. Payment amount must equal the outstanding amount exactly (no partial payments).

**Request**: `PaymentRegistrationRequest`

| Field | Type | Description | Required |
|-------|------|-------------|----------|
| `amount` | `BigDecimal` | Payment amount; must equal debt's outstanding exactly | Yes; > 0 |
| `idempotencyKey` | `UUID` | Key for idempotent handling | Yes |

**Response**: `PaymentRegistrationResponse`

| Field | Type | Description |
|-------|------|-------------|
| `paymentId` | `Long` | Created payment identifier |
| `status` | `PaymentStatus` | Initial status: REPORTED |
| `debtStatusAfter` | `DebtStatus` | Debt state after this payment |
| `decidedByUserId` | `Long` | Null (admin decision not yet made) |
| `decidedAt` | `OffsetDateTime` | Null (no decision yet) |

**Behavior**:
- If `group requires validation`: debt status → `PAGO_INFORMADO`; admin must confirm or reject
- If `group does not require validation`: if `amount == outstanding` → debt status → `PAGADO`; if `amount != outstanding` → reject (400)
- If `amount < outstanding`: reject with 400 "El monto del pago debe ser el total de la deuda pendiente"
- If `amount > outstanding`: reject with 400 "El monto del pago excede la deuda pendiente"
- Same `idempotencyKey` reused → returns existing payment result, no duplicate created

### `POST /groups/{groupId}/payments/{paymentId}/confirm`

**Description**: Admin confirms a payment previously registered as `REPORTED`.

**Request**: None (no body needed)

**Response**: `PaymentConfirmationResponse`

| Field | Type | Description |
|-------|------|-------------|
| `paymentId` | `Long` | The payment confirmed |
| `status` | `PaymentStatus` | Now `CONFIRMED` |
| `debtStatusAfter` | `DebtStatus` | Now `PAGADO` (or stays `PAGO_INFORMADO` if not full) |
| `confirmedByUserId` | `Long` | Admin user who confirmed |

**Behavior**:
- Only admin users may call this endpoint
- If caller is not admin → 403 "Solo los administradores pueden confirmar pagos"
- After confirmation: debt status → `PAGADO`; payment status → `CONFIRMED`

### `POST /groups/{groupId}/payments/{paymentId}/reject`

**Description**: Admin rejects a payment previously registered as `REPORTED`.

**Request**: None (no body needed)

**Response**: `PaymentRejectionResponse`

| Field | Type | Description |
|-------|------|-------------|
| `paymentId` | `Long` | The payment rejected |
| `status` | `PaymentStatus` | Now `REJECTED` |
| `debtStatusAfter` | `DebtStatus` | Returns to `PENDIENTE` |
| `rejectedByUserId` | `Long` | Admin user who rejected |

**Behavior**:
- Only admin users may call this endpoint
- If caller is not admin → 403 "Solo los administradores pueden rechazar pagos"
- After rejection: debt status → `PENDIENTE`; payment status → `REJECTED`

### `GET /groups/{groupId}/payments/pending-validation`

**Description**: Get all payments informados awaiting admin decision.

**Response**: `List<PendingPaymentDetail>`

| Field | Type | Description |
|-------|------|-------------|
| `paymentId` | `Long` | Payment identifier |
| `debtId` | `Long` | Origin debt |
| `payerUserId` | `Long` | User who made the payment |
| `amount` | `BigDecimal` | Payment amount |
| `registeredAt` | `OffsetDateTime` | When payment was registered |
| `daysPending` | `Integer` | Days since registration (for UX prioritization) |

**Inclusion**: Only payments with status `REPORTED` (informative, awaiting decision).

## Payment Status Enum (`PaymentStatus`)

| Value | Meaning | Transitions |
|-------|---------|-------------|
| `PENDING` | Payment registered, not yet processed | Initial; becomes `REPORTED` after validation check |
| `REPORTED` | Payment registered (informative); awaiting admin decision | `→ CONFIRMED` (admin confirm), `→ REJECTED` (admin reject) |
| `CONFIRMED` | Admin confirmed the payment | Final state; debt becomes `PAGADO` |
| `REJECTED` | Admin rejected the payment | Final state; debt returns to `PENDIENTE` |

## Behavior Rules

1. **Only full payments allowed**: `amount` must equal exactly `debt.getOutstanding()` (i.e., `amount == debt.getOutstanding()`. No partial payments are accepted. This is a change from spec 018 which allowed partial payments.)

2. **Idempotency**: Same `idempotencyKey` reused → returns existing payment result, no duplicate created. Different key over same debt → new payment attempt.

3. **Concurrent protection**: Pessimistic lock on debt row (`PESSIMISTIC_WRITE`) during payment registration. Same debt cannot have two concurrent successful payments.

4. **Same debt, same key → existing result**: If a payment with the same idempotencyKey already exists for this debt, return the same result without creating a new payment.

5. **Different key, new attempt → 409 if debt already reported/solidada**: Registering a new payment with a different key over a debt already in `PAGO_INFORMADO` or `PAGADO` state → 409 "Esta deuda ya tiene un pago registrado. Use los endpoints de confirmar/rechazar."

6. **Decision required for validated groups**: If group has "Pagos requiere validación" enabled, payment registration leaves debt in `PAGO_INFORMADO`; admin must subsequently confirm or reject. The debt cannot transition to `PAGADO` without admin confirmation.

7. **No validation → auto-complete**: If group does NOT have "Pagos requiere validación", and `amount == outstanding`, debt auto-transitions to `PAGADO` immediately after payment registration.

8. **VENCIDO debts can be paid**: A debt in `VENCIDO` state can be paid; payment registration follows the same rules (full amount only). After payment, debt status transitions based on the validation setting.

9. **Balance recalculation**: After any payment registration/confirmation/rejection, group balances are recalculated (pure function, no separate table needed).

## Error Codes

| Scenario | HTTP Status | Error Message |
|----------|-------------|---------------|
| `amount <= 0` | 400 | "El monto del pago debe ser mayor que cero" |
| `amount != outstanding` (partial or excess) | 400 | "El monto del pago debe ser el total de la deuda pendiente" |
| `debt not found or not in Settleable state` | 404 | "La deuda especificada no puede recibir pagos" |
| `debt already has a payment with same idempotency key` | 200 | Returns existing result (no error) |
| `different key over already-reported debt` | 409 | "Esta deuda ya tiene un pago registrado" |
| `not admin` | 403 | "Solo los administradores pueden confirmar/rechazar pagos" |
| `BCRA API unavailable or no quote within 7 days during expense registration (if multi-currency)` | 503 | "No se pudo obtener la cotización; reintente después o registre en moneda del grupo" |