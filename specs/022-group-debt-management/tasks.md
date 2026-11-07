# Tasks: Group Debt Management (022)

**Feature**: Group Debt Management  
**Based on**: plan.md, spec.md  
**Status**: Generated for implementation  
**Generated**: 2026-11-06

---
## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [X] T001 Verificar código existente: confirmar que `Debt`, `DebtStatus`, `Payment` con `of()`, `EqualSplit` y `ActivityEventService` ya existen (según verification prerequisites del plan.md)
- [X] T002 [P] [US9] Configurar entorno BCRA: añadir `BCRA_BASE_URL` (API pública, sin credenciales) a `.env.example` y Docker Compose para story 9 (multi-currency)
- [X] T003 [P] [US9] Crear estructura de paquetes frontend: `frontend/src/components/debts/`, `frontend/src/pages/groups/GroupDebtsView.jsx`
- [X] T004 [P] Configurar migraciones Flyway: asegurar que `migrations/` tiene versión base y espacio para 3 migraciones nuevas

---
## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T005 [P] [US1-US9] Añadir campo `primaryCurrency` a entity `Group` y migración V17 (con default para grupos existentes)
- [X] T006 [P] [US1-US9] Añadir campo `debtDueDays` (Integer, nullable) a entity `Group`; la misma V17 agrega la columna una sola vez
- [X] T007 [P] [US1] Extender enum `DebtStatus` a 6 estados (`PENDING`, `PARTIALLY_PAID`, `PAYMENT_REPORTED`, `PAID`, `VERIFIED`, `OVERDUE`) y recrear los CHECK de `debts`
- [X] T008 [P] [US6] Extender la entity/tabla existente `Payment`/`payments` con `status`, `decided_by_user_id`, `decided_at` y `legacy`; los pagos existentes quedan `CONFIRMED` + legacy y el índice único parcial por `debt_id` excluye los legacy
- [X] T009 [P] [US1] Implementar `BalanceCalculator` como función pura que recibe lista de deudas y devuelve mapa `<memberId, BigDecimal>` con invariante `Σ(balances) == 0`
- [X] T010 [P] [US9] Implementar `ConvertedAmount` frontend component that muestra "$50 USD · $925 ARS" cuando la moneda difiere
- [X] T011 [P] [US1-US9] Configurar `Clock` inyectable para todos los cálculos de fechas (vencimiento, cotizaciones históricas)

---
## Phase 3: User Story 3 - Ver cálculo automático de deudas (Priority: P1)

**Goal**: Verify that group balance sums to zero after expense/payment registration  
**Independent Test**: After registering an expense or payment, compute `Σ(balance_i)` for all group members (including simulated persons); result must be exactly 0

- [X] T012 [P] [US3] Implementar endpoint `GET /groups/{groupId}/balances` que devuelva saldo neto por miembro usando `BalanceCalculator` pura
- [X] T013 [US3] Probar que balances suman cero después de registrar gasto con participantes múltiples
- [X] T014 [P] [US3] Probar que balances suman cero después de registrar pago (con y sin validación)
- [X] T015 [US3] Probar que balances suman cero después de liquidación
- [X] T016 [P] [US3] Integrar persona simulada en cálculo de saldos (contribuye al balance vía `debtorSimulatedProfileId`)

---
## Phase 4: User Story 1 - Registrar un gasto compartido (Priority: P1)

**Goal**: Verify that registering a shared expense creates debts for all non-payer participants  
**Independent Test**: After registering an expense, 2 debts are created (one for each non-payer participant); debts start in `PENDING` status; group balance sums to zero

- [ ] T017 [P] [US1] Verificar que registrar gasto crea deudas para todos los participantes no-payer (reusando `GroupExpenseService.create()` existente)
- [ ] T018 [US1] Probar que debts start en `PENDING` status después de registro de gasto
- [ ] T019 [P] [US1] Probar idempotency: submitting same expense twice con mismo `idempotencyKey` devuelve resultado existente, no crea duplicado
- [ ] T020 [US1] Probar restricción: editar gasto con participantes → 409 "No se permite editar un gasto que ya tiene participantes"
- [ ] T021 [US1] Probar restricción: eliminar gasto con deudas asociadas → 409 "No se puede eliminar un gasto con deudas asociadas"
- [ ] T022 [P] [US1] Probar delete personal expense (sin participantes) succeeds y balances recalculados
- [ ] T060 [US1] Permitir que cualquier miembro del grupo registre un gasto pagado por otro miembro conservando la casilla `members_can_generate_expenses_for_others` como permiso por grupo: los grupos nuevos arrancan con la casilla en `true` (default en la creación del grupo y migración V20 que cambia solo el DEFAULT de la columna, sin tocar filas existentes) y los grupos existentes mantienen su valor; validar en servidor que pagador y participantes sean miembros del grupo; mantener el 403 con el mensaje "Solo podés registrar gastos pagados por vos" cuando la casilla está en `false` y agregar el caso con la casilla en `true`; cubrir con tests que `createdByUserId` y `payerUserId` quedan separados

