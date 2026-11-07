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
- [X] T004 [P] Configurar migraciones Flyway: asegurar que `migrations/` tiene versión base y espacio para 5 migraciones nuevas

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

- [X] T017 [P] [US1] Verificar que registrar gasto crea deudas para todos los participantes no-payer (reusando `GroupExpenseService.create()` existente) — Evidencia: `GroupExpenseServiceTest.createsTheSharesAndOnePendingDebtPerParticipantOtherThanThePayer` (2 deudas para 3 participantes, acreedor = pagador) y `GroupExpenseControllerIT.createsTheExpenseWithItsSharesAndPendingDebts` (HTTP 201 + `debts.length() == 2` + `SUM(group_expense_shares.amount) == total`)
- [X] T018 [US1] Probar que debts start en `PENDING` status después de registro de gasto — Evidencia: `GroupExpenseServiceTest.createsTheSharesAndOnePendingDebtPerParticipantOtherThanThePayer` (`Debt::getStatus == PENDING`) y `GroupExpenseControllerIT.createsTheExpenseWithItsSharesAndPendingDebts` (`debts[*].status == "PENDING"` y `SELECT status FROM debts` solo `PENDING`)
- [X] T019 [P] [US1] Probar idempotency: submitting same expense twice con mismo `idempotencyKey` devuelve resultado existente, no crea duplicado — Evidencia: `GroupExpenseServiceTest.theSameKeyAndPayloadReplaysTheExistingExpense` (`created == false`, mismo id, `nothingWasSaved()`) y `GroupExpenseControllerIT.theExpenseAppearsInTheHistoryOnceEvenWhenReplayed` (segundo POST 200, 1 `group_expenses`, 2 `debts`, 1 evento)
- [X] T020 [US1] Implementar `PATCH /api/groups/{groupId}/expenses/{expenseId}` en `GroupExpenseController` y `GroupExpenseService.updatePersonal(...)`: solo gastos sin participantes además del pagador; campos editables `amount`, `currency`, `date` y `description`; un gasto con participantes responde 409 "No se permite editar un gasto que ya tiene participantes". Tests: `GroupExpenseServiceTest` y `GroupExpenseControllerIT`. Contrato: `contracts/expense-split-and-edit.md` §2 — Evidencia: `GroupExpenseControllerIT.t020PatchEmptyBodyAnswers400`, `t020PatchExpenseWithParticipantsAnswers409`, `t020PatchPersonalExpenseUpdatesAndRecordsEvent`, `t020PatchCurrencyWithoutQuoteAnswers503WithoutSaving`, `t020AndT021PermissionsAreEnforced`.
- [X] T021 [US1] Implementar `DELETE /api/groups/{groupId}/expenses/{expenseId}` en `GroupExpenseController` y `GroupExpenseService.deletePersonal(...)`: si el gasto tiene deudas asociadas responde 409 "No se puede eliminar un gasto con deudas asociadas". Tests: `GroupExpenseServiceTest` y `GroupExpenseControllerIT`. Contrato: `contracts/expense-split-and-edit.md` §3 — Evidencia: `GroupExpenseControllerIT.t021DeleteExpenseWithDebtsAnswers409`, `t020AndT021PermissionsAreEnforced`.
- [X] T022 [P] [US1] Implementar el borrado exitoso de un gasto personal (sin participantes además del pagador) con `204 No Content`, eliminando `group_expenses` y su share personal; verificar que los saldos derivados (`BalanceCalculator`) siguen sumando cero y no se alteran por el borrado. Tests: `GroupExpenseControllerIT` y `GroupBalanceServiceTest`. Contrato: `contracts/expense-split-and-edit.md` §3 — Evidencia: `GroupExpenseControllerIT.t022DeletePersonalExpenseAnswers204AndRecordsEvents`, `t022DeletePersonalExpenseKeepsGroupBalancesZero`.
- [X] T067 [US1] Permitir crear un gasto de grupo personal (solo el pagador, sin otros participantes) sin generar deudas, como prerrequisito de T020–T022; ajustar la validación de `GroupExpenseService` y cubrirlo en `GroupExpenseServiceTest`/`GroupExpenseControllerIT`. Contrato: `contracts/expense-split-and-edit.md` §1 — Evidencia: `GroupExpenseServiceTest.atLeastOneParticipantIsRequired` y `GroupExpenseControllerIT.invalidFieldsAnswer400PerFieldWithoutWriting` (personal expense, 0 deudas).
- [X] T060 [US1] Permitir que cualquier miembro del grupo registre un gasto pagado por otro miembro conservando la casilla `members_can_generate_expenses_for_others` como permiso por grupo: los grupos nuevos arrancan con la casilla en `true` (default en la creación del grupo y migración V22, que cambia solo el DEFAULT de la columna, sin tocar filas existentes) y los grupos existentes mantienen su valor; validar en servidor que pagador y participantes sean miembros del grupo; mantener el 403 con el mensaje "Solo podés registrar gastos pagados por vos" cuando la casilla está en `false` y agregar el caso con la casilla en `true`; cubrir con tests que `createdByUserId` y `payerUserId` quedan separados — Evidencia: `GroupServiceTest.creatorBecomesAdminAndFriendJoinsAsMember`, `GroupExpenseControllerIT.theV9CasillasAreEnforced`, `t060NewGroupStartsWithExpensesForOthersTrueAndEnforcesActivePayer`, `GroupExpenseMigrationIT.v23DefaultMembersCanGenerateExpensesForOthersIsTrue`.

