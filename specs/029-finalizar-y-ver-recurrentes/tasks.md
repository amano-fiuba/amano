---
description: "Task list for Finalizar y ver gastos recurrentes de grupo"
---

# Tasks: Finalizar y ver gastos recurrentes de grupo

**Input**: Design documents from `/specs/029-finalizar-y-ver-recurrentes/`

**Prerequisites**: plan.md (requerido), spec.md (requerido para las historias), research.md, data-model.md, contracts/, quickstart.md

**Tests**: El plan y la constitución exigen pruebas automatizadas (unitarias puras con `Clock.fixed`, integración con Testcontainers + MockMvc, Vitest con cobertura ≥ 85 %). Las tareas de prueba se escriben ANTES de la implementación que cubren.

**Organization**: Tareas agrupadas por historia de usuario. La spec 029 extiende 028 (US4 ya implementada: pantalla, pausa/reactivación y frecuencia). Esta ejecución implementa US1 (pausar/reanudar con confirmación y reanclaje), US2 (finalizar) y US3 (vista con acumulado, vencimiento y origen).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: A qué historia pertenece (US1, US2, US3)
- Incluir rutas de archivo exactas en cada descripción

## Path Conventions

- Monorepo web: `backend/src/main/java/com/amano/backend/`, `backend/src/main/resources/db/migration/`, `backend/src/test/java/com/amano/backend/`, `frontend/src/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verificaciones previas y configuración de entorno

- [x] T001 Verificar los supuestos de la sección "Technical Context" de specs/029-finalizar-y-ver-recurrentes/plan.md: última migración integrada en backend/src/main/resources/db/migration/ (hoy V34), que `RecurrenceStatus` (backend/src/main/java/com/amano/backend/expense/RecurrenceStatus.java) ya incluye `FINISHED`, los CHECKs de V32 (`ck_group_expenses_recurrence_status` ya admite FINISHED y `ck_group_expenses_recurrence_next_date` exige NULL si FINISHED), el comportamiento actual de `GroupRecurringExpenseService.updateSeries` (rechaza FINISHED con 400 y reactiva anclado a `expenseDate`), la lista vigente de `GroupActivityType`/CHECK de V28, y que `STATUS_OPTIONS` de frontend/src/validation/expenseCatalog.js ya mapea `FINISHED` → "Finalizado"; corregir plan.md/data-model.md si algo difiere

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Migraciones, anclaje del ciclo e infraestructura de eventos compartidas por las tres historias

**⚠️ CRITICAL**: Ninguna historia puede comenzar hasta completar esta fase

- [x] T002 [P] Crear backend/src/main/resources/db/migration/V35__group_expense_recurrence_anchor.sql: `ALTER TABLE group_expenses ADD COLUMN recurrence_anchor_date DATE NULL;` + backfill `UPDATE group_expenses SET recurrence_anchor_date = expense_date WHERE recurrence_frequency IS NOT NULL;` + CHECK `ck_group_expenses_recurrence_anchor ((recurrence_frequency IS NULL) = (recurrence_anchor_date IS NULL))`, y la columna de origen `ALTER TABLE group_expenses ADD COLUMN recurrence_created_via VARCHAR(8) NULL;` + backfill `UPDATE group_expenses SET recurrence_created_via = 'JOB' WHERE recurrence_source_id IS NOT NULL;` + CHECK `ck_group_expenses_recurrence_created_via ((recurrence_source_id IS NULL) = (recurrence_created_via IS NULL))`, según data-model.md
- [x] T003 Extender backend/src/main/java/com/amano/backend/groupexpense/GroupExpense.java con `recurrenceAnchorDate` (`LocalDate` nullable, columna `recurrence_anchor_date`) y reemplazar en backend/src/main/java/com/amano/backend/groupexpense/GroupRecurringExpenseService.java todo uso aritmético de `series.getExpenseDate()` por `series.getRecurrenceAnchorDate()` (job, línea 187, y recálculo de frecuencia, línea 151), sin cambiar fechas para las series existentes (el backfill hace ambos equivalentes); verifica que las pruebas de 028 sigan pasando; depende de T002
- [x] T004 [P] Crear backend/src/main/resources/db/migration/V36__group_activity_recurrence_events.sql: `ALTER TABLE group_activity DROP CONSTRAINT group_activity_event_type_check;` + `ADD CONSTRAINT ... CHECK (event_type IN (<lista vigente de V28> + 'RECURRENCE_PAUSED', 'RECURRENCE_RESUMED', 'RECURRENCE_FINISHED'))`, patrón de V28
- [x] T005 Agregar a backend/src/main/java/com/amano/backend/groups/GroupActivity.java los tipos `RECURRENCE_PAUSED`, `RECURRENCE_RESUMED`, `RECURRENCE_FINISHED` (enum interno de V36), crear el DTO backend/src/main/java/com/amano/backend/groupexpense/RecurringActivityDetails.java (`{ expenseId, description, status }`) y el método `ActivityEventService.recordRecurrenceChange(groupId, type, actorUserId, details)`; depende de T004

**Checkpoint**: Migraciones, anclaje y eventos listos - las historias pueden comenzar

---

## Phase 3: User Story 1 - Pausar y reanudar una serie con confirmación (Priority: P1) 🎯 MVP

**Goal**: Pausar una serie Activa con confirmación (queda Pausada, no genera nada, la próxima fecha deja de regir y no se muestra); reanudar una serie Pausada con confirmación que explica el reinicio: la serie se reancla a hoy, se genera de inmediato una ocurrencia con fecha de hoy que vence según el plazo vigente del grupo, la próxima fecha queda un período después y nada de lo ya generado cambia (FR-001 a FR-003, FR-006, FR-017, FR-018).

**Independent Test**: pausar una serie activa con confirmación y verificar que el job no genera nada mientras esté pausada; reanudarla y verificar que aparece de inmediato un gasto con fecha de hoy (deudas que vencen en hoy + `debt_due_days`), que la próxima fecha es un período después de la reanudación y que las ocurrencias anteriores se conservan.

### Tests for User Story 1 ⚠️

- [ ] T006 [P] [US1] Crear backend/src/test/java/com/amano/backend/groupexpense/GroupRecurringExpenseServiceTest.java (unitario, Mockito puro, `Clock.fixed` con zona `America/Argentina/Buenos_Aires`): reanudar reancla (`anchor = hoy`, ocurrencia inmediata con `dueDate = hoy + debt_due_days`, `nextDate = frequency.firstAfter(hoy, hoy)`, sin retroactividad); pausar conserva la fila sin generar; pedir el estado ya vigente responde éxito sin cambios y NO genera la ocurrencia inmediata ni reancla (edge case de la spec); reanudar cuando ya existe una ocurrencia de la serie fechada hoy no crea duplicado; `EXPENSE_CREATED` con actor del miembro que reanudó; fallan antes de T009
- [ ] T007 [P] [US1] Extender backend/src/test/java/com/amano/backend/groupexpense/GroupRecurringExpenseIT.java: PATCH `status: "PAUSED"` responde 200 con `nextOccurrenceDate` y `nextDueDate` `null` (FR-010) y evento `RECURRENCE_PAUSED` con actor; PATCH `status: "ACTIVE"` sobre pausada genera la ocurrencia de hoy con `dueDate = hoy + debt_due_days` y eventos `RECURRENCE_RESUMED` + `EXPENSE_CREATED` a nombre del miembro; permisos 403, 404 de ocurrencia y 404 neutral sin regresión; actualizar las expectativas previas de 028 que asumían `nextOccurrenceDate` no nulo para series pausadas; fallan antes de T009
- [ ] T008 [P] [US1] Extender frontend/src/pages/groups/GroupRecurringExpensesPage.test.jsx: matriz de acciones (Activa → "Pausar"; Pausada → "Reanudar" — reemplaza el label actual "Reactivar" y sus aserciones existentes), confirmación previa en ambas (Cancelar no llama al API), mensaje de éxito en snackbar, aviso de reinicio de FR-017 visible en la confirmación de reanudación ("a partir de hoy empieza de nuevo: un gasto para pagar en 7 días y el siguiente en un mes"), y fila pausada sin próxima fecha cuando el API la manda `null`; fallan antes de T010

### Implementation for User Story 1

- [ ] T009 [US1] Implementar la lógica de estados en backend/src/main/java/com/amano/backend/groupexpense/GroupRecurringExpenseService.java: refactor de `createOccurrence(series, date)` a `createOccurrence(series, date, dueDate, actorUserId, createdVia)` (job: `dueDate = date`, actor `null`, `createdVia = 'JOB'`; reanudación: `dueDate = GroupExpenseService.dueDateFor(groupId, hoy)`, actor = `callerId`, `createdVia = 'RESUME'`); en `updateSeries`: reanudar (PAUSED → ACTIVE) hace `anchor = hoy`, genera la ocurrencia inmediata con `occurrenceKey(series.getId(), hoy)` respetando el índice único, copia shares y deudas `PENDING` (`debtCurrencyOf`), fija `nextDate = frequency.firstAfter(hoy, hoy)` y registra `RECURRENCE_RESUMED` + `EXPENSE_CREATED`; pausar cambia a `PAUSED` sin tocar `nextDate` en la fila y registra `RECURRENCE_PAUSED`; `RecurringGroupExpenseResponse.from` devuelve `nextOccurrenceDate` y `nextDueDate` `null` para series `PAUSED`/`FINISHED`; depende de T003, T005, T006, T007
- [ ] T010 [US1] Implementar la matriz de acciones y confirmaciones en frontend/src/pages/groups/GroupRecurringExpensesPage.jsx (patrón de frontend/src/pages/RecurringExpensesPage.jsx): `ACTIONS_BY_STATUS` (ACTIVE → Pausar; PAUSED → Reanudar, reemplazando el botón único toggle con label "Reactivar" por el label "Reanudar" de 009), confirmación inline por acción (`.recurring-item__confirm`), aviso de reinicio en la confirmación y en el snackbar de reanudación (FR-017), busy labels y mensajes `PAUSED_MESSAGE`/`RESUMED_MESSAGE` en español con voseo; la fila oculta la próxima fecha cuando `nextOccurrenceDate` viene `null`; depende de T008, T009
- [ ] T011 [P] [US1] Agregar en frontend/src/pages/groups/activityDescriptions.js las descripciones de `RECURRENCE_PAUSED`, `RECURRENCE_RESUMED` y `RECURRENCE_FINISHED` ("{actor} pausó el gasto recurrente {descripción}", "{actor} reanudó…", "{actor} finalizó…", actor "Sistema" no aplica) y sus pruebas en activityDescriptions.test.js; depende de T005

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Finalizar una serie (Priority: P1)

**Goal**: Finalizar una serie Activa o Pausada con confirmación que advierte la irreversibilidad: queda `FINISHED`, pierde la próxima fecha, no genera más, rechaza con 409 cualquier modificación posterior y conserva las ocurrencias y deudas ya generadas (FR-004, FR-005, FR-007, FR-008).

**Independent Test**: finalizar una serie activa o pausada con confirmación; el job no genera nada para esa serie; un PATCH posterior (status o frequency) responde 409 `RECURRENCE_FINISHED`; el gasto original y las ocurrencias siguen con sus deudas.

### Tests for User Story 2 ⚠️

- [ ] T012 [P] [US2] Extender backend/src/test/java/com/amano/backend/groupexpense/GroupRecurringExpenseServiceTest.java: finalizar (desde ACTIVE y desde PAUSED) pone `FINISHED`, `nextDate = null`, anchor sin cambio; cualquier modificación sobre una serie `FINISHED` (status, incluso el mismo valor, o frequency) lanza el conflicto 409 `RECURRENCE_FINISHED` con el mensaje "El gasto recurrente ya está finalizado y no puede modificarse"; `generateDueOccurrences` saltea series `FINISHED`; fallan antes de T014
- [ ] T013 [P] [US2] Extender backend/src/test/java/com/amano/backend/groupexpense/GroupRecurringExpenseIT.java: PATCH `status: "FINISHED"` → 200 con evento `RECURRENCE_FINISHED` con actor; PATCH sobre serie finalizada → 409 `properties.code = "RECURRENCE_FINISHED"`; 403 al finalizar para un miembro sin permiso (FR-008); el gasto original y las ocurrencias con sus deudas se conservan intactos tras finalizar (FR-006); el job no genera nada para la serie finalizada; fallan antes de T014

### Implementation for User Story 2

- [ ] T014 [US2] Aceptar `FINISHED` en backend/src/main/java/com/amano/backend/groupexpense/GroupRecurringExpenseService.java: catálogo de estados válido `ACTIVE`/`PAUSED`/`FINISHED` con mensaje de error "El estado no es válido" (reemplaza "El estado debe ser ACTIVE o PAUSED"), finalizar pone `recurrenceStatus = FINISHED` y `recurrenceNextDate = null` y registra `RECURRENCE_FINISHED`, y cualquier cambio sobre una serie `FINISHED` lanza el conflicto `RECURRENCE_FINISHED` (409, RFC 7807 con `properties.code`); depende de T005, T012, T013
- [ ] T015 [US2] Implementar "Finalizar" en frontend/src/pages/groups/GroupRecurringExpensesPage.jsx: acción para series ACTIVE y PAUSED con confirmación inline "Finalizar es definitivo: no se generarán más gastos de esta serie." (patrón de frontend/src/pages/RecurringExpensesPage.jsx), snackbar "El gasto recurrente se finalizó", serie `FINISHED` sin acciones y sin próxima fecha; manejo del 409 mostrando el `detail` del servidor y recargando la fila; depende de T010, T014

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - Ver los gastos recurrentes con importes, vencimiento y origen (Priority: P2)

**Goal**: La lista de gastos recurrentes muestra importe por ocurrencia, categoría/grupo, frecuencia, estado, acumulado gastado de la serie y próximo vencimiento (según reglas de 028); los saldos y el acumulado reflejan cada ocurrencia sin acción manual; el detalle de cualquier gasto de la serie identifica su origen (serie, Sistema o miembro) (FR-009 a FR-014).

**Independent Test**: con una serie de dos ocurrencias de $10.000, la fila muestra importe $10.000 y acumulado $20.000; una serie reanudada hoy muestra el vencimiento de la ocurrencia inmediata (hoy + `debt_due_days`); el detalle de una ocurrencia generada muestra su serie y que la creó el sistema.

### Tests for User Story 3 ⚠️

- [ ] T016 [P] [US3] Tests de agregados en backend/src/test/java/com/amano/backend/groupexpense/ (unitarios y/o IT): `accumulatedAmount` (serie sin ocurrencias → "0.00"; serie + 2 ocurrencias de $10.000 → "30000.00"), `nextDueDate` (serie nueva → su `next_date`; serie reanudada → hoy + `debt_due_days` de la ocurrencia inmediata; serie con ocurrencia futura generada temprano → el mínimo entre ambos; serie `PAUSED`/`FINISHED` → `null`; grupo sin `debt_due_days` → regla del mínimo igualmente); fallan antes de T018
- [ ] T017 [P] [US3] Tests de frontend: GroupRecurringExpensesPage.test.jsx muestra "Acumulado" por fila y el próximo vencimiento de la serie activa; GroupExpenseDetailView.test.jsx muestra el bloque de origen (`SERIE` → "Este gasto dio origen a la serie recurrente"; `SISTEMA` → "Generado automáticamente por el sistema · parte de la serie {descripción}"; `MIEMBRO` → "Generado al reanudar la serie… · creado por {nombre}") y lo omite en un gasto no recurrente (`recurrence` ausente); fallan antes de T020

### Implementation for User Story 3

- [ ] T018 [US3] Agregar a backend/src/main/java/com/amano/backend/groupexpense/GroupExpenseRepository.java la consulta agregada del acumulado (`SELECT COALESCE(SUM(e.amount), 0) FROM GroupExpense e WHERE e.id = :seriesId OR e.recurrenceSourceId = :seriesId`, en batch para el listado) y extender backend/src/main/java/com/amano/backend/groupexpense/RecurringGroupExpenseResponse.java con `accumulatedAmount` y `nextDueDate` (reglas de data-model.md y del contrato contracts/group-recurring-expenses-management.md); depende de T016
- [ ] T019 [US3] Agregar el campo `recurrence` (`{ seriesId, origin: "SERIE" | "SISTEMA" | "MIEMBRO" }`, `NON_NULL`) a backend/src/main/java/com/amano/backend/groupexpense/GroupExpenseResponse.java y poblarlo en `GroupExpenseService.get` leyendo los datos persistidos: serie (frecuencia no nula) → `SERIE`; ocurrencia con `recurrence_created_via = 'JOB'` → `SISTEMA`; ocurrencia con `recurrence_created_via = 'RESUME'` → `MIEMBRO` (columna de V35, no se infiere del histórico de actividad), según contracts/expense-detail-origin.md; depende de T017
- [ ] T020 [US3] Frontend: parsear `accumulatedAmount`, `nextDueDate` y `recurrence` en frontend/src/api/groupsClient.js y frontend/src/api/groupExpensesClient.js; mostrar en GroupRecurringExpensesPage.jsx el acumulado y el próximo vencimiento de cada fila (serie activa) y el bloque de origen en frontend/src/pages/groups/GroupExpenseDetailView.jsx; depende de T018, T019, T017

**Checkpoint**: All user stories should now be independently functional

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Verificación integrada, documentación y calidad

- [ ] T021 Validar el recorrido de specs/029-finalizar-y-ver-recurrentes/quickstart.md con las IT equivalentes (GroupRecurringExpenseIT para pausa/reanudación/finalización/409/permisos, GroupExpenseControllerIT para la generación posterior a la reanudación) y cubrir los criterios SC-001 a SC-006
- [ ] T022 [P] Actualizar specs/029-finalizar-y-ver-recurrentes/plan.md, research.md, data-model.md y contracts/ con cualquier ajuste que surja de la implementación (sin cambiar el alcance de la spec)
- [ ] T023 Cobertura y calidad: `mvn -B verify` en `backend/` y `npm ci && npm run lint && npm run test:coverage && npm run build` en `frontend/`, con cobertura ≥ 85 % (JaCoCo + Vitest coverage), sin bajar umbrales

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias, inicia de inmediato
- **Foundational (Phase 2)**: depende de Setup; BLOQUEA las tres historias (T002→T003 y T004→T005)
- **US1 (Phase 3)**: depende de Foundational; los tests (T006–T008) van antes de la implementación que cubren (T009/T010)
- **US2 (Phase 4)**: depende de Foundational y de T009 (usa el servicio modificado); puede iniciarse en paralelo con US1 una vez completado Phase 2, pero su implementación de frontend (T015) depende de la matriz de acciones de US1 (T010)
- **US3 (Phase 5)**: depende de Foundational; T018/T019 dependen de T009 (la ocurrencia de reanudación y las reglas de `nextDueDate`/`nextOccurrenceDate` la definen)
- **Polish (Phase 6)**: depende de las tres historias completas

### User Story Dependencies

- **US1**: puede iniciar tras Phase 2 — sin dependencias de otras historias
- **US2**: backend (T014) independiente de US1; frontend (T015) reutiliza la infraestructura de confirmaciones de T010
- **US3**: lectura sobre el modelo y el servicio resultantes de US1; independiente en su parte de tests

### Within Each User Story

- Tests antes que implementación (fallan primero)
- Migraciones antes que código Java que las usa (T002 antes que T003; T004 antes que T005)
- Servicio antes que controller/frontends (T009 antes que T010)
- Backend antes que frontend en la verificación de integración (quickstart)

### Parallel Opportunities

- T002, T004 (migraciones) y todos los tests marcados [P] (T006, T007, T008, T011, T012, T013, T016, T017, T022) corren en paralelo con las tareas previas de su fase
- Frontend (T008/T010, T015, T017/T020) es independiente del backend hasta la verificación del quickstart
- T011 (descripciones del historial) es paralelizable con T009/T010

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar Phase 1: Setup (T001)
2. Completar Phase 2: Foundational (T002–T005) — BLOQUEA todo
3. Completar Phase 3: User Story 1 (T006–T011)
4. **STOP and VALIDATE**: pausar/reanudar una serie real según el Independent Test; correr las verificaciones del módulo
5. Integrar la historia (PR a `sprint-5`) si está lista

### Incremental Delivery

1. Setup + Foundational → base lista
2. US1 → probar en forma independiente → integrar (MVP: control de pausa/reanudación)
3. US2 → probar en forma independiente → integrar (ciclo de vida completo con Finalizado)
4. US3 → probar en forma independiente → integrar (vista con acumulado, vencimiento y origen)
5. Cada historia agrega valor sin romper las anteriores

### Parallel Team Strategy

Con más de una persona, tras completar Setup + Foundational:

1. Desarrollador A: US1 (backend + frontend)
2. Desarrollador B: US2 (backend; el frontend espera T010)
3. Desarrollador C: US3 (tests y agregados; el detalle de origen espera T019)

---

## Notes

- [P] tasks = archivos distintos, sin dependencias pendientes
- [Story] label mapea la tarea a la historia para trazabilidad
- Cada historia es completable y comprobable en forma independiente
- Verificar que los tests fallan antes de implementar
- Commitear después de cada tarea o grupo lógico (Conventional Commits en español)
- Detenerse en cada checkpoint para validar la historia en forma independiente
- Evitar: tareas vagas, conflictos de mismo archivo, dependencias cruzadas que rompan la independencia
