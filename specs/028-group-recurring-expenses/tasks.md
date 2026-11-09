---
description: "Task list for Calendario de vencimientos de gastos compartidos recurrentes"
---

# Tasks: Calendario de vencimientos de gastos compartidos recurrentes

**Input**: Design documents from `/specs/028-group-recurring-expenses/`

**Prerequisites**: plan.md (requerido), spec.md (requerido para las historias), research.md, data-model.md, contracts/, quickstart.md

**Tests**: El plan y la constitución exigen pruebas automatizadas (unitarias puras, integración con Testcontainers + GreenMail, Vitest con cobertura ≥ 85 %). Las tareas de prueba se escriben ANTES de la implementación de cada historia.

**Organization**: Tareas agrupadas por historia de usuario. US1 (recurrencia de grupo) y US2 (reglas del gasto compartido) ya están implementadas y commiteadas (`feat: agrega recurrencia a los gastos compartidos`, `e4af638`); esta ejecución implementa US3 (calendario de vencimientos y recordatorios).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: A qué historia pertenece (US3)
- Incluir rutas de archivo exactas en cada descripción

## Path Conventions

- Monorepo web: `backend/src/main/java/com/amano/backend/`, `backend/src/main/resources/db/migration/`, `backend/src/test/java/com/amano/backend/`, `frontend/src/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verificaciones previas y configuración de entorno

- [ ] T001 Verificar los supuestos de la sección "Technical Context" de specs/028-group-recurring-expenses/plan.md: última migración integrada en backend/src/main/resources/db/migration/ (hoy V32), lógica actual de `ReminderSchedule.dueDate` usada por `ReminderMaterializer`/`ReminderDispatcher`, consulta `DebtRepository.findPendingDueBefore`, y lista vigente de `ALLOWED_SEND_DAYS` en `GroupReminderSettingsService` y `GroupReminderSettingsPanel.jsx`; corregir plan.md/data-model.md si algo difiere

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Migraciones y entidad con `dueDate` compartidas por todo el flujo

**⚠️ CRITICAL**: Ninguna historia puede comenzar hasta completar esta fase

- [ ] T002 [P] Crear backend/src/main/resources/db/migration/V33__group_expense_due_date.sql: `ALTER TABLE group_expenses ADD COLUMN due_date DATE NULL;` y backfill según data-model.md (ocurrencias generadas con `recurrence_source_id IS NOT NULL` → `expense_date`; el resto → `expense_date + groups.debt_due_days`, NULL si el grupo no tiene `debt_due_days`)
- [ ] T003 Extender backend/src/main/java/com/amano/backend/groupexpense/GroupExpense.java con `dueDate` (`LocalDate` nullable, columna `due_date`); depende de T002
- [ ] T004 [P] Tests de persistencia para `dueDate` en backend/src/test/java/com/amano/backend/groupexpense/ (Testcontainers, migraciones V33 + V32, caso gasto normal, caso ocurrencia generada y caso grupo sin `debt_due_days`); fallan antes de T002/T003
- [ ] T005 Asignar `dueDate` al crear un gasto de grupo en backend/src/main/java/com/amano/backend/groupexpense/GroupExpenseService.java (hoy + `groups.debt_due_days` vigente, NULL si el grupo no tiene `debt_due_days`); depende de T003

**Checkpoint**: Migraciones y entidad listas - las historias pueden comenzar

---

## Phase 3: User Story 3 - Calendario de vencimientos y recordatorios (Priority: P1) 🎯 MVP

**Goal**: La primera ocurrencia vence como un gasto normal; cada ocurrencia generada vence en su propia fecha; el job anticipa la generación hasta 7 días antes; los recordatorios de 025 (incluida la opción de 2 días antes) se calculan desde el vencimiento persistido.

**Independent Test**: Grupo con `debt_due_days = 7` y recordatorio de "2 días antes": registrar hoy un gasto recurrente mensual (primera ocurrencia vence hoy+7), y en la ventana de anticipación verificar que la segunda ocurrencia se crea con fecha futura, vence en su propia fecha, y el recordatorio se envía 2 días antes de ese vencimiento.

### Tests for User Story 3 ⚠️

- [ ] T006 [P] [US3] Tests de rematerialización de recordatorios en backend/src/test/java/com/amano/backend/groupexpense/reminder/ usando `expense.getDueDate()` en lugar de `expenseDate + debtDueDays` (casos primera ocurrencia, ocurrencia generada con fecha futura y grupo sin `debt_due_days`); fallan antes de T009
- [ ] T007 [P] [US3] Tests de `OverdueDebtJob`/`DebtRepository.findPendingDueBefore` basados en `e.dueDate < :today` (deuda vence hoy, deuda vencida, deuda cuya serie aún no vence aunque `expenseDate` sea pasada); fallan antes de T010

### Implementation for User Story 3

- [ ] T008 [US3] Ventana de anticipación en backend/src/main/java/com/amano/backend/groupexpense/GroupRecurringExpenseService.java: generar ocurrencias cuyo `recurrence_next_date` sea `<= today + 7` (máximo del catálogo de días de recordatorio), conservando backfill, idempotencia y límite de 1000 por serie y ejecución (FR-006, FR-011); depende de T005
- [ ] T009 [US3] Asignar `dueDate = expenseDate` de la ocurrencia en `GroupRecurringExpenseService.createOccurrence` (FR-016); depende de T003 y T008
- [ ] T010 [US3] Usar `expense.getDueDate()` en backend/src/main/java/com/amano/backend/groupexpense/reminder/ReminderMaterializer.java y ReminderDispatcher.java (queda un único origen de vencimiento en el plan D2); depende de T005, T006 y T009
- [ ] T011 [US3] Migrar `DebtRepository.findPendingDueBefore` y `OverdueDebtJob.markOverdueDebts` a `e.dueDate < :today` en backend/src/main/java/com/amano/backend/groupexpense/DebtRepository.java y .../overdue/OverdueDebtJob.java; depende de T005 y T007
- [ ] T012 [P] [US3] Crear backend/src/main/resources/db/migration/V34__groups_reminder_send_days_2.sql: reemplazar el CHECK de V26 por `CHECK (reminder_send_days IN (7, 5, 3, 2, 1))` (clarificación 2026-11-08, punto 2)
- [ ] T013 [US3] Agregar `2` a `ALLOWED_SEND_DAYS` en backend/src/main/java/com/amano/backend/groups/GroupReminderSettingsService.java (server-side validation para todos los grupos) y su prueba; depende de T012
- [ ] T014 [P] [US3] Agregar la opción "2 días antes" al `<select>` de frontend/src/components/groups/GroupReminderSettingsPanel.jsx (`ALLOWED_SEND_DAYS = [7, 5, 3, 2, 1]`, label existente `${days} días antes`) y su prueba en GroupReminderSettingsPanel.test.jsx

**Checkpoint**: At this point, User Story 3 should be fully functional and testable independently

---

## Phase 4: Polish & Cross-Cutting Concerns

**Purpose**: Verificación integrada y documentación

- [ ] T015 Ejecutar el recorrido de specs/028-group-recurring-expenses/quickstart.md end-to-end (grupo con `debt_due_days=7`, recordatorio de 2 días, primera y segunda ocurrencia con sus deudas, recordatorio en la fecha `dueDate - reminderSendDays`)
- [ ] T016 [P] Actualizar specs/028-group-recurring-expenses/plan.md, research.md, data-model.md y contracts/ con cualquier ajuste que surja de la implementación (sin cambiar el alcance de la spec)
- [ ] T017 Cobertura y calidad: `./mvnw verify` en backend y `npm run lint && npm test && npm run build` en frontend, con cobertura ≥ 85 % sin bajar umbrales

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias, inicia de inmediato
- **Foundational (Phase 2)**: depende de Setup; BLOQUEA US3
- **US3 (Phase 3)**: depende de Foundational; los tests (T006/T007) van antes de la implementación que cubren (T010/T011)
- **Polish (Phase 4)**: depende de US3 completa

### User Story Dependencies

- **US3**: es la única historia pendiente; T008 (ventana de anticipación) y T009 (`dueDate` de ocurrencia) son prerrequisitos de T010 (recordatorios) y T011 (vencidas); T012/T013/T014 (opción 2) puede avanzar en paralelo

### Within Each Task Group

- Tests antes que implementación
- Migraciones antes que código Java que las usa
- `GroupExpense`/`GroupRecurringExpenseService` antes que `ReminderMaterializer`/`ReminderDispatcher`/`OverdueDebtJob`

### Parallel Opportunities

- T002, T004, T006, T007, T012 y T014 pueden correr en paralelo con la tarea inmediatamente previa
- Frontend (T014) es independiente del backend hasta la prueba de integración del quickstart

---

## Implementation Strategy

### MVP First (User Story 3 Only)

1. Completar Phase 1 y Phase 2
2. Completar T008 → T009 → T010 → T011
3. Completar T012 → T013 → T014 (opción opción 2 (solo afecta recordatorios))
4. **STOP and VALIDATE**: correr quickstart
5. Deploy/demo si está listo

### Incremental Delivery

1. Migraciones + entidad → `dueDate` disponible
2. Ventana de anticipación y dueDate de ocurrencia → fr-006/FR-016/FR-019 listos
3. Recordatorios y deudas vencidas leen el vencimiento persistido → FR-017/FR-018 listos
4. Opción de 2 días → FR-017 completa para todos los grupos

---

## Notes

- [P] tareas = archivos distintos, sin dependencias
- [Story] label = traza a la historia específica
- Verificar que los tests fallen antes de implementar
- Commitear después de cada tarea o grupo lógico (Conventional Commits en español)
- No editar migraciones ya integradas; nuevas migraciones V33 y V34
