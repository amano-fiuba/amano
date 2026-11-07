# Data Model: Group Debt Management

**Feature**: Group Debt Management
**Based on**: Feature specification specs/022-group-debt-management/spec.md
**Research**: research.md findings
**Created**: 2026-11-06

## Entity: Gasto (GroupExpense)

**Source**: Existing entity `backend/src/main/java/com/amano/backend/groupexpense/GroupExpense.java`
**Extended for**: 022 feature requirements

| Field | Type | Nullable | Description | Constraints |
|-------|------|----------|-------------|-------------|
| `id` | `Long` | No | Primary key | `GenerationType.IDENTITY` |
| `groupId` | `Long` | No | Foreign key to group | - |
| `createdByUserId` | `Long` | No | User who registered the expense | - |
| `payerUserId` | `Long` | No | User who paid the expense | - |
| `amount` | `BigDecimal` | No | Original expense amount | `precision=11, scale=2`; > 0 |
| `currency` | `Currency` | No | Currency enum (ARS, USD, EUR) | Converted via `CurrencyConversion` JPA converter |
| `expenseDate` | `LocalDate` | No | Date of the expense | Not future; format `yyyy-MM-dd` |
| `description` | `String` | Yes | Optional description | Max 200 chars |
| `idempotencyKey` | `UUID` | No | For concurrent registration safety | Unique per (creator, key) |
| `montoConvertido` | `BigDecimal` | Yes | Converted amount to group's primary currency | `precision=11, scale=2`; null if same currency |
| `tasaAplicada` | `BigDecimal` | Yes | Exchange rate applied | `precision=11, scale=6`; null if same currency |
| `fechaCotizacion` | `LocalDate` | Yes | Date the rate was effective from | null if same currency |
| `rateSource` | `String` | Yes | Identifier of the rate source (e.g., "BCRA") | null if same currency |

**Relationships**:
- `1:N` with `GroupExpenseShare` (shares/participants)
- `1:N` with `Debt` (one debt per non-payer participant)
- `1:N` with `Payment` (payments registered against debts)

** invariants**:
- `amount > 0`
- `currency in {ARS, USD, EUR}`
- `montoConvertido` and `tasaAplicada` and `fechaCotizacion` and `rateSource` are set only when `currency` differs from group's primary currency
- `idempotencyKey` uniqueness enforced per `(createdByUserId, idempotencyKey)`
- `payerUserId` and every registered participant must be active members of the group; simulated participants must belong to the group. The server validates this (D11)
- `payerUserId` may differ from `createdByUserId`; both are stored separately (D11)
- Debts are always owed to the payer: `debt.creditorUserId = expense.payerUserId` (D11)
- If the expense currency differs from the group currency and no quote is available, the
  expense is **rejected with 503 and not saved**: it is never registered using the original
  currency as a fallback

**Quote source (022 D8)**:
- `ExchangeRateProvider` is implemented by `BcraExchangeRateClient` (US9) using the public
  BCRA Estadísticas Cambiarias v1.0 API; no credentials or registration.
- USD→ARS and EUR→ARS come directly from the BCRA; USD↔EUR is crossed through ARS with
  `BigDecimal`, never floating point.
- If the expense date has no quote (weekend or holiday), the last business day's quote is
  used, up to 7 days back. If none exists, the expense is rejected with 503.
- `rateSource = 'BCRA'` and `fechaCotizacion` is the date of the quote actually used, not
  the expense date.
- Tests never call the real BCRA API: they use a double of `ExchangeRateProvider`.

## Entity: Deuda (Debt)

**Source**: Existing entity `backend/src/main/java/com/amano/backend/groupexpense/Debt.java`
**Extended for**: 022 feature requirements (new state machine)