---
## Phase 5: User Story 2 - Definir cómo se reparte el gasto (Priority: P1)

**Goal**: Verify that EqualSplit handles equal, custom, and percentage divisions correctly  
**Independent Test**: After split operation, share amounts sum to total expense amount

- [X] T023 [P] [US2] Implementar el reparto `EQUAL`/`CUSTOM` en backend: nuevo enum `SplitMethod` (`EQUAL` por defecto, `CUSTOM`, `PERCENTAGE`), `CreateGroupExpenseRequest` acepta `splitMethod` y `amount` por participante, y `GroupExpenseService` calcula las shares de `CUSTOM` de modo que sumen exactamente el total. Archivos: `SplitMethod.java`, `CreateGroupExpenseRequest.java`, `GroupExpenseService.java`. Tests: `GroupExpenseServiceTest` y nuevo `GroupExpenseSplitTest`. Contrato: `contracts/expense-split-and-edit.md` §1 — Evidencia: `GroupExpenseServiceTest.customSplitKeepsTheRequestedSharesAndPersistsTheMethod`, `GroupExpenseControllerIT.t023CustomSplitPersistsTheRequestedShares`, `GroupExpenseSplitTest.customSharesAlwaysSumTheTotalForGeneratedAmounts`.
- [X] T024 [P] [US2] Implementar el reparto `PERCENTAGE` en backend: `percentage` por participante, cada uno `> 0`, suma exacta de 100, y cálculo de shares con piso al centavo y resto al pagador (Q1). Errores `400 ProblemDetail` voseo: "Cada porcentaje tiene que ser mayor que 0" y "Los porcentajes deben sumar exactamente 100". Archivos: `GroupExpenseService.java`, `CreateGroupExpenseRequest.java`. Tests: `GroupExpenseServiceTest` y `GroupExpenseSplitTest`. Contrato: `contracts/expense-split-and-edit.md` §1
- [X] T025 [US2] Probar redondeo: cada parte se calcula en centavos hacia abajo (piso) y el pagador absorbe el resto, de modo que ningún participante no pagador pague de más por redondeo (clarificación Q1) — Evidencia: `EqualSplitTest.thePayerAbsorbsTheRemainderWhenParticipating` (100.00/3 → 3333.34/3333.33/3333.33 y 0.05/3), `theSumIsAlwaysExactlyTheTotal` (múltiples totales y participantes, con y sin pagador) y `everyShareHasTwoDecimals`
- [ ] T026 [P] [US2] Implementar la UI de reparto en `GroupExpenseFormView.jsx`: selector `EQUAL`/`CUSTOM`/`PERCENTAGE`, inputs de importe o porcentaje por participante, diferencia visible, mensajes de validación y botón de guardar deshabilitado hasta que la suma sea exacta (total para `CUSTOM`, 100 para `PERCENTAGE`). Tests: `GroupExpenseFormView.test.jsx`. Contrato: `contracts/expense-split-and-edit.md` §1
- [X] T064 [P] [US2] Agregar tests de propiedad del reparto `CUSTOM`/`PERCENTAGE`: la suma de las shares es siempre exactamente el total y ningún participante no pagador queda por encima de su parte exacta. Archivo: nuevo `GroupExpenseSplitTest.java` (o `EqualSplitTest` si el cálculo se extrae a una utilidad pura). Contrato: `contracts/expense-split-and-edit.md` §1
- [ ] T065 [P] [US2] Extraer los helpers de cálculo `CUSTOM`/`PERCENTAGE` del frontend (por ejemplo `frontend/src/pages/groups/splitCustom.js`) con tests unitarios Vitest de suma exacta y resto al pagador, reutilizables por la UI de T026. Contrato: `contracts/expense-split-and-edit.md` §1
- [X] T066 [US2] Persistir y exponer `splitMethod`: agregar `splitMethod` a `GroupExpense`/`GroupExpenseResponse` y la migración nueva `V21__add_group_expense_split_method.sql` (`split_method VARCHAR(10) NOT NULL DEFAULT 'EQUAL'` + CHECK), sin editar migraciones existentes. Tests: `GroupExpenseControllerIT`. Contrato: `contracts/expense-split-and-edit.md` §1 y *Compatibility and migration* — Evidencia: `GroupExpenseControllerIT.t066TheSplitMethodDefaultsToEqualForLegacyPayloads` y `GroupExpenseMigrationIT.theSplitMethodDefaultsToEqualAndOnlyAcceptsTheThreeKnownValues`.

