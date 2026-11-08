# Contract: Payment Validation

**Feature**: Group Debt Management (022)
**Related Specs**: FR-004, FR-005, FR-006, D6, D8
**Created**: 2026-11-06
**Updated**: 2026-11-07 (product decision of the same day: **partial payments are allowed**)

> **Implementation status (2026-11-07).** The backend implements this contract (tasks T079, T080, T085; migration V25). The UI still sends the whole outstanding balance and does not let the user edit the amount (task T082).

## Purpose

Define the contract for registering, confirming and rejecting payments against group debts. A payment can be **partial or total**: its amount is greater than zero and not greater than the debt's outstanding balance (`amount − paidAmount`). A registration is idempotent by key.

Common conventions:

- Authentication: `amano_auth` cookie. Without a session → `401`.
- Amounts: canonical decimal text with a dot and two decimals (`"3333.33"`). Responses may send amounts as JSON numbers.
- Errors: `ProblemDetail`; validation errors carry `properties.fields` and business conflicts a `properties.code`.
- The **UI** never shows the enum values; it uses the Spanish labels of `debtStatusLabels.js` (`Pendiente`, `Pago parcial`, `Pago informado`, `Pagada`, `Pago verificado`, `Vencida`).

## Debt states used here (`DebtStatus`)

| Enum | UI label | Settled? | Payable? |
|------|----------|----------|----------|
| `PENDING` | Pendiente | No | Yes |
| `PARTIALLY_PAID` | Pago parcial | No (a balance remains) | Yes (up to the remaining balance) |
| `PAYMENT_REPORTED` | Pago informado | **No** (waits for an admin; `paidAmount` is untouched) | No: only one payment can wait for validation at a time |
| `PAID` | Pagada | Yes | No |
| `VERIFIED` | Pago verificado | Yes (equivalent to `PAID`) | No |
| `OVERDUE` | Vencida | No (still counts in the balance) | **Yes** (a partial payment turns it into `PARTIALLY_PAID` — decision to confirm) |

## Endpoints

### `POST /api/groups/{groupId}/debts/{debtId}/payments`

**Description**: Registers a partial or total payment of a debt. The payer, receiver, group and currency always come from the debt; the payment date is the server date (`Clock`), so the request carries no date.

**Request body** (`RegisterDebtPaymentRequest`)

| Field | Type | Description | Required |
|-------|------|-------------|----------|
| `amount` | text | `> 0` and `<=` the debt's `outstanding`; the UI offers the outstanding balance by default and lets the user edit it | Yes |
| `idempotencyKey` | UUID text | Key of the attempt (in the **body**, not in a header; the 018 endpoint `POST /api/payments` keeps the `Idempotency-Key` header) | Yes |

Unknown properties are rejected with `400` (`"No se permite informar este campo"`).

**Who may register**: the debtor, the creditor or an administrator of the group (`403` `"Solo quien debe, quien cobra o un administrador pueden registrar este pago"` otherwise). Non-members and debts of another group get the neutral `404`.

**Response**: `PaymentRegistrationResponse` — `201` with `Location: /api/groups/{groupId}/payments/{paymentId}`; an idempotent replay answers `200`.

| Field | Type | Description |
|-------|------|-------------|
| `paymentId` | `Long` | Payment identifier |
| `status` | `PaymentStatus` | `REPORTED` if the group requires validation, `CONFIRMED` otherwise |
| `debtStatusAfter` | `DebtStatus` | Group with validation: `PAYMENT_REPORTED`. Group without validation: `PARTIALLY_PAID` if a balance remains, `PAID` if it reaches zero |
| `decidedByUserId` | `Long` | `null` until an administrator decides |
| `decidedAt` | `OffsetDateTime` | `null` until an administrator decides |

**Behavior**:

- Group **does not require** validation: the payment is `CONFIRMED` and added to `paidAmount` at once; the debt becomes `PARTIALLY_PAID` if a balance remains or `PAID` if it reaches zero (from `OVERDUE`, a partial payment also gives `PARTIALLY_PAID`).
- Group **requires** validation ("Pagos requiere validación"): the payment is `REPORTED` and the debt `PAYMENT_REPORTED`, **without touching `paidAmount`**; an administrator must confirm or reject.
- While a debt has a `REPORTED` payment, another payment on it answers `409` (see the table below). Once it is decided the debt accepts new payments again.
- `amount` greater than `outstanding` → `400`, field `amount`: `"El monto del pago excede la deuda pendiente"`. (The check uses the outstanding balance at the moment of the request, after the row lock, so a second concurrent payment that no longer fits also gets this `400`.)
- Invalid, zero or negative `amount` → `400` with the amount messages of 007; missing/invalid key → `400` field `idempotencyKey` (`"Indicá la clave de idempotencia"` / `"La clave de idempotencia debe ser un UUID válido"`).
- Same `idempotencyKey` on the same debt and amount → `200` with the existing result, no duplicate.
- Same key on the same debt with another amount, or a key the caller already used on another debt → `422` `"La operación ya fue registrada con datos distintos. Reintentá el guardado."` (existing 018/022 text of `IdempotencyConflictException`).
- A debt that is settled (`PAID`, `VERIFIED`) or has a `REPORTED` payment → `409`, `properties.code = DEBT_ALREADY_HAS_PAYMENT`. Text for the `REPORTED` case: `"Esta deuda ya tiene un pago esperando validación: esperá a que un administrador lo decida"`; for a settled debt: `"Esta deuda ya tiene un pago registrado"` (inherited endpoint: `"La deuda ya está pagada"`, code `DEBT_ALREADY_PAID`).

### `POST /api/groups/{groupId}/payments/{paymentId}/confirm`

**Description**: An administrator confirms a payment in `REPORTED`.

**Request**: no body.

**Response** `200`: `PaymentConfirmationResponse` — `paymentId`, `status` (`CONFIRMED`), `debtStatusAfter` (`VERIFIED` if the payment leaves the balance at zero, `PARTIALLY_PAID` if a balance remains; the payment amount is added to `paidAmount` now), `confirmedByUserId`.

### `POST /api/groups/{groupId}/payments/{paymentId}/reject`

**Description**: An administrator rejects a payment in `REPORTED`.

**Request**: no body.

**Response** `200`: `PaymentRejectionResponse` — `paymentId`, `status` (`REJECTED`), `debtStatusAfter` (the debt returns to its previous state from its unchanged `paidAmount`: `PENDING` or `PARTIALLY_PAID`; a debt that had been overdue returns as `PENDING` and the overdue job marks it `OVERDUE` again on its next run), `rejectedByUserId`.

**Errors of confirm and reject**

| Status | When | Message |
|--------|------|---------|
| `403` | The caller is a non-admin member | `"Solo los administradores pueden confirmar pagos"` / `"Solo los administradores pueden rechazar pagos"` |
| `404` | Unknown payment, payment of another group or non-member (neutral) | `"No existe el pago solicitado o no sos miembro del grupo"` |
| `409` | The payment is not `REPORTED` (already decided) — `properties.code = PAYMENT_NOT_DECIDABLE` | `"Este pago no está pendiente de validación"` |

### `GET /api/groups/{groupId}/payments/pending-validation`

**Description**: Reported payments awaiting an administrator's decision, oldest first. Read-only. (Added with T075, so the UI of "Pagos por validar" can list them.)

**Access**: administrators only. A non-admin member gets `403` `"Solo los administradores pueden ver los pagos por validar"`; non-members the neutral `404`; no session `401`.

**Response** `200`: `List<PendingPaymentDetail>`

| Field | Type | Description |
|-------|------|-------------|
| `paymentId` | `Long` | Payment identifier |
| `debtId` | `Long` | Origin debt |
| `expenseId` | `Long` | Origin expense (for the "Ver gasto" link) |
| `payerUserId` | `Long` | Paying user; `null` when the payer is a simulated profile |
| `payerName` | `String` | Name of who pays |
| `receiverName` | `String` | Name of who receives |
| `amount` | `BigDecimal` | Payment amount (scale 2) |
| `currency` | `String` | Debt currency |
| `registeredAt` | `OffsetDateTime` | When it was registered |
| `daysPending` | `Integer` | Whole days since registration (never negative) |

