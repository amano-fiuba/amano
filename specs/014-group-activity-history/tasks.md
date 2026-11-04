# Tasks: Ver historial de actividad del grupo

**Input**: Design documents from `/specs/014-group-activity-history/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/activity-api.md](./contracts/activity-api.md), [quickstart.md](./quickstart.md)

**Tests**: incluidas (constitución V, research.md D11). En cada historia las pruebas se escriben primero.

**Organization**: tareas agrupadas por historia de usuario.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: US1, US2 o US3; sin etiqueta en Setup, Foundational y Polish

## Path Conventions

- Backend: `backend/src/main/java/com/amano/backend/groups/…`, pruebas en `backend/src/test/java/com/amano/backend/groups/…`
- Frontend: `frontend/src/…`, pruebas `*.test.js(x)` junto al código
- Sin migraciones

---

## Phase 1: Setup

- [x] T001 Confirmar la rama `feat/historial-actividad-grupal` desde `sprint-3` (`61356f6`), con `V10__create_group_activity_table.sql` y sin dependencias nuevas — plan.md §Technical Context

---

## Phase 2: Foundational

- [x] T002 `ActivityEventService.record(Long, GroupActivityType, Long, Map<String, ?>)` serializa con el `ObjectMapper` de Spring y omite claves `null`; se elimina la variante con `String` — research.md D3, FR-004
- [x] T003 [P] `ActivityEventServiceTest`: JSON válido con comillas y acentos, nulos omitidos, mapa vacío → `{}` — SC-002
- [x] T004 Ajustar las pruebas de 010 que verificaban `record(..., anyString())` a `anyMap()` (`GroupMemberServiceTest`, `MemberRemovalServiceTest`, `GroupSettingsServiceTest`)

**Checkpoint**: las suites de 010 siguen en verde con la nueva firma.

---

## Phase 3: User Story 1 - Altas y bajas con quién y a quién (Priority: P1) 🎯 MVP

### Tests for User Story 1

- [x] T005 [P] [US1] `GroupMemberServiceTest`/`MemberRemovalServiceTest`: los detalles de altas y bajas (registrado y simulado) incluyen tipo, id, nombre y correo — FR-001/FR-003
- [x] T006 [P] [US1] `GroupServiceTest`: crear el grupo registra `MEMBER_ADDED` con `atCreation` para el miembro inicial (registrado y simulado) — FR-002
- [x] T007 [P] [US1] `GroupActivityControllerIT`: alta inicial, nombre con comillas devuelto tal cual, baja con copia del nombre aunque el perfil se borre — FR-001..FR-004, FR-009
- [x] T008 [P] [US1] `frontend/src/pages/groups/activityDescriptions.test.js`: frases de altas, bajas, salida, permisos, foto, formatos viejos, actor ausente, apellido vacío, tipo desconocido — FR-014
- [x] T009 [P] [US1] `GroupHistoryView.test.jsx`: frases completas y agrupación por día con hora — FR-014/FR-015

### Implementation for User Story 1

- [x] T010 [US1] `GroupMemberService`: detalles de `addRegisteredMember`, `addSimulatedProfile`, `removeMember` (busca el usuario para copiar nombre y correo) y `removeSimulatedProfile` como `Map` — FR-001/FR-003
- [x] T011 [US1] `GroupService.createGroup`: registrar `MEMBER_ADDED` con `atCreation: true`; permisos, foto y salida como `Map` — FR-002
- [x] T012 [US1] `ActivityHistoryResponse.Entry.details` como `JsonNode`; `GroupActivityService` parsea y devuelve `null` si es inválido — FR-009
- [x] T013 [US1] Crear `frontend/src/pages/groups/activityDescriptions.js` (`describeActivity`, `formatMoney`, `dayKey`/`formatDay`/`formatTime`) — FR-014/FR-015
- [x] T014 [US1] Reescribir `GroupHistoryView.jsx` con frases y grupos por día; quitar el aviso fijo — FR-014/FR-015/FR-019

**Checkpoint**: US1 funcional (MVP).

---

## Phase 4: User Story 2 - Gastos y pagos (Priority: P1)

### Tests for User Story 2

- [x] T015 [P] [US2] `ActivityEventServiceTest`: `recordExpenseCreated` y `recordPaymentMade` guardan el formato del data-model; rechazan `details` nulo, monto nulo/cero/negativo y moneda vacía sin guardar — FR-006..FR-008
- [x] T016 [P] [US2] `GroupActivityControllerIT`: un gasto y un pago registrados con el enganche aparecen en el endpoint con sus detalles — SC-004
- [x] T017 [P] [US2] `activityDescriptions.test.js`: frases de gasto y pago con y sin descripción, nombres y moneda desconocida

### Implementation for User Story 2

- [x] T018 [US2] Crear `ExpenseActivityDetails` y `PaymentActivityDetails`; agregar los métodos de enganche — FR-006..FR-008
- [x] T019 [US2] Frases de `EXPENSE_CREATED` y `PAYMENT_MADE` en `activityDescriptions.js` — FR-014

**Checkpoint**: gastos y pagos listos para el módulo de gastos de grupo.

---

## Phase 5: User Story 3 - Filtrar, paginar y estados (Priority: P2)

### Tests for User Story 3

- [x] T020 [P] [US3] `GroupActivityServiceTest`: filtro múltiple, tipo inválido → `FieldValidationException`, tope 100, página y tamaño por defecto, actores con `findAllById`, 404 no miembro, 403 sin permiso — FR-010..FR-013
- [x] T021 [P] [US3] `GroupActivityControllerIT`: `eventType=MEMBER_REMOVED,MEMBER_LEFT`, 400 con `fields.eventType`, `size=500` → `size` 100 — FR-010/FR-011
- [x] T022 [P] [US3] `groupsClient.test.js`: `eventTypes` se envía separado por comas; 5xx → `network` — contrato del frontend
- [x] T023 [P] [US3] `GroupHistoryView.test.jsx`: filtro reinicia la página, vacío con y sin filtro, paginación "Página X de Y", error con reintento sin entradas viejas, 401 cierra sesión, 404, "Volver al grupo" — FR-016..FR-018

### Implementation for User Story 3

- [x] T024 [US3] `GroupActivityRepository.findByGroupIdAndEventTypeInOrderByCreatedAtDescIdDesc`; quitar el método de un solo tipo y el `findTop50` sin uso — FR-010
- [x] T025 [US3] `GroupActivityService`: normalización y validación de tipos, tope 100, actores en lote — FR-010..FR-013
- [x] T026 [US3] `groupsClient.getActivity`: `eventTypes` y 5xx como red
- [x] T027 [US3] `GroupHistoryView.jsx`: filtro "Mostrar", paginación, estados, 401, descarte de respuestas viejas, "Volver al grupo" — FR-016..FR-018
- [x] T028 [US3] Estilos `.group-history` en `frontend/src/index.scss`

**Checkpoint**: todas las historias funcionan y resisten errores.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [x] T029 [P] Actualizar el contrato de 010 (`api-contracts.md`, sección "Group Activity History") con una referencia a 014
- [x] T030 [P] Verificar que no hay migraciones nuevas ni `UPDATE`/`DELETE` sobre `group_activity` — FR-020
- [ ] T031 **Validación manual (persona usuaria)**: escenarios A–F de quickstart.md §2
- [x] T032 Automatizadas: `cd backend && ./mvnw -B verify` y `cd frontend && npm run lint && npm run test:coverage && npm run build` en verde con cobertura ≥ 85 % — constitución V/XI
- [ ] T033 Commits con Conventional Commits (`docs:` spec 014, `feat:` historial, `test:`) — constitución XIII
- [ ] T034 PR de `feat/historial-actividad-grupal` hacia `sprint-4` (o `sprint-3` si todavía no existe) — constitución XII

---

## Dependencies & Execution Order

- Setup → Foundational → US1 → US2 → US3 → Polish.
- US2 usa el `record(Map)` de Foundational; US3 extiende la consulta y la pantalla de US1.
- Backend y frontend de cada historia en paralelo una vez fijado el contrato.

## Notes

- No se crean migraciones ni se modifican filas existentes.
- Los gastos y pagos de grupo no se implementan acá; solo el enganche.
- Los commits y el PR los realiza el equipo (T033/T034).
