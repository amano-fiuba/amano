# Quickstart: Group Debt Management Validation

**Feature**: Group Debt Management (022)
**Based on**: [spec.md](spec.md), [plan.md](plan.md), [data-model.md](data-model.md) and [contracts/](contracts/)
**Created**: 2026-11-06
**Updated**: 2026-11-07 (T056/T084: every step was run against a real backend with partial payments; see "Validation run" at the end)

## Prerequisites

- Java 25 and the Maven wrapper (`./mvnw`, no global Maven needed).
- Node.js 20.19+ (the project uses 24) and npm.
- Docker (PostgreSQL 18 runs as `postgres:18-alpine`, and the backend tests use Testcontainers).
- Internet access to `https://api.bcra.gob.ar` for the multi-currency scenario (public API, no credentials).

See [first-steps.md](../../first-steps.md) for the full environment.

## Setup

### 1. Automated checks

```bash
# Backend: compile, unit tests (surefire), integration tests (failsafe) and JaCoCo (>= 85 %)
cd backend && ./mvnw -B verify

# Frontend: install, lint, tests with coverage (>= 85 %) and build
cd frontend && npm ci && npm run lint && npm run test:coverage && npm run build
```

### 2. Database migrations

Flyway runs automatically when the backend starts (V1 to V25). There is no manual step: do **not** run `flyway:migrate`. To see the state, query the `flyway_schema_history` table (Adminer is at <http://localhost:8082> with `docker compose up`).

### 3. Run the application

```bash
# Full stack in containers (needs a .env copied from .env.example)
docker compose up -d --build        # UI at http://localhost:8081

# Or the development servers
cd backend && ./mvnw spring-boot:run   # API at http://localhost:8080
cd frontend && npm run dev             # UI at http://localhost:5173
```

Relevant settings (environment): `BCRA_BASE_URL` (BCRA API, default `https://api.bcra.gob.ar`) and `OVERDUE_CRON` (six-field cron of the overdue-debt job, default `0 30 0 * * *`). Without SMTP the verification e-mail is not delivered; in a throwaway database mark the account as verified with `UPDATE users SET email_verified_at = now() WHERE email = '...'`.

### 4. Run the API-level validation

[scripts/validate_quickstart.py](scripts/validate_quickstart.py) registers users through the real API, builds groups and exercises scenarios 1 to 8 below, printing PASS / FAIL / NOT-RUN per step. Use a **throwaway** database:

```bash
docker run -d --name amano-qs-db -e POSTGRES_DB=amano -e POSTGRES_USER=amano -e POSTGRES_PASSWORD=qs \
  -p 127.0.0.1:55432:5432 postgres:18-alpine

DB_HOST=localhost POSTGRES_PORT=55432 POSTGRES_DB=amano POSTGRES_USER=amano POSTGRES_PASSWORD=qs \
  JWT_SECRET=<at least 32 characters> SERVER_PORT=18080 OVERDUE_CRON="0 * * * * *" \
  java -jar backend/target/backend-0.0.1-SNAPSHOT.jar

python3 specs/022-group-debt-management/scripts/validate_quickstart.py http://localhost:18080 amano-qs-db main

# Then restart the backend with BCRA_BASE_URL=http://127.0.0.1:9 and run:
python3 specs/022-group-debt-management/scripts/validate_quickstart.py http://localhost:18080 amano-qs-db nobcra
```

## Validation Scenarios

The steps are written for the UI (routes in parentheses) and give the API equivalent. Screen texts are in Spanish; debt states are shown with their labels (`Pendiente`, `Pago informado`, `Pagada`, `Pago verificado`, `Vencida`, `Pago parcial`), never with the enum value.

### Scenario 1: Register a Shared Expense

**Goal**: a shared expense creates debts for every non-payer participant.

1. Log in as a member of a group with at least two other members (`/groups/:id`, "Nuevo gasto" → `/groups/:id/expenses/new`).
2. Fill the form: **Monto** `100`, **Moneda** (starts as the group's currency), **Fecha** (dd/mm/aaaa, today or past), **Descripción** `Cena de grupo`, **¿Quién pagó?** yourself, **¿Cómo se reparte?** `En partes iguales`, all members as **Participantes**. (There is no category or type field in a group expense.)
3. Save. API: `POST /api/groups/{id}/expenses` with an `Idempotency-Key` header.
4. **Expected**:
   - `201`; shares `33,33 / 33,33 / 33,34` (each non-payer pays the floor to the cent, the payer absorbs the rest).
   - One debt per non-payer participant, state `Pendiente` (`PENDING`).
   - The balances of the group add up to zero (SC-001): `GET /api/groups/{id}/balances` (UI: `/groups/:id/debts`, tab **Saldos**).
   - The history shows the expense (`EXPENSE_CREATED`).
   - The same `Idempotency-Key` again answers `200` with the same expense.
5. **Other split methods**: `Importes personalizados` (amounts typed in the expense currency that add up exactly to the total) and `Porcentajes` (each > 0, adding up to exactly 100). Percentages that do not add up to 100 answer `400` "Los porcentajes deben sumar exactamente 100".
6. **Personal expense**: marking "Es un gasto personal" (only the payer participates) creates the expense without debts.

### Scenario 2: Register a Payment Against a Debt

**Goal**: partial and total payments, with and without validation.

1. Open the debts screen (`/groups/:id/debts`, tab **Mis deudas pendientes**) as the debtor, the creditor or an administrator.
2. Click **Registrar pago**. The confirmation shows an **editable amount**, preset to the debt's outstanding balance (`amount − paidAmount`), the remaining balance after the payment, and the currency. There is no payment date: the server uses the current date.
3. Confirm. API: `POST /api/groups/{groupId}/debts/{debtId}/payments` with `{ "amount": "<0 < amount <= outstanding>", "idempotencyKey": "<uuid>" }`.
4. **Expected**, depending on the group's "Pagos requiere validación" setting:
   - **Without validation**: `201`, payment `CONFIRMED`, added to `paidAmount` at once. Debt `Pago parcial` (`PARTIALLY_PAID`) if a balance remains, `Pagada` (`PAID`) if it reaches zero. A second partial payment on the same debt is accepted.
   - **With validation**: `201`, payment `REPORTED`, debt `Pago informado` (`PAYMENT_REPORTED`), `paidAmount` untouched. The debt is **not** settled: it keeps counting, with its whole outstanding balance, in the balances. While it is reported, another payment on the debt answers `409` ("Esta deuda ya tiene un pago esperando validación: esperá a que un administrador lo decida"). An administrator decides in the tab **Pagos por validar**: **Confirmar** → the amount is added; `Pago verificado` (`VERIFIED`) if the balance is zero, `Pago parcial` if a balance remains; **Rechazar** → the debt returns to its previous state (`Pendiente` or `Pago parcial`) and accepts a new payment.
   - An amount above the outstanding balance answers `400` ("El monto del pago excede la deuda pendiente"); zero or negative amounts answer `400` too.
   - A partial payment on an `OVERDUE` debt leaves it `Pago parcial` (decision to confirm).
   - The same `idempotencyKey` twice answers `200` with the same payment; the same key with another amount answers `422`.
   - Only the debtor, the creditor or an administrator may register (`403` otherwise); only administrators confirm, reject or list payments to validate (`403`).
   - The inherited 018 endpoint `POST /api/payments` (screen `RegisterPaymentView`) also respects "Pagos requiere validación" and returns `status = CONFIRMED` ("Pago registrado") or `REPORTED` ("Pago informado: queda esperando que un administrador lo confirme").
   - The history shows `PAYMENT_REGISTERED`, `PAYMENT_CONFIRMED` and `PAYMENT_REJECTED`; the balances still add up to zero, and after a confirmed partial payment they use the outstanding balance of each debt.

### Scenario 3: Generate a Liquidation Proposal

**Goal**: an advisory proposal with few transfers.

1. Register several expenses with different payers so cross-debts exist.
2. Open `/groups/:id/debts`, tab **Propuesta de liquidación** (API: `GET /api/groups/{id}/settlement-proposal`).
3. **Expected**:
   - One list of transfers per currency (from → to, amount).
   - Transfers ≤ (members with non-zero balance − 1) (SC-002).
   - `totalTransferred` equals `totalNetBalances`, the sum of the positive balances (not the gross sum of debts).
   - The proposal changes nothing: balances and expenses are the same before and after.
   - **Manual check**: applying the transfers leaves every member at zero.
   - Transfers that involve a simulated person are marked `informative`.
   - A non-member gets the neutral `404`.

### Scenario 4: Multi-Currency Expense

**Goal**: original and converted amounts with the official BCRA quote, applied once to the total.

1. Group in `ARS`. Create an expense in `USD` for `50` dated `2026-01-15` (a business day).
2. While typing, the form shows the estimated converted amount ("La cotización final se fija al guardar"), from `GET /api/groups/{id}/exchange-quote?currency=USD&date=2026-01-15`.
3. Save. **Expected**: the expense keeps `amount 50.00 USD` and stores `convertedAmount` = 50 × rate (ARS), `exchangeRate` (scale 8), `rateDate` and `rateSource` `BCRA`. The shares and debts are in the group currency and the balances use the converted amounts.
4. **Weekend/holiday**: a Sunday date uses the previous business day's quote (up to 7 days back); `rateDate` is that day.
5. **No quote**: if the BCRA does not answer or has no quote within 7 days, the expense is rejected with `503` "No se pudo obtener la cotización; reintente después o registre en moneda del grupo" and **nothing is saved**. The preview shows a message but does not block saving a group-currency expense.
6. **Cache**: the same pair and date reuse the cached quote (not observable from the API; covered by `GroupExpenseControllerIT.t052SecondExpenseWithSameCurrencyAndDateUsesCachedQuoteWithoutSecondApiCall`).
7. The group currency is chosen when the group is created (`ARS`, `USD` or `EUR`; default `ARS`); another value answers `400` "La moneda no es válida".

### Scenario 5: Simulated Person in Debt Calculation

1. Add a simulated profile to the group (members section).
2. Register an expense where the simulated person is a participant (not the payer).
3. **Expected**:
   - Their debt appears with their name; they appear in the balances and the balances add up to zero.
   - They have no account: the payment of their debt is registered by the creditor or an administrator (a member who is not part of that debt gets `403`).
   - The proposal includes their balance, but any transfer that involves them is marked `informative`.

### Scenario 6: Edit and Delete Restrictions

1. An expense with participants other than the payer: editing answers `409` "No se permite editar un gasto que ya tiene participantes"; deleting answers `409` "No se puede eliminar un gasto con deudas asociadas".
2. A personal expense (only the payer, no debts) can be edited (amount, currency, date, description) and deleted (`204`) by the member who registered it, its payer or an administrator; anyone else gets `403` ("No podés editar este gasto" / "No podés eliminar este gasto"). In the UI: expense detail → **Editar gasto** / **Eliminar gasto** (with confirmation).
3. After deleting, the balances are unchanged (a personal expense has no debts); the history shows `EXPENSE_EDITED` and `EXPENSE_DELETED`.

### Scenario 7: Overdue Debts

1. As an administrator set **Días para el vencimiento de las deudas** in the group edit form (integer 0 to 3650; empty = never overdue). API: `PUT /api/groups/{id}` with `{ "debtDueDays": 0 }`. A non-administrator gets `403`; a value out of range, `400`.
2. Create an expense dated before today and wait for the daily job (`OVERDUE_CRON`).
3. **Expected**: the `PENDING` debt becomes `Vencida` (`OVERDUE`), the history shows `DEBT_OVERDUE` without an actor ("Sistema"), the debt still counts in the balances and **can be paid**.

### Scenario 8: Leaving a Group With Open Debts (020)

A member with an open debt, as debtor or creditor (`PENDING`, `PARTIALLY_PAID`, `PAYMENT_REPORTED` or `OVERDUE`), cannot leave: `POST /api/groups/{id}/leave` answers `403` with `properties.code = PENDING_DEBTS` and "No podés abandonar el grupo: tenés saldos pendientes con otros miembros". `PAID` and `VERIFIED` debts do not block.

## Property Test Verification

These properties are covered by tests that run in `./mvnw -B verify` (property tests with a fixed seed):

| Property | Description | Test |
|----------|-------------|------|
| **SC-001** | The sum of the balances of a group is zero in every currency | `BalanceCalculatorTest.everyGeneratedGroupSumsToZeroPerCurrency` |
| **SC-002** | Transfers ≤ M − 1 and applying them leaves every balance at zero | `SettlementPlannerTest.everyGeneratedSettlementZeroesAllBalancesWithAtMostNMinusOneTransfers` |
| **Payments never exceed the debt** | A payment above the outstanding balance answers 400 and the sum of confirmed payments is at most the debt amount | `PartialPaymentPropertyTest.theSumOfTheConfirmedPaymentsNeverExceedsTheDebtAndEqualsPaidAmount`, `DebtPaymentControllerIT.anAmountAboveTheBalanceOrNotPositiveIsRejectedWithAClearMessage` |
| **Balances with partial payments** | The balances of a currency add up to zero after partial payments and use each debt's outstanding balance | `PartialPaymentPropertyTest.theBalancesOfTheGroupAlwaysSumToZeroAndUseTheOutstandingBalance` |
| **Idempotency** | The same key returns the existing payment | `DebtPaymentControllerIT`, `DebtPaymentConcurrencyIT` |
| **Reported is not settled** | `PAYMENT_REPORTED` keeps counting in balances and proposal until confirmed | `PaymentDecisionControllerIT.t056AReportedPaymentKeepsCountingInTheBalancesAndTheProposalUntilItIsConfirmed` |
| **Overdue** | `OVERDUE` still counts in the balance and only comes from `PENDING` | `OverdueDebtJobIT`, `BalanceCalculatorTest` |
| **No floating point** | Money is `BigDecimal` scale 2 (backend) and integers/BigInt (frontend) | code review; `splitCustom.test.js`, `convertPreview.test.js` |

## Commands Summary

```bash
cd backend && ./mvnw -B verify
cd frontend && npm ci && npm run lint && npm run test:coverage && npm run build
```

## Validation run (T056 / T084, 2026-11-07)

Backend built from the working tree and started against a throwaway PostgreSQL 18 container (`docker run --name amano-qs-db`, port `55432`, without touching the development stack), with the real BCRA API (`main`: **52 PASS / 0 FAIL / 2 NOT-RUN**) and a second boot with `BCRA_BASE_URL=http://127.0.0.1:9` (`nobcra`: **4 PASS / 0 FAIL / 0 NOT-RUN**). Flyway applied V1 to V25 on boot with no error.

| Step | Result |
|------|--------|
| Setup: register, login, friendships, group, simulated profile | Executed, passed (4 checks). E-mail verification marked with SQL (no SMTP) |
| 1. Shared expense: shares `33.33/33.33/33.34`, debts `PENDING`, balances zero, idempotency, `EXPENSE_CREATED`, `CUSTOM`, `PERCENTAGE`, invalid percentages `400` | Executed, passed (8 checks) |
| 2. Partial and total payments: excess (`400`), zero (`400`), partial without validation (`CONFIRMED` → `PARTIALLY_PAID`), idempotency (`200` / `422`), second payment completing balance (`PAID`), settled `409`, partial with validation (`REPORTED` → `PAYMENT_REPORTED`, `paidAmount = 0`, balances untouched), `409` while waiting for validation (exact voseo text), `403` for non-admin, admin confirms partial (`PARTIALLY_PAID`), reject restores `PARTIALLY_PAID`, inherited `POST /api/payments` respects validation and closes `VERIFIED`, history events | Executed, passed (19 checks) |
| 3. Proposal: ≤ M − 1, total = sum of positives, applying leaves zero, read-only, `404` | Executed, passed (5 checks) |
| 4. Multi-currency with the **real BCRA** (preview and expense USD 50 on 2026-01-15 @ 1441, Sunday EUR 2026-01-18 → 2026-01-16, same currency `SAME_CURRENCY`, future date `400`, group currency USD `201` / BRL `400`) | Executed, passed (6 checks) |
| 4. No quote (BCRA unreachable, `nobcra` mode): preview `503`, expense `503` (`EXCHANGE_RATE_UNAVAILABLE`) and nothing saved, group-currency expense `201` | Executed, passed (4 checks) |
| 4. Cache: the second equal expense does not call the BCRA again | **Not executed** (`NOT-RUN`): not observable over HTTP; covered by `GroupExpenseControllerIT.t052SecondExpenseWithSameCurrencyAndDateUsesCachedQuoteWithoutSecondApiCall` |
| 5. Simulated person: debt, balances, `403` for outsiders, partial and final payment by the creditor, `informative` transfer | Executed, passed (2 checks) |
| 6. Edit/delete: `409`s on shared expense, personal expense create/edit/delete, `403`, `404` after delete, balances unchanged, `EXPENSE_EDITED` and `EXPENSE_DELETED` | Executed, passed (3 checks) |
| 7. Overdue: `debtDueDays` `403`/`400`/`200`, scheduled job moved the debt to `OVERDUE`, `DEBT_OVERDUE` without actor, partial payment → `PARTIALLY_PAID` and remainder → `PAID` | Executed, passed (3 checks) |
| 8. Leaving with an open debt: `403 PENDING_DEBTS` | Executed, passed (1 check) |
| Auth: no session → `401` | Executed, passed (1 check) |
| All UI-driven steps (clicking through the screens in a browser, SMTP e-mail delivery) | **Not executed** (`NOT-RUN`): covered by the Vitest suite (`GroupDebtsView.test.jsx`, `RegisterPaymentView.test.jsx`, `GroupExpenseFormView.test.jsx`, `GroupEditForm.test.jsx`, `GroupExpenseDetailView.test.jsx`, …); the API calls behind them were run |

**Defect found and fixed by this run**: reporting a payment set `paid_amount = amount`, so a `PAYMENT_REPORTED` debt had `outstanding = 0` and silently dropped out of the balances and the proposal (the first run showed Carla's balance unchanged after her payment was reported). Reporting no longer touches `paid_amount` (confirming sets it, rejecting restores the state from it), and `V24__repair_reported_debts_paid_amount.sql` repairs rows written by the old behavior (checked on the real row left by the first run: `paid_amount` went from 30,00 to 0,00 on boot).

**Differences between the old quickstart and the app** (corrected here): states were named in Spanish (`PENDIENTE`, `PAGADO`, `PAGO_INFORMADO`, `VERIFICADO`, `VENCIDO`) and are `PENDING`, `PAID`, `PAYMENT_REPORTED`, `VERIFIED`, `OVERDUE`; the payment form has no date (and, under the previous rule, no editable amount; the new design makes it editable); the expense form has no category or type; the overdue transition is not limited to recurring expenses; `mvn` and `mvn flyway:migrate` do not apply (`./mvnw`, automatic Flyway); personal expenses can be edited and deleted.