| Field | Type | Nullable | Description | Constraints |
|-------|------|----------|-------------|-------------|
| `id` | `Long` | No | Primary key | `GenerationType.IDENTITY` |
| `groupId` | `Long` | No | Foreign key to group | - |
| `expenseId` | `Long` | No | Origin expense | - |
| `shareId` | `Long` | No | Unique share identifier | `unique=true` |
| `debtorType` | `ParticipantType` | No | Who owes: REGISTERED or SIMULATED | - |
| `debtorUserId` | `Long` | Conditional | User who owes (if `debtorType=REGISTERED`) | Null if `SIMULATED` |
| `debtorSimulatedProfileId` | `Long` | Conditional | Simulated profile who owes (if `debtorType=SIMULATED`) | Null if `REGISTERED` |
| `debtorName` | `String` | No | Name of the debtor (participant name) | - |
| `creditorUserId` | `Long` | No | User who is owed (usually the payer) | - |
| `currency` | `Currency` | No | Currency of the debt | From originating expense |
| `amount` | `BigDecimal` | No | Total amount owed | `precision=11, scale=2`; > 0 |
| `paidAmount` | `BigDecimal` | No | Amount already paid | `precision=11, scale=2`; ≥ 0; ≤ amount |
| `status` | `DebtStatus` | No | Current state of the debt | See state machine below |
| `createdAt` | `OffsetDateTime` | No | Auto-created | Hibernate `@CreationTimestamp` |
| `updatedAt` | `OffsetDateTime` | No | Auto-updated | Hibernate `@UpdateTimestamp` |

**Debt Status Machine** (`DebtStatus` enum, six persisted values):

| Status | Meaning | Transition Rules |
|--------|---------|------------------|
| `PENDING` | Initial state; no payments yet | Default on debt creation. Can become `PAID`, `PAYMENT_REPORTED` or `OVERDUE` |
| `PARTIALLY_PAID` | Legacy state from 018; read-only | Never produced by 022. A full payment of the remaining balance can take it to `PAID` or `PAYMENT_REPORTED`; it never becomes `OVERDUE` |
| `PAYMENT_REPORTED` | Full payment reported, awaiting an admin decision | From `PENDING`, `OVERDUE` or `PARTIALLY_PAID` when the group requires validation. Admin confirm → `VERIFIED`; admin reject → `PENDING` |
| `PAID` | Full payment received and settled | From `PENDING`, `OVERDUE` or `PARTIALLY_PAID` when the group does not require validation. Final state |
| `VERIFIED` | Admin confirmed the reported payment | From `PAYMENT_REPORTED`. Final state; equivalent to `PAID` for balance calculations (Q7) |
| `OVERDUE` | Debt past its due date | Only from `PENDING`, when the group has `debtDueDays` and `currentDate > expenseDate.plusDays(debtDueDays)`. Remains payable |

**Key Methods** (existing + new):
- `Debt.pendingFor(expense, share)` → creates `PENDING` debt (existing)
- `Debt.applyPayment(payment)` → updates `paidAmount`; 022 only accepts a payment for the whole remaining balance and moves the debt to `PAID` or `PAYMENT_REPORTED` depending on the group setting
- `Debt.getOutstanding()` → `amount.subtract(paidAmount)` (existing)
- `Debt.isSettled()` → `status == PAID or status == VERIFIED` (extended)

**Migration for the state CHECK** (V18):

```sql
ALTER TABLE debts DROP CONSTRAINT ck_debts_status;
ALTER TABLE debts ADD CONSTRAINT ck_debts_status
    CHECK (status IN ('PENDING', 'PARTIALLY_PAID', 'PAYMENT_REPORTED', 'PAID', 'VERIFIED', 'OVERDUE'));

ALTER TABLE debts DROP CONSTRAINT ck_debts_status_matches_paid;
ALTER TABLE debts ADD CONSTRAINT ck_debts_status_matches_paid CHECK (
    (status = 'PENDING'           AND paid_amount = 0)
    OR (status = 'PARTIALLY_PAID' AND paid_amount > 0 AND paid_amount < amount)
    OR (status = 'PAYMENT_REPORTED' AND paid_amount >= 0 AND paid_amount <= amount)
    OR (status = 'PAID'           AND paid_amount = amount)
    OR (status = 'VERIFIED'       AND paid_amount = amount)
    OR (status = 'OVERDUE'        AND paid_amount = 0));
```

`PARTIALLY_PAID` stays valid so existing 018 rows keep loading; the 022 code never creates it.

**Invariants**:
- `0 <= paidAmount <= amount`
- `PAYMENT_REPORTED` does **not** count as settled: only `PAID` and `VERIFIED` are settled for balances
- `OVERDUE` only when the group has `debtDueDays` configured and only from `PENDING`
- `PARTIALLY_PAID` is read-only for 022

**Relationships**:
- `N:1` with `GroupExpense` (the expense that generated the debt)
- `N:1` with `Currency` (the currency)
- `1:N` with `Payment` (payments registered against this debt)

## Entity: Payment (EXTENDIDA, no nueva)

**Source**: Existing entity `backend/src/main/java/com/amano/backend/payment/Payment.java`, table `payments`, created by `V14__create_payments.sql` (018).
**Extended for**: 022 with `ALTER TABLE`; **no new class and no new table**.

