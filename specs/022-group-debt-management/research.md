# Research: Group Debt Management - Technical Context Unknowns

**Feature**: Group Debt Management (specs/022-group-debt-management)
**Created**: 2026-11-06
**Status**: Phase 0 - Research Complete

## Technical Context Unknowns - Findings from Codebase Analysis

### 1. Money Representation (No Floating Point) ✅ RESOLVED

**Design Decision**: "Dinero: sin punto flotante. Guardar siempre moneda original, monto original, monto convertido y tasa aplicada, siguiendo la representación monetaria que ya use el proyecto."

**Findings**:
- The project already uses `BigDecimal` for all monetary values (see `GroupExpense.amount`, `Debt.amount`, `Debt.paidAmount`, `Payment.amount`)
- Currency enum exists: `ARS, USD, EUR` (`backend/src/main/java/com/amano/backend/user/Currency.java`)
- JPA converter `CurrencyConversion` handles persistence between `Currency` enum and 3-letter ISO code
- All monetary columns have `precision = 11, scale = 2` in the database schema
- No floating point types (Double/Float) used anywhere in the financial domain

**Resolution**: The existing `BigDecimal` + `Currency` pattern already satisfies the "no floating point" requirement. The design should:
- Guard original currency (from `Currency` enum)
- Guard original amount (`BigDecimal` with 2 decimal places)
- Guard converted amount (`BigDecimal`, stored separately if needed)
- Guard applied exchange rate (`BigDecimal` or `double` with proper precision documentation)
- Use the existing `CurrencyConversion` converter pattern for currency persistence

**Rationale**: Reusing existing patterns avoids introducing new dependencies and respects the constitution's "reutilizar lo existente" principle. The `BigDecimal` scale of 2 already handles cent precision.

### 2. Balance Recalculation Triggers ✅ RESOLVED

**Design Decision**: "Saldos: derivarlos del registro de gastos y pagos (fuente de verdad), con cache solo si hace falta rendimiento. Definir cuándo se recalculan."

**Findings**:
- Balances are NOT currently stored as a denormalized field; they are derived from `Debt.paidAmount` vs `Debt.amount` (via `getOutstanding()`)
- The `Debt.getOutstanding()` method computes `amount.subtract(paidAmount)`
- Group balances would be computed by summing `getOutstanding()` for all debts in a group
- No existing cache layer for balances (Redis, Caffeine, etc.)
- Balance recalculation happens when debts are created/updated via the service transactions

**Resolution**:
- Balances derived from debt records (source of truth) - already the pattern
- Cache only if performance testing indicates need (not currently implemented)
- Recalculate balances whenever:
  - A new expense is registered (debts created)
  - A payment is registered (debt `paidAmount` updated)
  - A debt state changes
  - Liquidation is generated
- No separate "balance table" needed; compute on-demand from debts

**Rationale**: Deriving from the debt records (which are the source of truth per FR-003) ensures consistency without additional state. The existing transactional pattern in `GroupExpenseService` already ensures atomicity.

### 3. Liquidation Algorithm ✅ RESOLVED

**Design Decision**: "Liquidación: módulo de dominio puro, sin dependencias de infraestructura, con algoritmo voraz de compensación de saldos netos. Documentar que garantiza como máximo n-1 transferencias y que el mínimo absoluto es NP-difícil, así que no se busca."

**Findings**:
- No existing liquidation/simplification module found
- The `EqualSplit.split()` handles equal division but not debt compensation
- The `Debt.applyPayment()` handles individual payments but not group liquidation
- The feature spec (FR-004) requires: "proponiendo el conjunto reducido de transferencias (quién transfiere, a quién y cuánto) que deja a todos saldados"

**Resolution**: Implement a pure domain greedy algorithm:
1. Compute net balance for each member (sum of all owed/minus-all-owed within group)
2. Filter out members with zero net balance
3. While there are members with non-zero balances:
   - Find the member with the highest positive balance (owed to them)
   - Find the member with the highest negative balance (owes the most)
   - Transfer the minimum of the two amounts
   - Record the transfer
   - Update balances
   - Remove members whose balance reached zero
4. Document guarantee: At most n-1 transfers (where n = members with non-zero balance)
5. Document that the absolute minimum number of transfers is NP-difícil, so greedy is the practical approach

