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
| `recurrent` | `boolean` | No | Whether expense is recurrent | Default: `false` |
| `vencimientoDias` | `Integer` | Yes | Days until expense considered "Vencido" | Only applicable if `recurrent=true`; null otherwise |

**Relationships**:
- `1:N` with `GroupExpenseShare` (shares/participants)
- `1:N` with `Debt` (one debt per non-payer participant)
- `1:N` with `Payment` (payments registered against debts)

** invariants**:
- `amount > 0`
- `currency in {ARS, USD, EUR}`
- If `recurrent=true` then `vencimientoDias` must be set (enforced in service validation)
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
| `vencidoAt` | `OffsetDateTime` | Yes | When debt transitioned to Vencido | Null if not vencido |

**Debt Status Machine** (`DebtStatus` enum extension):

| Status | Meaning | Transition Rules |
|--------|---------|------------------|
| `PENDIENTE` | Initial state; no payments yet | Default on debt creation |
| `PAGO_INFORMADO` | Payment registered (informative only) | When payment registered; with "Pagos requiere validación": stays until admin confirms/rejects; without: auto-becomes `PAGADO` |
| `PAGADO` | Full payment received; saldo zero | When `paidAmount >= amount`; also auto-from `PAGO_INFORMADO` if no validation required |
| `VERIFICADO` | Admin confirmed payment (groups with validation) | When admin confirms `PAGO_INFORMADO`; equivalent to `PAGADO` for balance calculations |
| `VENCIDO` | Debt past due date (recurring expenses only) | Auto-transition when: `expense.recurrent=true` AND `currentDate > expense.expenseDate.plusDays(expense.vencimientoDias)` AND `debt.status in {PAGO_INFORMADO, PAGADO}` |

**Key Methods** (existing + new):
- `Debt.pendingFor(expense, share)` → creates `PENDIENTE` debt (existing)
- `Debt.applyPayment(payment)` → updates `paidAmount` and `status` (existing, needs extension for new states)
- `Debt.getOutstanding()` → `amount.subtract(paidAmount)` (existing)
- `Debt.isSettled()` → `status == PAGADO` (existing, needs extension: also true for `VERIFICADO` per clarification Q7)

**Invariants**:
- `0 <= paidAmount <= amount`
- Status transitions only forward (never backward)
- `VENCIDO` only for recurring expenses
- `PAGO_INFORMADO` and `PAGADO` are equivalent for balance calculations (SC-004)

**Relationships**:
- `N:1` with `GroupExpense` (the expense that generated the debt)
- `N:1` with `Currency` (the currency)
- `1:N` with `Payment` (payments registered against this debt)

## Entity: Persona Simulada (SimulatedProfile)

**Source**: Existing entity (inferred from `GroupExpenseService` and `ParticipantType.SIMULATED`)

| Field | Type | Nullable | Description | Constraints |
|-------|------|----------|-------------|-------------|
| `id` | `Long` | No | Primary key | - |
| `groupId` | `Long` | No | Group the persona belongs to | - |
| `name` | `String` | No | Display name (loaded by name and email) | - |
| `email` | `String` | Yes | Contact email (optional, for identification) | - |
| `isActive` | `boolean` | Yes | Whether the persona is active in the group | Default: `true` |

**Behavior**:
- Participates in debt calculations identically to registered users
- Does NOT have a user account; cannot receive payments directly
- Debts with `debtorType=SIMULATED` have `debtorUserId=null`
- `paidAmount` for simulated person debts can be updated only by admin/group processes
- Included in group balance sums per SC-001 (confirmed by clarification Q5)

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

1. **DebtStatus extended** from 3 states (PENDING/PARTIALLY_PAID/PAID) to 5 states (PENDIENTE/PAGO_INFORMADO/PAGADO/VERIFICADO/VENCIDO)
2. **New fields**: `recurrent` (GroupExpense), `vencimientoDias` (GroupExpense), `vencidoAt` (Debt), `monedaOriginal`/`montoConvertido`/`tasaAplicada`/`fechaCotizacion` (MontoConvertido VO concept)
3. **State machine rules** document the "payments require validation" group checkbox behavior and the "vencido" auto-transition for recurring expenses
4. **`PAGADO` and `VERIFICADO` are equivalent for saldo calculations** per clarification Q7

## Invariants Across All Entities

- No floating point types (BigDecimal used for all monetary values)
- All currency values use the `Currency` enum (ARS/USD/EUR)
- Idempotency keys for all registration operations
- Transactional integrity (Spring `@Transactional`)
- Debts always belong to a group and an expense
- Simulated persons participate in calculations but have no account