Existing fields (unchanged): `id`, `groupId`, `debtId`, `payerType`, `payerUserId`,
`payerSimulatedProfileId`, `payerName`, `receiverUserId`, `receiverName`,
`registeredByUserId`, `amount`, `currency`, `paymentDate`, `idempotencyKey`,
`createdAt`.

New fields added in V18:

| Field | Type | Nullable | Description | Constraints |
|-------|------|----------|-------------|-------------|
| `status` | `PaymentStatus` | No | Payment state | `PENDING`, `REPORTED`, `CONFIRMED`, `REJECTED`; existing rows = `CONFIRMED` |
| `decidedByUserId` | `Long` | Yes | Admin who confirmed/rejected | FK `users(id)`; both decided fields set or both null |
| `decidedAt` | `OffsetDateTime` | Yes | When the decision was made | Both decided fields set or both null |
| `legacy` | `boolean` | No | Pre-022 payment | Existing rows `TRUE`; new rows `FALSE` |

**PaymentStatus** enum values:
- `PENDING`: reserved for a payment registered but not yet processed (not produced by the 018 flow)
- `REPORTED`: full payment reported; awaiting an admin decision when the group requires validation
- `CONFIRMED`: payment accepted and applied to the debt (no validation required, or admin confirmed under the 018 flow)
- `REJECTED`: admin rejected the payment; the debt returns to `PENDING`. If it was already overdue, the next execution of the overdue debt job (D7) moves it back to `OVERDUE`; the job never changes `PARTIALLY_PAID`. If a legacy 018 partial payment existed, the debt returns to `PARTIALLY_PAID` with the `paid_amount` it had before the rejected payment

**Migration (V18)**:

```sql
ALTER TABLE payments ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED';
ALTER TABLE payments ADD COLUMN decided_by_user_id BIGINT NULL REFERENCES users (id);
ALTER TABLE payments ADD COLUMN decided_at TIMESTAMPTZ NULL;
ALTER TABLE payments ADD COLUMN legacy BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE payments SET status = 'CONFIRMED', legacy = TRUE;
ALTER TABLE payments ALTER COLUMN legacy SET DEFAULT FALSE;

ALTER TABLE payments ADD CONSTRAINT ck_payments_status
    CHECK (status IN ('PENDING', 'REPORTED', 'CONFIRMED', 'REJECTED'));
ALTER TABLE payments ADD CONSTRAINT ck_payments_decision
    CHECK ((decided_by_user_id IS NULL AND decided_at IS NULL)
        OR (decided_by_user_id IS NOT NULL AND decided_at IS NOT NULL));

CREATE UNIQUE INDEX uq_payments_active_debt ON payments (debt_id)
    WHERE legacy = FALSE AND status IN ('REPORTED', 'CONFIRMED');
```

The partial index is the database backstop for "one active payment per debt"; the legacy
rows (including the partial payments of 018) stay outside it.

**Invariants**:
- `0 < amount <= debt.getOutstanding()`; 022 accepts only a payment for the whole remaining balance
- Same `idempotencyKey` reused → returns existing result (no duplicate)
- `decidedByUserId` and `decidedAt` must both be set or both be null
- `PAYMENT_REPORTED`/`REPORTED` does not settle the debt by itself

## Entity: Group (extended)

**Source**: Existing entity, extended for 022 feature requirements

| Field | Type | Nullable | Description | Constraints |
|-------|------|----------|-------------|-------------|
| `id` | `Long` | No | Primary key | - |
| `primaryCurrency` | `Currency` | No | Group's primary currency | `ARS`/`USD`/`EUR`; existing groups default to `ARS` in V17 |
| `debtDueDays` | `Integer` | Yes | Days until debt considered OVERDUE | Nullable; null = never overdue; `>= 0` |
| `createdByUserId` | `Long` | No | Group creator | - |
| `name` | `String` | No | Group name | Max 100 chars |
| `description` | `String` | Yes | Optional description | Max 200 chars |

**Relationships**:
- `1:N` with `GroupExpense` (expenses in this group)
- `1:N` with `GroupExpenseShare` (members/shares)
- `1:N` with `Debt` (debts in this group)
- `1:N` with `Payment` (payments in this group)

**Invariants**:
- `primaryCurrency in {ARS, USD, EUR}`
- `debtDueDays >= 0` if set (null is valid meaning "never overdue")