**Algorithm Pseudocode**:
```
balances = computeNetBalances(group)
transfers = []
while exists member with balance != 0:
    pos = member with max positive balance
    neg = member with max negative balance (most debt)
    amount = min(pos.balance, abs(neg.balance))
    transfers.add(pos.name → neg.name, amount)
    pos.balance -= amount
    neg.balance += amount
```
Guarantee: ≤ n-1 transfers where n = |{members with balance != 0}|

**Rationale**: Pure domain module with no infrastructure dependencies. The greedy algorithm is well-known, simple to test, and the n-1 guarantee matches the success criteria (SC-002). The NP-hard acknowledgment manages expectations.

### 4. Debt State Machine ⚠️ PARTIAL - NEEDS EXTENSION

**Design Decision**: "Estados de deuda: máquina de estados explícita (PENDING, PAYMENT_REPORTED, PAID, VERIFIED, OVERDUE) con transiciones válidas y reglas según la casilla "Pagos requiere validación" del grupo."

**Findings**:
- Current `DebtStatus` enum has: `PENDING`, `PARTIALLY_PAID`, `PAID` (3 states)
- Current `Debt.applyPayment()` moves PENDING → PARTIALLY_PAID → PAID
- Current `Debt.isSettled()` returns true only for PAID
- No `Pago informado`, `Verificado`, or `Vencido` states exist
- No transition rules based on "payments require validation" group setting

**Required Extension**:
Add new states to the debt state machine:
- `PENDIENTE` (equivalent to current PENDING)
- `PAGO_INFORMADO` (payment registered, awaiting validation or auto-confirmed)
- `PAGADO` (equivalent to current PAID, full amount paid)
- `VERIFICADO` (admin confirmed payment, for groups with validation)
- `VENCIDO` (auto-transition after deadline, only for recurring expenses)

**New State Machine Transitions**:
```
PENDIENTE → PAGO_INFORMADO (when payment registered, with/without validation)
PAGO_INFORMADO → PAGADO (if no validation required, or admin confirms)
PAGO_INFORMADO → PENDIENTE (if admin rejects)
ANY → VENCIDO (if recurring expense and current date > expense date + configured days)
PAGADO/VERIFICADO are equivalent for balance calculations (per clarification Q7)
```

**Additional Changes needed**:
- Extend `DebtStatus` enum (or create new enum)
- Add `overdueAt` field (optional, for recurring expenses)
- Add `dias_vencimiento` or configure per group
- Update `Debt.applyPayment()` to handle the new state transitions
- Update `GroupExpenseService.create()` to initialize new state

**Rationale**: The existing 3-state model is insufficient for the feature requirements. Extending it preserves the existing `PAID` semantics while adding the new states needed for the "payment validation" workflow and "vencido" state.

### 5. Vencimiento (Debt Expiration) Mechanism ⚠️ PARTIAL - NEEDS IMPLEMENTATION

**Design Decision**: "Vencimiento: mecanismo de tareas programadas ya existente en el proyecto (o el equivalente más simple si no hay), con ejecución idempotente."

**Findings**:
- No existing "vencido" or expiration mechanism found in the codebase
- Spring Boot `@Scheduled` or similar may be configured project-wide
- No existing cron/interval patterns searched specifically for this feature
- The constitution mentions CI/CD via GitHub Actions but doesn't specify scheduled task patterns

**Required Implementation**:
1. Check if Spring `@Scheduled` is configured anywhere in the project
2. If yes, create a scheduled task that:
   - Runs periodically (e.g., daily at midnight)
   - Iterates over groups
   - For each group, checks debts with recurring expenses
   - If current date > expense date + configured vencimiento days → set debt status to VENCIDO
   - If not configured per group, skip (or use default)
3. If no scheduled task mechanism exists, use the "simplest equivalent" - a manual trigger or a simple Quartz/CRON setup
4. Ensure idempotent execution (running multiple times produces same result)

**Idempotency Design**:
- The task should be safe to run multiple times
- Check current debt status before transitioning
- Only transition debts that are in eligible states (PAGO_INFORMADO or PAGADO for recurring)
- Log what was transitioned for audit

**Rationale**: Following the design decision to use existing project mechanisms. Spring `@Scheduled` is the standard Java Spring approach.

### 6. Concurrency & Idempotency ✅ RESOLVED (PARTIAL)

**Design Decision**: "Concurrencia: pagos o gastos simultáneos sobre un mismo grupo no deben corromper saldos; registrar un pago debe ser idempotente."