**Orden de dependencias (sin cambiar casillas)**: `T066 → T023 → T024 → T064` para el reparto; `T067 → T020 → T021 → T022` para el gasto personal y su edición/borrado; `T065 → T026` para los helpers y la UI de reparto.

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

- [X] T032 [P] [US5] Implementar endpoint `POST /groups/{groupId}/payments/{paymentId}/confirm` (solo admin)
- [X] T033 [P] [US5] Implementar endpoint `POST /groups/{groupId}/payments/{paymentId}/reject` (solo admin)
- [X] T034 [US5] Probar que pago sin validación → debt state → `PAID` inmediatamente
- [X] T035 [P] [US5] Probar que pago con validación → debt state → `PAYMENT_REPORTED`; admin confirma → `VERIFIED`; admin rechaza → vuelve a `PENDING`
- [X] T036 [P] [US5] Probar que `PAID` y `VERIFIED` son equivalentes para cálculos de saldo (Q7 invariant)
- [X] T037 [US5] Probar que deuda `OVERDUE` aún se puede pagar y sigue contando en saldo
- [X] T063 [US5] Implementar job programado idempotente que pasa deudas `PENDING` a `OVERDUE` según `debtDueDays` del grupo

---
## Phase 8: User Story 6 - Registrar pago de deuda pendiente (Priority: P2)

**Goal**: Verify that only full payments are allowed (no partial payments per clarification Q5)  
**Independent Test**: Attempting to register payment < outstanding amount fails; payment = outstanding amount succeeds

- [X] T038 [P] [US6] Implementar validación: payment amount must equal exactly `debt.getOutstanding()`; reject if `amount < outstanding` or `amount > outstanding` with 400 error
- [X] T039 [US6] Probar que pago parcial (amount < outstanding) es rechazado con mensaje claro
- [X] T040 [P] [US6] Probar idempotency: mesma `idempotencyKey` sobre deuda misma → returns existing result, no duplicate
- [X] T041 [US6] Probar que clave distinta sobre deuda ya reportada/saldada → 409 "Esta deuda ya tiene un pago registrado"
- [X] T042 [P] [US6] Probar concurrency: dos pagos concurrentes sobre la misma deuda con claves diferentes → solo uno se aplica (PESSIMISTIC_WRITE lock)

---
## Phase 9: User Story 7 - Ver mis deudas pendientes (Priority: P3)

**Goal**: Verify that pending debts are listed with origin and detail  
**Independent Test**: After registering expenses with different payers, pending debts list shows correct origin info

- [X] T043 [P] [US7] Implementar endpoint `GET /groups/{groupId}/debts/pending` que devuelva deudas con status `PENDING`, `PARTIALLY_PAID`, `PAYMENT_REPORTED` u `OVERDUE`, igual que el cálculo de saldos
- [X] T044 [US7] Probar que deuda incluye origen: `expenseId`, `shareId`, `currency`, amount, paidAmount, outstanding
- [X] T045 [P] [US7] Probar que persona simulada aparece en listado de deudas pendientes con su nombre

---
## Phase 10: User Story 8 - Mantener historial de actividad del grupo (Priority: P3)