**Inclusion**: only payments with status `REPORTED` (at most one per debt). `amount` is the amount of that payment, which can be lower than the debt.

## Payment status enum (`PaymentStatus`)

| Value | Meaning | Transitions |
|-------|---------|-------------|
| `PENDING` | Reserved; not produced by the current flows | — |
| `REPORTED` | Registered, awaiting an administrator's decision | `→ CONFIRMED` (admin confirms), `→ REJECTED` (admin rejects) |
| `CONFIRMED` | Accepted (no validation required, or confirmed by an admin) | Final |
| `REJECTED` | Rejected by an administrator | Final; the debt can receive a new payment |

## Behavior rules

1. **Partial payments are allowed**: `0 < amount <= outstanding`. The sum of the `CONFIRMED` payments of a debt never exceeds its `amount`.
2. **Idempotency**: see the registration behavior above. One key per attempt; a client retry after a network failure reuses the key.
3. **Concurrency**: the debt row is taken with `PESSIMISTIC_WRITE` while registering, confirming or rejecting, so concurrent registrations over the same debt are applied one after the other; each one is validated against the balance left by the previous one. The partial unique index `uq_payments_reported_debt` (V25, `WHERE status = 'REPORTED'`) is the database backstop for "one payment waiting for validation per debt"; several `CONFIRMED` payments can coexist.
4. **Validated groups**: the debt cannot receive the amount (`paidAmount`) without an administrator's confirmation, and `PAYMENT_REPORTED` is **not settled**: it still counts, with its full outstanding balance, in the balances and the settlement proposal.
5. **No validation → immediate effect**: the amount is added to `paidAmount` and the debt becomes `PARTIALLY_PAID` or `PAID`.
6. **Overdue debts can be paid**: an `OVERDUE` debt follows the same rules; a partial payment makes it `PARTIALLY_PAID` (decision to confirm, see plan.md, pending decision 7).
7. **Balances** are a pure function of the debts and use each debt's outstanding balance (`amount − paidAmount`): after any registration, confirmation or rejection the next query reflects it, and the balances of a currency still add up to zero.
8. **History**: registering records `PAYMENT_REGISTERED`; confirming `PAYMENT_CONFIRMED`; rejecting `PAYMENT_REJECTED`.
9. **Inherited 018 flow**: `POST /api/payments` (with the `Idempotency-Key` header and a payment date) follows the same rules: it also accepts partial payments, respects the group's "Pagos requieren validación" (payment `REPORTED` and debt `PAYMENT_REPORTED` without touching `paidAmount`, or `CONFIRMED` and added at once), answers `409` while a payment waits (same text as above) and adds to `paidAmount` through the same domain operations, so both flows produce the same states and balances. Its response carries the additive field `status` (`CONFIRMED` or `REPORTED`). Its payments are written with `legacy = true`, an origin marker with no functional role: a `REPORTED` payment of this flow is listed in the queue and decided like any other. Payments already stored are never modified.

## Error summary

| Scenario | HTTP | Message |
|----------|------|---------|
| `amount` ≤ 0 or malformed | 400 | Amount messages of 007 (field `amount`) |
| `amount` greater than the outstanding balance | 400 | `"El monto del pago excede la deuda pendiente"` |
| Caller is not debtor, creditor or admin | 403 | `"Solo quien debe, quien cobra o un administrador pueden registrar este pago"` |
| Debt/payment not found, other group, non-member | 404 | `"No existe la deuda solicitada o no sos miembro del grupo"` / `"No existe el pago solicitado o no sos miembro del grupo"` |
| Debt settled, or with a payment waiting for validation | 409 | `"Esta deuda ya tiene un pago registrado"` (own voseo text for the waiting case, see above) |
| Payment already decided | 409 | `"Este pago no está pendiente de validación"` |
| Same key with other data | 422 | `"La operación ya fue registrada con datos distintos. Reintentá el guardado."` |
| Admin-only action by a non-admin | 403 | See each endpoint |