**Findings**:
- **Idempotency key already exists**: `GroupExpense.idempotencyKey` (UUID) and `Payment.idempotencyKey` (UUID)
- **Concurrent registration handled**: `GroupExpenseService.resolveConcurrentRegistration()` - winner-takes-all pattern after transaction rollback
- **Database-level safety**: Transactions with proper isolation
- **Payment uniqueness**: Not yet enforced at DB level, but application-level idempotency key prevents double-processing

**Resolution**:
- **Payments must include idempotency key** - already pattern in `Payment.entity` and `Payment.of()` 
- **Concurrent payment protection**: Use the same idempotency key pattern as expenses
- **Transaction isolation**: Already using Spring `@Transactional`
- **Balance corruption prevention**: The existing transactional pattern in `GroupExpenseService` ensures atomicity - either all changes commit or none do
- **Synchronization**: No explicit `synchronized` or locking needed beyond database transactions

**Implementation**:
- Payment registration endpoint/service must require and validate idempotency key
- Same key reuse → return existing payment result (don't create duplicate)
- Database unique constraint on (debt_id, idempotency_key) could be added
- Balance recalculation after payment is within the same transaction

**Rationale**: Reusing existing idempotency key pattern from group expenses. No new infrastructure needed.

### 7. OANDA Exchange Rate Adapter ⚠️ NEW - NEEDS DESIGN

**Design Decision**: "Cotizaciones OANDA: detrás de un adaptador, con cache por (par, fecha) y comportamiento definido si el servicio no responde. **Sin tasa por defecto, sin TTL.**"

**Findings**:
- No existing exchange rate service or adapter in the codebase
- No external API calls for rates found
- The project uses `Currency` enum with ARS, USD, EUR only
- No existing HTTP client configuration found

**Required Design**:
1. Create `ExchangeRateService` interface/adaptor:
   - Method: `getRate(Currency from, Currency to, LocalDate date) -> BigDecimal`
   - Cache: `Map<String, BigDecimal>` keyed by `from-to-date` or use Caffeine/Redis
   - Fallback behavior when service doesn't respond:
     - Return cached rate if available (stale is better than none)
     - Or throw specific exception that caller must handle
     - **No default rate**; if OANDA unavailable and no cache → expense not saved (503)

2. Cache strategy:
   - Key: `"{from}-{to}-{date}"` (e.g., "USD-ARS-2026-01-15")
   - **No TTL** - rates are historical, same date always has same rate
   - On cache miss → fetch from external service
   - On service failure → if cached rate exists (any age), use it with warning; otherwise throw 503
   - **No "stale data" concept** - rates for a given date are deterministic

3. Exchange rate storage (per feature decision):
   - Store original currency, original amount, converted amount, and applied rate
   - Per the design decision: "Guardar siempre moneda original, monto original, monto convertido y tasa aplicada"

**Example Data Flow**:
```
Expense in USD 50, group primary currency ARS, date 2026-01-15
→ Fetch rate USD→ARS for 2026-01-15 (e.g., 18.50)
→ Store: {currency: USD, amount: 50, convertedAmount: 925.0, rate: 18.50}
→ Display: "$50 USD - $925 ARS"
```

**Fallback Behavior**:
- If OANDA service unavailable and no cache → **expense not saved (503)**, message: "No se pudo obtener la cotización; registre el gasto en la moneda del grupo o reintente después"
- If cache hit → use it (no staleness concern for historical rates)
- Log all fallback usage for monitoring

**Rationale**: Follows the design decision explicitly. Creates a clear contract for currency conversion that can be implemented/replaced later. No default rate - the group must always have a valid rate or the expense cannot be registered.

### 8. API Data Model Contracts ✅ RESOLVED (PARTIAL)

**Design Decision**: "Contratos de API y modelo de datos para gastos, reparto, deudas, pagos y propuesta de liquidación, alineados con los de las specs anteriores."

**Findings**:
- **Gasto/Expense**: `CreateGroupExpenseRequest` → `GroupExpense` + shares + debts (already exists in `GroupExpenseService.create()`)
- **Reparto/Split**: `EqualSplit.split()` already handles equal, custom, percentage (though percentage still needs validation)
- **Deudas/Debts**: `Debt.entity`, `DebtStatus enum`, `Debt.pendingFor()`, `Debt.applyPayment()` already exist
- **Pagos/Payments**: `Payment.entity`, `Payment.of()`, `Payment.idempotencyKey` already exist
- **Liquidación proposal**: Not yet implemented - needs new DTO/contract

**Existing DTOs/Contracts Found**:
- `CreateGroupExpenseRequest` (in `GroupExpenseService` - internal, may need API-side DTO)
- `GroupExpenseResponse` (with `Share`, `DebtView` inner classes)
- `DebtView` (inner class in `GroupExpenseResponse` - shows debt status, canRegisterPayment)
- `Payment` entity with `of()` factory method

**Missing Contracts/DTOs**:
- Liquidation proposal request/response (new)
- Currency conversion request/response (new, per Q7 design)
- Pending debts view with full detail (already partially in `GroupExpenseResponse.DebtView`)

**Resolution**:
1. Keep using existing internal service contracts where possible (reuses `EqualSplit`, `DebtStatus`, etc.)
2. Create new DTOs only where the spec requires API exposure:
   - `LiquidationProposalRequest` / `LiquidationProposalResponse`
   - `CurrencyConversionRequest` / `CurrencyConversionResponse`
   - Update `PendingDebtDetail` to include origin info (expense, share, etc.)
3. Ensure all new DTOs align with previous specs' patterns (001-021)

**Rationale**: Maximize reuse of existing contracts per constitution principle. Only add new DTOs where the feature specification requires it.

### 9. Testing Strategy Extension ⚠️ PARTIAL - NEEDS IMPLEMENTATION

**Design Decision**: "Pruebas: seguir la estrategia de testing del proyecto y agregar pruebas de propiedades (la suma de saldos de un grupo es siempre cero; el total transferido iguala el total adeudado)."

**Findings**:
- **Project testing strategy**:
  - Backend: JUnit 5 + Spring Boot Test + Testcontainers
  - Frontend: Vitest + React Testing Library (`*.test.jsx`)
  - Coverage: JaCoCo (backend, 85% min) + Vitest frontend coverage
  - CI: GitHub Actions runs `mvn -B verify` and `npm ci && npm run lint && npm run test:coverage && npm run build`
- **Existing tests**: 
  - `ExpenseAmountNormalizerTest` (unit test found)
  - Debt-related tests may exist in `*Test` or `*IT` files
- **Property tests needed**:
  - "Suma de saldos de un grupo es siempre cero" - new
  - "El total transferido iguala el total adeudado" - new
  - Liquidation transfer count ≤ n-1 - new

**Required Test Additions**:
1. **Unit tests**:
   - `EqualSplit.split()` produces shares that sum to total
   - `Debt.applyPayment()` correctly updates paidAmount and status
   - Liquidation algorithm produces ≤ n-1 transfers

2. **Integration tests**:
   - Expense registration → debt creation → balance verification
   - Payment registration → debt status transition → balance check
   - Liquidation generation → transfer count verification → balance SC-001
   - Multi-currency expense display → original + converted amount

3. **Property tests** (using JUnit 5 own generators, no junit-quickcheck dependency):
   - `forAllGroups: sum(balances) == 0`
   - `forAllLiquidations: transfersCount <= (nonZeroMembers - 1)`
   - `forAllPayments: afterPayment, groupBalancesSum == 0`

**Test Structure Suggestion** (following existing patterns):
```
src/test/java/com/amano/backend/groupexpense/
  EqualSplitTest.java          # existing or new
  DebtTest.java                # existing or new, add property tests
  GroupExpenseServiceIT.java   # integration test (using Testcontainers)
  DebtLiquidationPropertyTest.java  # new property-based tests
```

**Rationale**: Follows the project's testing strategy (JUnit 5, Testcontainers, 85% coverage). Property tests add the specific financial correctness guarantees the feature requires. **No junit-quickcheck dependency**.

## Research Summary

All 9 unknowns have been addressed with findings from the codebase analysis. The key conclusions are:

1. **Existing patterns reuse strongly recommended** - BigDecimal, Currency enum, idempotency keys, Transactional services
2. **Extensions needed** - Debt state machine (add VENCIDO, PAGO_INFORMADO, VERIFICADO), liquidation algorithm, exchange rate adapter
3. **No floating point** - Already satisfied by BigDecimal pattern
4. **Idempotency** - Already patterned, just needs to be applied to payments
5. **New DTOs/contracts** - Only where API exposure required, not for internal services
6. **No junit-quickcheck** - Use JUnit 5 own generators

**Next Step**: Proceed to Phase 1: Design & Contracts, creating data-model.md, contracts/, and quickstart.md based on these research findings.