---
## Phase 5: User Story 2 - Definir cómo se reparte el gasto (Priority: P1)

**Goal**: Verify that EqualSplit handles equal, custom, and percentage divisions correctly  
**Independent Test**: After split operation, share amounts sum to total expense amount

- [ ] T023 [P] [US2] Verificar `EqualSplit.split()` produce shares that sum to total (partes iguales, personalizados y porcentajes exactly 100%)
- [ ] T024 [P] [US2] Implementar validación de porcentajes: suma exacta 100% en registro de gasto
- [ ] T025 [US2] Probar redondeo: cada parte se redondea al centavo (`HALF_UP`) y pagador absorbe la diferencia (clarificación Q1)
- [ ] T026 [P] [US2] Implementar validación en frontend: importes personalizados deben sumar exactamente el total

---
## Phase 6: User Story 4 - Simplificar deudas (liquidación) (Priority: P2)

**Goal**: Verify that liquidation proposal suggests minimum transfers (≤ n-1) to settle all debts  
**Independent Test**: After liquidation, count members with balance ≠ 0 (call it M); verify `transferCount ≤ M - 1`; total transferred equals total net needed to settle

- [X] T027 [P] [US4] Implementar endpoint `GET /groups/{groupId}/settlement-proposal` (sin cuerpo, GET solo) que devuelva propuesta de liquidación
- [X] T028 [US4] Probar que transfer count ≤ (nonZeroMembers - 1) [SC-002]
- [X] T029 [P] [US4] Probar que total transferred = sum of positive net balances (no la deuda bruta, que es mayor con deudas cruzadas)
- [X] T030 [US4] Probar ejemplo correcto: Alice→Bob $5, Alice→Claire $2; total transferred $7, transfer count 2 = 3-1 ✓
- [X] T031 [P] [US4] Probar que propuesta NO modifica gastos ni records de deudas (solo lectura)

---
## Phase 7: User Story 5 - Gestionar estado de cada deuda (Priority: P2)

**Goal**: Verify debt state transitions follow group's "Pagos requiere validación" setting  
**Independent Test**: After payment registration, debt state is `PAYMENT_REPORTED` (con validación) o `PAID` (sin validación); state transitions confirmed/rejected by admin work correctly

- [ ] T032 [P] [US5] Implementar endpoint `POST /groups/{groupId}/payments/{paymentId}/confirm` (solo admin)
- [ ] T033 [P] [US5] Implementar endpoint `POST /groups/{groupId}/payments/{paymentId}/reject` (solo admin)
- [ ] T034 [US5] Probar que pago sin validación → debt state → `PAID` inmediatamente
- [ ] T035 [P] [US5] Probar que pago con validación → debt state → `PAYMENT_REPORTED`; admin confirma → `VERIFIED`; admin rechaza → vuelve a `PENDING`
- [ ] T036 [P] [US5] Probar que `PAID` y `VERIFIED` son equivalentes para cálculos de saldo (Q7 invariant)
- [ ] T037 [US5] Probar que deuda `OVERDUE` aún se puede pagar y sigue contando en saldo

---
## Phase 8: User Story 6 - Registrar pago de deuda pendiente (Priority: P2)

**Goal**: Verify that only full payments are allowed (no partial payments per clarification Q5)  
**Independent Test**: Attempting to register payment < outstanding amount fails; payment = outstanding amount succeeds

- [ ] T038 [P] [US6] Implementar validación: payment amount must equal exactly `debt.getOutstanding()`; reject if `amount < outstanding` or `amount > outstanding` with 400 error
- [ ] T039 [US6] Probar que pago parcial (amount < outstanding) es rechazado con mensaje claro
- [ ] T040 [P] [US6] Probar idempotency: mesma `idempotencyKey` sobre deuda misma → returns existing result, no duplicate
- [ ] T041 [US6] Probar que clave distinta sobre deuda ya reportada/saldada → 409 "Esta deuda ya tiene un pago registrado"
- [ ] T042 [P] [US6] Probar concurrency: dos pagos concurrentes sobre la misma deuda con claves diferentes → solo uno se aplica (PESSIMISTIC_WRITE lock)

---
## Phase 9: User Story 7 - Ver mis deudas pendientes (Priority: P3)

**Goal**: Verify that pending debts are listed with origin and detail  
**Independent Test**: After registering expenses with different payers, pending debts list shows correct origin info