**Migration (V17)**:

```sql
ALTER TABLE groups ADD COLUMN primary_currency VARCHAR(3) NOT NULL DEFAULT 'ARS';
ALTER TABLE groups ADD COLUMN debt_due_days INTEGER NULL;
ALTER TABLE groups ADD CONSTRAINT ck_groups_primary_currency
    CHECK (primary_currency IN ('ARS', 'USD', 'EUR'));
ALTER TABLE groups ADD CONSTRAINT ck_groups_debt_due_days
    CHECK (debt_due_days IS NULL OR debt_due_days >= 0);
```

`primary_currency` defaults to `ARS` for groups that already exist (there is no other
source to infer it from); `debt_due_days` is nullable and is added exactly once.

## Enum: ParticipantType

| Value | Meaning | Usage |
|-------|---------|---------|
| `REGISTERED` | User with account in the system | Has `debtorUserId` or `payerUserId` set |
| `SIMULATED` | Person without account, loaded by name/email | Has `debtorSimulatedProfileId` set; no financial obligation |

## Enum: Currency (existing, reused)

| Value | Description |
|-------|-------------|
| `ARS` | Argentine Peso |
| `USD` | US Dollar |
| `EUR` | Euro |

**Used in**: `GroupExpense`, `Debt`, `Payment`, `GroupExpenseShare` via `CurrencyConversion` JPA adapter.

## Value Object: MontoConvertido (Converted Amount)

**Purpose**: Store both original and converted amounts per the money representation design decision.

| Field | Type | Description |
|-------|------|-------------|
| `monedaOriginal` | `Currency` | The currency of the original amount |
| `montoOriginal` | `BigDecimal` | The original amount (always stored) |
| `montoConvertido` | `BigDecimal` | The amount converted to the group's primary currency |
| `tasaAplicada` | `BigDecimal` | The exchange rate applied (e.g., 18.50 means 1 USD = 18.50 ARS) |
| `fechaCotizacion` | `LocalDate` | The date the rate was effective from |

**Usage**: When an expense is in a currency different from the group's main currency, both the original amount and its converted equivalent are shown, using the official BCRA exchange rate for the transaction date (per FR-009 and the multi-currency user story). Weekend/holiday dates use the last business day's quote within 7 days; `fechaCotizacion` records the quote date actually used.

## Summary of Changes from Existing Model

1. **DebtStatus extended** from 3 states (PENDING/PARTIALLY_PAID/PAID) to 6 states (PENDING/PARTIALLY_PAID/PAYMENT_REPORTED/PAID/VERIFIED/OVERDUE); `PARTIALLY_PAID` is read-only
2. **Payment is extended, not recreated**: existing `com.amano.backend.payment.Payment` / table `payments` gains `status`, `decided_by_user_id`, `decided_at` and `legacy`; existing rows become `CONFIRMED` + legacy
3. **Removed from GroupExpense**: `recurrent` field and `vencimientoDias` field (threshold now configured per Group via `debtDueDays`)
4. **Added to Group**: `primaryCurrency` (default `ARS` for existing groups) and `debtDueDays` (nullable)
5. **MontoConvertido VO**: Fields now owned by GroupExpense directly (`montoConvertido`, `tasaAplicada`, `fechaCotizacion`, `rateSource`); no fallback to the original currency when a quote is unavailable
6. **State machine rules** document the "payments require validation" group checkbox behavior and the "overdue" auto-transition configured per group
7. **`PAID` and `VERIFIED` are equivalent for saldo calculations** per clarification Q7
8. **New `isSettled()` semantics**: returns true for both `PAID` and `VERIFIED`; `PAYMENT_REPORTED` is not settled
9. **Payer and creator are distinct**: any group member can register an expense paid by another member; `payerUserId` and `createdByUserId` are stored separately, debts are owed to the payer and both appear in the activity history (D11)

## Invariants Across All Entities

- No floating point types (BigDecimal used for all monetary values)
- All currency values use the `Currency` enum (ARS/USD/EUR)
- Idempotency keys for all registration operations
- Transactional integrity (Spring `@Transactional`)
- Debts always belong to a group and an expense
- Simulated persons participate in calculations but have no account
- **PAYMENT_REPORTED ≠ PAID for saldo calculations**: a debt in `PAYMENT_REPORTED` state still contributes `amount - paidAmount` to net balance (non-settled)
- **OVERDUE debts still count as saldo**: a debt in OVERDUE state still appears in net balance calculations and can be paid