**Goal**: Verify that activity history reflects events correctly  
**Independent Test**: After expense/payment/liquidation operations, activity history shows correct events

- [X] T046 [P] [US8] Probar que evento `EXPENSE_CREATED` aparece en historial después de registrar gasto
- [X] T047 [P] [US8] Probar que evento `PAYMENT_REGISTERED` aparece en historial después de registrar pago
- [X] T048 [P] [US8] Probar que evento `LIQUIDATION_PROPOSED` aparece en historial después de generar propuesta
- [X] T049 [US8] Probar que el historial conserva el camino recorrido de transiciones de estado (Q7)
- [X] T061 [US8] Incluir al pagador en el evento `EXPENSE_CREATED` y mostrar en el historial tanto a quien registró el gasto como al pagador (D11)

---
## Phase 11: User Story 9 - Operar en varias monedas (Priority: P2)

**Goal**: Verify that expenses in different currencies show both original and converted amounts  
**Independent Test**: Expense in USD shows "$50 USD - $925 ARS"; exchange rate stored and visible; if the BCRA API has no quote within 7 days → 503 error, expense not saved

- [X] T050 [P] [US9] Implementar lógica: si `expense.currency != group.primaryCurrency` → fetch rate; si falla 503 y no guarda gasto
- [X] T051 [US9] Probar expense en moneda distinta: muestra monto original + convertido + tasa aplicada
- [X] T052 [P] [US9] Probar rate cache: generar mismo expense en misma fecha usa cotización cached (sin segunda API call)
- [X] T053 [US9] Probar BCRA unavailable o sin cotización dentro de 7 días → 503 "No se pudo obtener la cotización; reintente después o registre en moneda del grupo"
- [X] T054 [P] [US9] Probar misma moneda: tasa 1, sin llamada externa, `convertedAmount` = `amount`

---
## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T055 [P] Documentar invariantes clave en README: `Σ(balances) == 0`; transfer count ≤ n-1; PAYMENT_REPORTED ≠ PAID; OVERDUE debts still count as saldo
- [ ] T056 [P] Ejecutar validación completa quickstart.md y corregir cualquier fallo
- [ ] T057 [P] Añadir cobertura FR/SC tabla al final de tasks.md (ver abajo)
- [X] T058 [P] Revisar que no hay numeración de ID duplicada en tasks.md
- [ ] T059 [P] Confirmar integración con feature 020: `GroupService.leaveGroup` bloquea si `hasOutstandingBalance(groupId, member)` es true

---
## Phase 12: Frontend (022)

