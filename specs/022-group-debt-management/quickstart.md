# Quickstart: Group Debt Management Validation

**Feature**: Group Debt Management (022)
**Based on**: Feature specification specs/022-group-debt-management/spec.md
**Data Model**: data-model.md
**Contracts**: contracts/ directory
**Created**: 2026-11-06

## Prerequisites

- Java 25 (backend Spring Boot project)
- Maven 3.8+ (for backend: `mvn -B verify`)
- Node.js 20+ + npm 10+ (for frontend: `npm ci && npm run lint && npm run test:coverage && npm run build`)
- PostgreSQL 15+ running with `amano` database
- Backend and frontend Docker containers (via `docker compose` in project root)

## Setup

### 1. Backend

```bash
# From project root
cd backend

# Compile and run tests (will fail if compilation errors)
mvn -B verify

# Or just compile to check
mvn -B compile
```

### 2. Frontend

```bash
# From project root
cd frontend

# Install dependencies and check
npm ci

# Run lint
npm run lint

# Run tests with coverage
npm run test:coverage

# Build
npm run build
```

### 3. Database Migration

```bash
# Flyway will run automatically on backend startup
# If manual execution is needed:
cd backend
mvn flyway:migrate
```

## Validation Scenarios

### Scenario 1: Register a Shared Expense

**Goal**: Verify that registering a shared expense creates debts for all non-payer participants.

**Steps**:

1. Start the application (backend + frontend + PostgreSQL)
2. Log in as a user who is a member of a group
3. Navigate to the group's expense registration page
4. Fill the form:
   - Amount: `100`
   - Currency: `ARS` (or `USD`/`EUR`)
   - Date: `2026-11-06` (today)
   - Category: `Comida`
   - Type: `único`
   - Payer: `Your user`
   - Participants: Add 2 other group members (e.g., `user2`, `user3`)
   - Description: `Cena de grupo`
5. Submit the form
6. **Expected outcomes**:
   - Expense is registered with `idempotencyKey`
   - 2 debts are created (one for each non-payer participant)
   - Debts start in `PENDIENTE` status
   - Group balance sums to zero (SC-001 verification)
   - Activity history shows `EXPENSE_CREATED` event

### Scenario 2: Register a Payment Against a Debt

**Goal**: Verify that registering a payment reduces the debt and updates the state correctly.

**Steps**:

1. Ensure Scenario 1 is complete (debts exist in `PENDIENTE` status)
2. Navigate to the group's debt details
3. Select a pending debt
4. Enter payment amount (must equal outstanding amount exactly; partial payments rejected)
5. Enter payment date (today or past)
6. Submit the payment
7. **Expected outcomes** (depending on group "Pagos requiere validación" setting):
   - **Without validation**: Debt state changes to `PAGADO` if full amount paid; partial/rejected payments fail
   - **With validation**: Debt state changes to `PAGO_INFORMADO`; admin must confirm (changes to `VERIFICADO`) or reject (remains `PENDIENTE`)
   - Payment is recorded in the group's activity history
   - Group balances still sum to zero (SC-001)
   - Idempotency: Submitting the same payment twice produces the same result

### Scenario 3: Generate Liquidation Proposal

**Goal**: Verify that the liquidation proposal suggests the minimum transfers to settle all debts.

**Steps**:

1. Ensure multiple expenses are registered in the group with different payers (so cross-debts exist)
2. Navigate to the group's liquidation page
3. Generate the liquidation proposal
4. **Expected outcomes**:
   - Proposal shows list of transfers (from → to, amount)
   - Transfer count ≤ (members with non-zero balance - 1) [SC-002]
   - Total transferred equals total net needed to settle [SC-001]
   - Original expense history is unchanged (verified by checking expense list)
   - Summary shows `totalTransferred` and `totalNetBalances` with a guarantee they match
5. **Manual verification**: Apply the transfers manually and confirm all members end at zero balance

### Scenario 4: Multi-Currency Expense

**Goal**: Verify that expenses in different currencies show both original and converted amounts.

**Steps**:

1. Ensure the group's primary currency is `ARS`
2. Register a new expense with currency `USD`
3. Enter amount: `50`
4. Enter date: `2026-01-15` (a date with a known/exchange rate)
5. Submit the expense
6. **Expected outcomes**:
   - Expense displays: `"$50 USD - $925 ARS"` (example, depending on the rate)
   - The expense detail shows: `monto original: $50 USD` and `monto convertido: $925 ARS`
   - The applied exchange rate (e.g., 18.50) is stored and visible
   - Group balance calculation includes both the original and converted amounts correctly
7. **Rate cache test**: Generate the same expense again on the same date; the system should use the cached rate (no second API call)

### Scenario 5: Simulated Person in Debt Calculation

**Goal**: Verify that persona simulada participates in debt calculations.

**Steps**:

1. Ensure a simulated profile is registered in the group (via admin interface or SQL)
2. Register an expense where the simulated person is a participant (not the payer)
3. Generate the balance calculation
4. **Expected outcomes**:
   - The simulated person appears in the debt list
   - The simulated person's debt is shown with their name
   - The group balance sum includes the simulated person's debt (SC-001 verification)
   - The simulated person does NOT appear as a valid recipient for payment (they have no account)
   - Liquidation proposal includes the simulated person's net balance but does not propose transfers to/from them (or marks them as informational)

### Scenario 6: Edit/Delete Expense Restrictions

**Goal**: Verify that expenses with participants cannot be edited or deleted.

**Steps**:

1. Register an expense with at least one participant (other than the payer)
2. Attempt to edit the expense (change amount, currency, participants, etc.)
3. **Expected outcome**: Edit is rejected with error: "No se permite editar un gasto que ya tiene participantes"
4. Attempt to delete the expense
5. **Expected outcome**: Delete is rejected with error: "No se puede eliminar un gasto con deudas asociadas"; or: "No se puede eliminar un gasto compartido; solo se pueden eliminar gastos personales (sin participantes)"
6. Attempt to delete a personal expense (no participants)
7. **Expected outcome**: Delete succeeds; group balances recalculated as if the expense never existed

## Property Test Verification

The following properties should pass after implementation (verified via the project's testing strategy):

| Property | Description | Verification |
|----------|-------------|-------------|
| **SC-001** | Suma de saldos de un grupo es siempre cero | After any expense/payment/liquidation operation, compute `Σ(balance_i)` for all group members (including simulated persons); result must be exactly 0 |
| **SC-002** | Transferencias ≤ n-1 | After liquidation, count members with balance ≠ 0 (call it M); verify `transferCount ≤ M - 1` |
| **Full payment required** | Attempting to register payment ≠ outstanding amount fails | Payment registration with amount ≠ `debt.getOutstanding()` throws exception / 400 error |
| **Idempotency** | Same idempotency key twice → same result | Submitting payment with same key returns existing payment; no duplicate created |
| **Pago informado → Verificado/Pagado** | State transitions follow the group's "Pagos requiere validación" setting | Verify debt state after payment registration with both settings |
| **Vencido auto-transition** (recurring only) | Recurring expenses past their deadline → Vencido | For recurring expenses, check `currentDate > expenseDate + group.debtDueDays` → debt status becomes VENCIDO |
| **No floating point** | All monetary values are BigDecimal with scale 2 | Verify no Double/Float types in financial domain; all DB columns precision=11, scale=2 |
| **VENCIDO debts still count as saldo** | Debts in VENCIDO state contribute to net balance | Verify that VENCIDO debts still appear in balance calculations |

## Commands Summary

```bash
# Full backend check (compilation + tests)
cd backend && mvn -B verify

# Full frontend check
cd frontend && npm ci && npm run lint && npm run test:coverage && npm run build

# Run a specific validation scenario (manual, via UI or API)
# See individual scenario steps above

# Check database state (optional)
cd backend
mvn flyway:info  # shows migration status
```

## Need Help?

- Check the existing test patterns in `backend/src/test/` and `frontend/src/`
- Review the constitution at `.specify/memory/constitution.md` for project principles
- Review previous specs (001-021) for established patterns
- Open an issue in the project tracker if validation fails unexpectedly