- [ ] T043 [P] [US7] Implementar endpoint `GET /groups/{groupId}/debts/pending` que devuelva deudas con status `PENDING`, `PARTIALLY_PAID`, `PAYMENT_REPORTED` u `OVERDUE`, igual que el cálculo de saldos
- [ ] T044 [US7] Probar que deuda incluye origen: `expenseId`, `shareId`, `currency`, amount, paidAmount, outstanding
- [ ] T045 [P] [US7] Probar que persona simulada aparece en listado de deudas pendientes con su nombre

---
## Phase 10: User Story 8 - Mantener historial de actividad del grupo (Priority: P3)

**Goal**: Verify that activity history reflects events correctly  
**Independent Test**: After expense/payment/liquidation operations, activity history shows correct events

- [ ] T046 [P] [US8] Probar que evento `EXPENSE_CREATED` aparece en historial después de registrar gasto
- [ ] T047 [P] [US8] Probar que evento `PAYMENT_REGISTERED` aparece en historial después de registrar pago
- [ ] T048 [P] [US8] Probar que evento `LIQUIDATION_PROPOSED` aparece en historial después de generar propuesta
- [ ] T049 [US8] Probar que el historial conserva el camino recorrido de transiciones de estado (Q7)
- [ ] T061 [US8] Incluir al pagador en el evento `EXPENSE_CREATED` y mostrar en el historial tanto a quien registró el gasto como al pagador (D11)

---
## Phase 11: User Story 9 - Operar en varias monedas (Priority: P2)

**Goal**: Verify that expenses in different currencies show both original and converted amounts  
**Independent Test**: Expense in USD shows "$50 USD - $925 ARS"; exchange rate stored and visible; if the BCRA API has no quote within 7 days → 503 error, expense not saved

- [ ] T050 [P] [US9] Implementar lógica: si `expense.currency != group.primaryCurrency` → fetch rate; si falla 503 y no guarda gasto
- [ ] T051 [US9] Probar expense en moneda distinta: muestra monto original + convertido + tasa aplicada
- [ ] T052 [P] [US9] Probar rate cache: generar mismo expense en misma fecha usa cotización cached (sin segunda API call)
- [ ] T053 [US9] Probar BCRA unavailable o sin cotización dentro de 7 días → 503 "No se pudo obtener la cotización; reintente después o registre en moneda del grupo"
- [ ] T054 [P] [US9] Probar misma moneda: tasa 1, sin llamada externa, `convertedAmount` = `amount`

---
## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T055 [P] Documentar invariantes clave en README: `Σ(balances) == 0`; transfer count ≤ n-1; PAYMENT_REPORTED ≠ PAID; OVERDUE debts still count as saldo
- [ ] T056 [P] Ejecutar validación completa quickstart.md y corregir cualquier fallo
- [ ] T057 [P] Añadir cobertura FR/SC tabla al final de tasks.md (ver abajo)
- [ ] T058 [P] Revisar que no hay numeración de ID duplicada en tasks.md
- [ ] T059 [P] Confirmar integración con feature 020: `GroupService.leaveGroup` bloquea si `hasOutstandingBalance(groupId, member)` es true

---
## Coverage Table: FR-SC Alignment

`Complete` requires every mapped task to be marked `[X]`; otherwise the requirement stays `Pending`.

| Requirement | Status | Mapped tasks | Description |
|-------------|--------|--------------|-------------|
| **FR-001** | Pending | T017-T022, T060 | Registrar gasto compartido; tareas de US1 sin completar |
| **FR-002** | Pending | T023-T026 | Definir reparto; tareas de US2 sin completar |
| **FR-003** | Complete | T012-T016 | Saldos consistentes con `BalanceCalculator`, endpoint y cero-sum |
| **FR-004** | Complete | T027-T031 | Liquidación con conjunto reducido de transferencias y solo lectura |
| **FR-005** | Pending | T032-T037 | Gestión de estado de deuda; tareas de US5 sin completar |
| **FR-006** | Pending | T038-T042 | Registrar pago; tareas de US6 sin completar |
| **FR-007** | Pending | T043-T045 | Ver deudas pendientes; tareas de US7 sin completar |
| **FR-008** | Pending | T046-T049, T061 | Historial de actividad; tareas de US8 sin completar |
| **FR-009** | Pending | T050-T054 | Conversión multi-currency; tareas de US9 sin completar |
| **FR-010** | Pending | T059 | Restricción de salida con saldo pendiente; T059 sin completar |
| **SC-001** | Complete | T009, T012-T016 | La suma de saldos es cero, incluida la persona simulada |
| **SC-002** | Complete | T027-T031 | Transferencias ≤ M-1 y total igual a la suma de saldos positivos |
| **SC-003** | Complete | T012-T016 | Saldo neto por miembro expuesto sin cálculos manuales |
| **SC-004** | Pending | T034, T035, T047, T049 | Pago informado en estado e historial; tareas de US5/US6/US8 sin completar |