- [ ] T068 [P] [US9] Selector de moneda principal (`primaryCurrency`) en la creación del grupo: verificar que `POST /api/groups` acepta `primaryCurrency` (backend ya expone el campo); si no, tarea de backend aparte. Archivos: `frontend/src/pages/groups/GroupCreateForm.jsx` (o equivalente), `frontend/src/api/groupsClient.js`. Tests: `GroupCreateForm.test.jsx`. Contrato: `contracts/expense-split-and-edit.md` §1.
- [ ] T069 [P] [US9] Backend: `GET /api/groups/{groupId}/exchange-quote?currency=&date=` de solo lectura (tasa, fecha efectiva, fuente; 503 sin cotización; solo miembros). Archivos: `GroupExpenseController.java`, `GroupExpenseService.java`. Tests: `GroupExpenseControllerIT`. Contrato: `contracts/expense-split-and-edit.md` §1.
- [ ] T070 [P] [US9] En el formulario de gasto, vista previa del monto convertido con la cotización de `exchange-quote`, con aviso de que la cotización final se fija al guardar. Archivos: `frontend/src/pages/groups/GroupExpenseFormView.jsx`, `frontend/src/components/debts/ConvertedAmount.jsx`. Tests: `GroupExpenseFormView.test.jsx`.
- [ ] T071 [P] [US2] Helpers de reparto frontend (`frontend/src/pages/groups/splitCustom.js`, `splitPercentage.js`) con tests Vitest de suma exacta y resto al pagador. Archivos: helpers + tests. Contrato: `contracts/expense-split-and-edit.md` §1. (reemplaza T065)
- [ ] T072 [P] [US2] UI de reparto en `GroupExpenseFormView.jsx`: selector EQUAL/CUSTOM/PERCENTAGE, inputs por participante, diferencia visible, botón deshabilitado hasta suma exacta (total para CUSTOM, 100 para PERCENTAGE). Tests: `GroupExpenseFormView.test.jsx`. Contrato: §1. (reemplaza T026)
- [ ] T073 [P] [US2] Selector de pagador en el formulario de gasto respetando la casilla `members_can_generate_expenses_for_others` del grupo. Archivos: `GroupExpenseFormView.jsx`, `frontend/src/api/groupsClient.js`. Tests: `GroupExpenseFormView.test.jsx`.
- [ ] T074 [P] [US1] `GroupDebtsView.jsx` con pestañas (mis deudas pendientes, saldos, propuesta de liquidación, "Pagos por validar" solo admins), responsive, estados con `debtStatusLabels.js`. Archivos: `frontend/src/pages/groups/GroupDebtsView.jsx`, `frontend/src/pages/groups/debtStatusLabels.js`. Tests: `GroupDebtsView.test.jsx`, `debtStatusLabels.test.js`.
- [ ] T075 [P] [US6] Registrar un pago completo desde una deuda; confirmar y rechazar pagos (solo admin). Archivos: `frontend/src/components/groups/...`, `frontend/src/api/paymentsClient.js`. Tests: Vitest.
- [ ] T076 [P] [US8] Historial: mostrar eventos nuevos (pago registrado, confirmado, rechazado, deuda vencida, liquidación propuesta, gasto editado, gasto borrado). Archivos: `frontend/src/pages/groups/GroupHistoryView.jsx`, `frontend/src/pages/groups/activityDescriptions.js`. Tests: `GroupHistoryView.test.jsx`, `activityDescriptions.test.js`.
- [ ] T077 [P] [US1] Editar y borrar un gasto personal desde la UI (PATCH/DELETE). Archivos: `frontend/src/pages/groups/GroupExpenseFormView.jsx` o detalle. Tests: Vitest.
- [ ] T078 [P] [US1] Configurar `debtDueDays` del grupo en la UI (si el backend ya lo expone). Archivos: `frontend/src/components/groups/GroupEditForm.jsx`. Tests: `GroupEditForm.test.jsx`.

## Coverage Table: FR-SC Alignment

`Complete` requires every mapped task to be marked `[X]`; otherwise the requirement stays `Pending`.

| Requirement | Status | Mapped tasks | Description |
|-------------|--------|--------------|-------------|
| **FR-001** | Pending | T017-T022, T060, T067 | Registrar gasto compartido; T017-T019 completas; T020-T022 (edición/borrado) y T067 (gasto personal) pendientes según `contracts/expense-split-and-edit.md` §2–§3 |
| **FR-002** | Pending | T023-T026, T064-T066 | Definir reparto; T025 completa; `EQUAL`/`CUSTOM`/`PERCENTAGE`, validaciones, persistencia y UI pendientes según `contracts/expense-split-and-edit.md` §1 |
| **FR-003** | Complete | T012-T016 | Saldos consistentes con `BalanceCalculator`, endpoint y cero-sum |
| **FR-004** | Complete | T027-T031 | Liquidación con conjunto reducido de transferencias y solo lectura |
| **FR-005** | Complete | T032-T037, T063 | Gestión de estado de deuda; confirmación/rechazo de pagos, transiciones verificadas y vencimiento programado |
| **FR-006** | Complete | T038-T042 | Registrar pago; flujo de pago completo con validación, idempotencia, 409 y concurrencia |
| **FR-007** | Complete | T043-T045 | Ver deudas pendientes; listado con origen, moneda y persona simulada |
| **FR-008** | Complete | T046-T049, T061 | Historial de actividad; eventos de gastos (con pagador), pagos, liquidación y transiciones de estado |
| **FR-009** | Complete | T050-T054 | Conversión multi-currency; `BcraExchangeRateClient`, caché por (par, fecha), retroceso hasta 7 días y 503 sin guardar |
| **FR-010** | Pending | T059 | Restricción de salida con saldo pendiente; T059 sin completar |
| **SC-001** | Complete | T009, T012-T016 | La suma de saldos es cero, incluida la persona simulada |
| **SC-002** | Complete | T027-T031 | Transferencias ≤ M-1 y total igual a la suma de saldos positivos |
| **SC-003** | Complete | T012-T016 | Saldo neto por miembro expuesto sin cálculos manuales |
| **SC-004** | Complete | T034, T035, T047, T049 | Pago informado en estado e historial; transiciones registradas en el historial de actividad |
