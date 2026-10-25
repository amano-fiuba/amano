# Tasks: Pausar o finalizar un gasto recurrente

**Input**: Design documents from `/specs/009-pause-recurring-expense/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/recurring-expenses-api.md, quickstart.md

**Tests**: Incluidos por el principio V de la constitución (research.md §D13).

**Organization**: Tareas agrupadas por historia de usuario.

**Estado**: Implementación y pruebas en el árbol de trabajo de `feat/definir-tipo-gasto` (sin commitear); `mvn -B verify` y `npm run test:coverage` en verde. Pendientes `[ ]`: validación manual, commits y PR.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Paralelizable (archivos distintos, sin dependencias entre tareas incompletas)
- **[Story]**: US1–US4; sin etiqueta en Setup, Foundational y Polish

## Phase 1: Setup

- [X] T001 Crear `backend/src/main/resources/db/migration/V5__expense_recurrence.sql`: columnas `recurrence_frequency`, `recurrence_status`, `recurrence_next_date`, `recurrence_source_id`; CHECKs `ck_expenses_recurrence_{frequency,status,series,type,next_date,occurrence}`; FK `fk_expenses_recurrence_source` (`ON DELETE SET NULL`); `uq_expenses_recurrence_occurrence`; índices parciales `ix_expenses_recurrence_due` e `ix_expenses_recurrence_owner`; sin `UPDATE`/`DELETE` — FR-005..FR-009, FR-015, data-model.md
- [X] T002 [P] Agregar `recurrence.zone` y `recurrence.cron` a `backend/src/main/resources/application.properties`, crear el record `backend/src/main/java/com/amano/backend/expense/RecurrenceProperties.java` y registrarlo en `BackendApplication.java` — FR-005, research.md §D7/§D8

---

## Phase 2: Foundational

**⚠️ CRITICAL**: bloquea todas las historias.

- [X] T003 [P] Crear `expense/RecurrenceFrequency.java` (`WEEKLY`/`MONTHLY`/`YEARLY`) con `nth`, `firstAfter` y `firstOnOrAfter` anclados al inicio — FR-003, FR-004, FR-008, research.md §D5/§D6
- [X] T004 [P] Crear `expense/RecurrenceStatus.java` (`ACTIVE`/`PAUSED`/`FINISHED`) — FR-007
- [X] T005 Agregar a `expense/Expense.java` las 4 columnas mapeadas e `isRecurringSeries()`; a `ExpenseResponse.java` `frequency`, `status`, `nextOccurrenceDate` (omitidos si son `null`) — data-model.md, research.md §D11
- [X] T006 Agregar a `expense/ExpenseRepository.java` `findByUserIdAndRecurrenceFrequencyIsNotNullOrderByCreatedAtDescIdDesc` y `findByRecurrenceStatusAndRecurrenceNextDateLessThanEqualOrderByIdAsc` — FR-005, FR-012
- [X] T007 [P] Crear `backend/src/test/java/com/amano/backend/expense/RecurrenceFrequencyTest.java` (8 casos: semanal, mensual, no retroactivo, fin de mes, 29/02, `nth`, `firstOnOrAfter`) — FR-003, FR-004, FR-008
- [X] T008 Adaptar `ExpenseSchemaIT.flywayCreatesTheExpensesTableWithTheExpectedColumns` a las columnas de V5 — plan.md §Complexity Tracking

**Checkpoint**: Hibernate valida la entidad contra V5 (`ddl-auto=validate`); 007 sigue en verde.

---

## Phase 3: User Story 1 - Registrar un gasto recurrente con frecuencia (Priority: P1) 🎯 MVP

**Goal**: `RECURRING` exige frecuencia y nace como serie `ACTIVE` con próxima fecha no retroactiva; `ONE_TIME` no admite frecuencia.

**Independent Test**: registrar un recurrente mensual con fecha de hoy → `status = ACTIVE`, `nextOccurrenceDate` = dentro de un mes; recurrente sin frecuencia → 400.

### Tests for User Story 1 ⚠️

- [X] T009 [P] [US1] Crear `expense/ExpenseRecurrenceRegistrationTest.java` (10 casos: serie activa, inicio pasado no retroactivo, frecuencia faltante/en blanco/inválida, `ONE_TIME` con frecuencia, `ONE_TIME` sin columnas nuevas, acumulación con errores de 007, tipo inválido, conflicto de idempotencia por frecuencia) — FR-001..FR-003
- [X] T010 [P] [US1] Ampliar `frontend/src/components/ExpenseForm.test.jsx` (5 casos: visibilidad de "Frecuencia", obligatoria, envío y reset, no enviada para "Único", error de servidor junto al campo) — FR-001, FR-002
- [X] T011 [P] [US1] Ampliar `frontend/src/validation/expenseCatalog.test.js` con `FREQUENCY_OPTIONS` y `STATUS_OPTIONS` — FR-001, FR-007
- [X] T012 [US1] Adaptar los tests de 007 a la regla nueva: payload `RECURRING` con `frequency` en `RegisterExpenseIT`, mutación de tipo con frecuencia y `storedExpense` con frecuencia en `ExpenseServiceTest` — plan.md §Complexity Tracking

### Implementation for User Story 1

- [X] T013 [US1] Agregar `frequency` a `expense/RegisterExpenseRequest.java` — contracts §POST
- [X] T014 [US1] En `expense/ExpenseService.java`: `parseFrequency` (obligatoria para `RECURRING`, prohibida para `ONE_TIME`, catálogo), frecuencia en el payload normalizado y en `samePayload`, y al guardar una serie `status = ACTIVE` + `nextDate = firstAfter(fecha, hoy del dispositivo)` — FR-001..FR-003, research.md §D4/§D6/§D8
- [X] T015 [P] [US1] Agregar `FREQUENCY_OPTIONS` y `STATUS_OPTIONS` a `frontend/src/validation/expenseCatalog.js` — FR-001, FR-007
- [X] T016 [US1] En `frontend/src/components/ExpenseForm.jsx`: `useWatch` del tipo, `SelectField` "Frecuencia" solo para "Recurrente" con `FREQUENCY_REQUIRED_MESSAGE`, `frequency` en el payload solo para `RECURRING`, en el reset y en `FIELD_NAMES` — FR-001, research.md §D12

**Checkpoint**: se registran series activas desde el formulario.

---

## Phase 4: User Story 2 - Generación automática de las repeticiones (Priority: P1)

**Goal**: cada día se registran las repeticiones vencidas de las series activas, sin duplicados.

**Independent Test**: serie activa con próxima fecha hoy → la generación crea un gasto de hoy vinculado a la serie y avanza la próxima fecha; una segunda corrida no duplica.

### Tests for User Story 2 ⚠️

- [X] T017 [P] [US2] Casos de generación en `expense/RecurringExpenseServiceTest.java` (copia de datos y avance, recuperación de fechas vencidas, fin de mes, claves determinísticas, nada vencido) — FR-005, FR-006
- [X] T018 [P] [US2] Casos de generación en `expense/RecurringExpenseIT.java` (`activeSeriesCatchesUpEveryDueDate`, `pastStartDateIsNotRetroactive`, segunda corrida sin duplicados) — FR-003, FR-005, FR-006, SC-002

### Implementation for User Story 2

- [X] T019 [US2] Crear `expense/RecurringExpenseService.java` con `Clock` de la zona configurada, `today()` y `generateDueOccurrences(hoy)` (una repetición por fecha vencida, `occurrenceKey` UUID v3, límite de 1000 por serie y corrida) — FR-005, FR-006, research.md §D7
- [X] T020 [US2] Crear `expense/RecurringExpenseScheduler.java` (`@Scheduled(cron = "${recurrence.cron}", zone = "${recurrence.zone}")`) — FR-005

**Checkpoint**: las series activas generan sus gastos a diario.

---

## Phase 5: User Story 3 - Ver mis gastos recurrentes y pausarlos o reanudarlos (Priority: P1)

**Goal**: pantalla "Gastos recurrentes" con "Pausar" / "Reanudar"; una serie pausada no genera y al reanudarse no recupera la pausa.

**Independent Test**: pausar → la generación no crea nada; reanudar → próxima fecha hoy o posterior.

### Tests for User Story 3 ⚠️

- [X] T021 [P] [US3] Casos de estado en `RecurringExpenseServiceTest.java` (pausar conserva la fecha, reanudar salta la pausa, reanudar en fecha de la serie = hoy, mismo estado sin cambios, 404 para lo que no es una serie propia, estado inválido, listado) — FR-007, FR-008, FR-010, FR-011
- [X] T022 [P] [US3] Casos HTTP en `RecurringExpenseIT.java` (`recurringExpenseIsRegisteredAsAnActiveSeriesAndListed`, `pauseStopsTheGenerationAndResumeSkipsThePause`, `onlyTheOwnerSeriesCanBeChanged`, `invalidStatusIsAFieldError`, `endpointsRequireAVerifiedSession`) — FR-008, FR-011, FR-012, FR-016
- [X] T023 [P] [US3] Crear `frontend/src/pages/RecurringExpensesPage.test.jsx` (lista, acciones por estado, pausar con carga y éxito, reanudar, 409 con recarga, red, 401, vacío, reintento, 401 al cargar) — FR-012..FR-014
- [X] T024 [P] [US3] Ampliar `frontend/src/api/expenseClient.test.js` (9 casos de `listRecurringExpenses` y `updateRecurringExpenseStatus`) y `frontend/src/components/SiteNav.test.jsx` (acceso y resaltado) — contracts §Comportamiento del cliente
- [X] T025 [P] [US3] Crear `frontend/src/validation/expenseFormat.test.js` — research.md §D12

### Implementation for User Story 3

- [X] T026 [US3] Agregar a `RecurringExpenseService` `listSeries` y `changeStatus` (parseo del estado, búsqueda por id + dueño + serie, transiciones) — FR-007, FR-008, FR-010, FR-011
- [X] T027 [US3] Crear `RecurringExpenseNotFoundException` y su handler 404 en `ProblemDetailExceptionHandler` — FR-011
- [X] T028 [US3] Agregar a `expense/ExpenseController.java` `GET /api/expenses/recurring` y `PATCH /api/expenses/{id}/status` (`ChangeStatusRequest`) — contracts
- [X] T029 [US3] Agregar `listRecurringExpenses` y `updateRecurringExpenseStatus` con sus mensajes fijos a `frontend/src/api/expenseClient.js` — contracts §Comportamiento del cliente
- [X] T030 [P] [US3] Crear `frontend/src/validation/expenseFormat.js` (`formatAmount`, `formatIsoDate` sin `Number`) — research.md §D12
- [X] T031 [US3] Crear `frontend/src/pages/RecurringExpensesPage.jsx` y sus estilos en `frontend/src/index.scss` — FR-012..FR-014
- [X] T032 [US3] Registrar `/expenses/recurring` (protegida) en `frontend/src/App.jsx` y el acceso "Gastos recurrentes" en `frontend/src/components/SiteNav.jsx` — FR-012

**Checkpoint**: se pausa y reanuda desde la pantalla.

---

## Phase 6: User Story 4 - Finalizar un gasto recurrente (Priority: P1)

**Goal**: "Finalizar" con confirmación; la serie no genera más, pierde la próxima fecha y no se puede reanudar.

**Independent Test**: finalizar → la generación no crea nada; reanudar → 409.

### Tests for User Story 4 ⚠️

- [X] T033 [P] [US4] Casos en `RecurringExpenseServiceTest.java` (finalizar desde activo y pausado, salir de finalizado = 409) y en `RecurringExpenseIT.java` (`finishIsFinalAndStopsTheGeneration`) — FR-009, SC-005
- [X] T034 [P] [US4] Casos en `RecurringExpensesPage.test.jsx` (confirmar y cancelar, finalizado sin acciones ni próxima fecha) — FR-013

### Implementation for User Story 4

- [X] T035 [US4] Transición a `FINISHED` (borra la próxima fecha) y `RecurringExpenseFinishedException` con handler 409 `RECURRENCE_FINISHED` — FR-009
- [X] T036 [US4] Confirmación en línea "Finalizar es definitivo…" con "Sí, finalizar" / "Cancelar" en `RecurringExpensesPage.jsx` — FR-013

**Checkpoint**: todas las historias funcionan de forma independiente.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T037 [P] Casos de compatibilidad y esquema en `RecurringExpenseIT.java` (`legacyRecurringExpensesWithoutFrequencyAreLeftAlone`, `schemaRejectsInconsistentRecurrenceRows`, `frequencyRulesAreEnforcedWithoutPersisting`) — FR-002, FR-006, FR-015, SC-006
- [X] T038 Verificación automatizada: `mvn -B verify` (BUILD SUCCESS, cobertura OK) y `npm run lint && npm run test:coverage && npm run build` (363 tests, 92,66 % de líneas, sin errores de lint) — constitución V, XI
- [X] T039 Documentación SDD completa en `specs/009-pause-recurring-expense/` — constitución II
- [ ] T040 Validación manual según `quickstart.md` §2 (escenarios A–E) y registro del resultado — DoD
- [ ] T041 Commits con Conventional Commits (por ejemplo `docs:` spec 009, `feat:` recurrencia y generación, `feat:` pausar/finalizar, `test:`) — constitución XIII
- [ ] T042 PR de `feat/definir-tipo-gasto` hacia `sprint-2` explicando la ampliación de alcance (recurrencia), el cambio de FR-010 de 007, la migración V5 y los tres tests de 007 adaptados — constitución XII

---

## Dependencies & Execution Order

- **Setup → Foundational → US1 → US2 → US3 → US4 → Polish.**
- US2 necesita series (US1). US3 necesita series y la generación para demostrar la pausa (US2). US4 reutiliza el endpoint y la pantalla de US3.
- Dentro de cada historia: tests primero; backend (servicio → excepciones → controlador) y frontend (cliente → página → ruta) en paralelo una vez fijado el contrato.

### Parallel Opportunities

- T002–T004 en paralelo; T007 en paralelo con T005/T006.
- Los tests de cada historia (marcados [P]) en paralelo entre sí.
- Frontend (T029–T032) en paralelo con backend (T026–T028).

## Notes

- Mensajes exactos de la spec y el contrato: no reimprovisar textos.
- Nunca alterar filas de 007 (FR-015): la migración solo agrega columnas.
- En los IT, invocar la generación con el "hoy" del servicio (research.md §D13).
- Los commits los realiza el equipo manualmente (T041).
