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
| `rateSource` | `String` | Yes | Identifier of the rate source (e.g., "OANDA") | null if same currency |

**Relationships**:
- `1:N` with `GroupExpenseShare` (shares/participants)
- `1:N` with `Debt` (one debt per non-payer participant)
- `1:N` with `Payment` (payments registered against debts)

** invariants**:
- `amount > 0`
- `currency in {ARS, USD, EUR}`
- `montoConvertido` and `tasaAplicada` and `fechaCotizacion` and `rateSource` are set only when `currency` differs from group's primary currency
- `idempotencyKey` uniqueness enforced per `(createdByUserId, idempotencyKey)`

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

**Debt Status Machine** (`DebtStatus` enum):

| Status | Meaning | Transition Rules |
|--------|---------|------------------|
| `PENDING` | Initial state; no payments yet | Default on debt creation |
| `PAYMENT_REPORTED` | Payment registered (informative only) | When payment registered; with "Pagos requiere validación": stays until admin confirms/rejects; without: auto-becomes `PAID` |
| `PAID` | Full payment received; saldo zero | When `paidAmount >= amount`; also auto-from `PAYMENT_REPORTED` if no validation required |
| `VERIFIED` | Admin confirmed payment (groups with validation) | When admin confirms `PAYMENT_REPORTED`; equivalent to `PAID` for balance calculations |
| `OVERDUE` | Debt past due date | Auto-transition when: group has `debtDueDays` configured AND `currentDate > expenseDate.plusDays(group.debtDueDays)` AND `debt.status in {PAYMENT_REPORTED, PAID}` |

**Key Methods** (existing + new):
- `Debt.pendingFor(expense, share)` → creates `PENDING` debt (existing)
- `Debt.applyPayment(payment)` → updates `paidAmount` and `status` (existing, needs extension for new states)
- `Debt.getOutstanding()` → `amount.subtract(paidAmount)` (existing)
- `Debt.isSettled()` → `status == PAID or status == VERIFIED` (existing, needs extension: also true for `VERIFIED`)

**Invariants**:
- `0 <= paidAmount <= amount`
- Status transitions only forward (never backward)
- `OVERDUE` only when group has `debtDueDays` configured

**Relationships**:
- `N:1` with `GroupExpense` (the expense that generated the debt)
- `N:1` with `Currency` (the currency)
- `1:N` with `Payment` (payments registered against this debt)

## Entity: Payment (NUEVA)

**Source**: New entity for 022 feature requirements

| Field | Type | Nullable | Description | Constraints |
|-------|------|----------|-------------|-------------|
| `id` | `Long` | No | Primary key | `GenerationType.IDENTITY` |
| `groupId` | `Long` | No | Foreign key to group | - |
| `debtId` | `Long` | No | Origin debt | - |
| `payerUserId` | `Long` | No | User who made the payment | - |
| `amount` | `BigDecimal` | No | Payment amount | `precision=11, scale=2`; > 0; ≤ debt.getOutstanding() |
| `status` | `PaymentStatus` | No | Payment state | PENDING, REPORTED, CONFIRMED, REJECTED |
| `decidedByUserId` | `Long` | Yes | Admin who confirmed/rejected (if applicable) | Null if status != CONFIRMED/REJECTED |
| `decidedAt` | `OffsetDateTime` | Yes | When decision was made | Null if not decided |
| `idempotencyKey` | `UUID` | No | For concurrent registration safety | Unique per (debtId, key) |

**PaymentStatus** enum values:
- `PENDING`: Payment registered, not yet processed
- `REPORTED`: Payment registered (informative); awaiting validation decision
- `CONFIRMED`: Admin confirmed the payment (for groups with validation)
- `REJECTED`: Admin rejected the payment

**Invariants**:
- `0 < amount <= debt.getOutstanding()`
- Same `idempotencyKey` reused → returns existing result (no duplicate)
- `decidedByUserId` and `decidedAt` must both be set or both be null

## Entity: Group (extended)

**Source**: Existing entity, extended for 022 feature requirements

| Field | Type | Nullable | Description | Constraints |
|-------|------|----------|-------------|-------------|
| `id` | `Long` | No | Primary key | - |
| `primaryCurrency` | `Currency` | No | Group's primary currency | - |
| `debtDueDays` | `Integer` | Yes | Days until debt considered OVERDUE | Nullable; null = never vencido |
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
- `debtDueDays >= 0` if set (null is valid meaning "never vencido")

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

**Usage**: When an expense is in a currency different from the group's main currency, both the original amount and its converted equivalent are shown, using the exchange rate from the transaction date (per FR-009 and the multi-currency user story).

## Summary of Changes from Existing Model

1. **DebtStatus extended** from 3 states (PENDING/PARTIALLY_PAID/PAID) to 5 states (PENDING/PAYMENT_REPORTED/PAID/VERIFIED/OVERDUE)
2. **New entity: Payment** with status, decided_by_user_id, decided_at and idempotency key
3. **Removed from GroupExpense**: `recurrent` field and `vencimientoDias` field (threshold now configured per Group via `debtDueDays`)
4. **Added to Group**: `primaryCurrency` field and `debtDueDays` field
5. **MontoConvertido VO**: Fields now owned by GroupExpense directly (`montoConvertido`, `tasaAplicada`, `fechaCotizacion`, `rateSource`)
6. **State machine rules** document the "payments require validation" group checkbox behavior and the "vencido" auto-transition configured per group
7. **`PAID` and `VERIFIED` are equivalent for saldo calculations** per clarification Q7
8. **New `isSettled()` semantics**: returns true for both `PAID` and `VERIFIED`

## Invariants Across All Entities

- No floating point types (BigDecimal used for all monetary values)
- All currency values use the `Currency` enum (ARS/USD/EUR)
- Idempotency keys for all registration operations
- Transactional integrity (Spring `@Transactional`)
- Debts always belong to a group and an expense
- Simulated persons participate in calculations but have no account
- **PAYMENT_REPORTED ≠ PAID for saldo calculations**: a debt in `PAYMENT_REPORTED` state still contributes `amount - paidAmount` to net balance (non-settled)
- **OVERDUE debts still count as saldo**: a debt in OVERDUE state still appears in net balance calculations and can be paid