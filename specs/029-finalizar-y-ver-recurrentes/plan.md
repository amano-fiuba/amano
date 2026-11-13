# Implementation Plan: Finalizar y ver gastos recurrentes de grupo

**Branch**: `feat/finalizar-ver-recurrentes` | **Date**: 2026-11-13 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/029-finalizar-y-ver-recurrentes/spec.md`

## Summary

Extiende las series recurrentes de grupo de 028 con el estado **Finalizado** (definitivo, no reanudable), **confirmación previa** para pausar/reanudar/finalizar, **reanudación reanclada a la fecha de reanudación** (genera de inmediato una ocurrencia con fecha de hoy que vence según el plazo vigente del grupo — "flujo de 0"), y completa la vista con **acumulado gastado por serie**, **próximo vencimiento** y **origen de cada gasto** (serie, creado por Sistema o por una persona). Técnicamente: migraciones Flyway nuevas (columna de anclaje del ciclo + ampliación del CHECK de `group_activity`), lógica en `GroupRecurringExpenseService`, contrato PATCH/GET ampliado, y reutilización en el frontend de los patrones de la pantalla personal de 009 (matriz de acciones por estado, confirmación inline, snackbar).

## Technical Context

**Language/Version**: Java 21 + Spring Boot (backend monorepo `backend/`), React 19 + Vite 8 (frontend `frontend/`)

**Primary Dependencies**: Spring Web/MVC, JPA/Hibernate, Flyway, PostgreSQL; frontend: react-router, react-hook-form, Sass; pruebas: JUnit 5, Mockito, Testcontainers, MockMvc (backend), Vitest + React Testing Library (frontend)

**Storage**: PostgreSQL (migraciones Flyway como fuente de verdad; `ddl-auto=validate`)

**Testing**: backend `mvn -B verify` en `backend/` (unitarias `*Test` con Mockito, integración `*IT` con Testcontainers + `@AutoConfigureMockMvc`); frontend `npm ci && npm run lint && npm run test:coverage && npm run build` en `frontend/` (umbral de cobertura 85 % en ambos)

**Target Platform**: Web (API REST + SPA); job diario con cron existente

**Project Type**: Monorepo web (backend REST + frontend React)

**Performance Goals**: sin metas nuevas; el listado de series sigue siendo una consulta por grupo, ahora con dos agregados (suma y mínimo de vencimiento) calculados en la misma capa

**Constraints**: cobertura mínima 85 % (JaCoCo en `mvn verify`, Vitest en `npm run test:coverage`); no editar migraciones Flyway ya integradas (cambios en migración nueva); errores RFC 7807 en español con `properties.fields` / `properties.code`; frontend sin i18n (textos en voseo, constantes exportadas para los tests)

**Scale/Scope**: 3 historias de usuario (US1 pausar/reanudar, US2 finalizar, US3 vista), 2 migraciones, 2 endpoints modificados + 1 campo de origen en el detalle de gasto

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Comprobación | Estado |
|-----------|--------------|--------|
| I. Arquitectura y separación | Se reutilizan `GroupRecurringExpenseService/Controller`, `RecurrenceFrequency`, `ActivityEventService`, el patrón de errores RFC 7807 y los patrones de la pantalla personal de 009. Única modificación estructural: columna `recurrence_anchor_date` + ampliación del CHECK de `group_activity`, justificadas en Complexity Tracking. | PASS |
| II. SDD y trazabilidad | Plan, research, data-model, contratos y quickstart trazan cada FR/SC de la spec 029. | PASS |
| III. Entrega incremental por historia | Las tareas se descompondrán por historia (US1 → US2 → US3) en `/speckit.tasks`; backend y frontend por historia. | PASS |
| IV. Alcance controlado | Solo lo definido en la spec 029; sin features extra. La divergencia con 028 FR-022 (reanudación) está explícita en la spec. | PASS |
| V. Calidad y cobertura | Pruebas unitarias (`*Test` con `Clock.fixed`) e integración (`*IT` con Testcontainers, job invocado con fecha explícita) por cada criterio; cobertura 85 % obligatoria. | PASS |
| VI. Reglas de negocio | Máquina de estados FR-001/FR-007 (transiciones y 409 sobre Finalizada), vencimientos según FR-016 de 028, permisos FR-008 (= FR-021 de 028). | PASS |
| VII. Contratos REST | Cambios aditivos y compatibles, documentados en `contracts/`; el único cambio semántico (`nextOccurrenceDate` null para PAUSED/FINISHED y reanudación reanclada) está autorizado por la spec ("donde esta spec y una anterior difieran, vale esta"). | PASS |
| VIII. Validación y errores | Validación en backend (estado, frecuencia, 409, 403, 404) y mensajes de UI sin éxitos falsos (patrón existente). | PASS |
| IX. Seguridad | Permisos verificados en el servicio (no solo UI), sesión requerida (FR-016). | PASS |
| X. Código mantenible | Reutilización de catálogos (`STATUS_OPTIONS` ya incluye FINISHED), estilos existentes (`.recurring-status--FINISHED` ya estilizado), sin dependencias nuevas. | PASS |
| XI. Reproducibilidad/CI | Verificaciones `mvn verify` y `npm ci && npm run lint && npm run test:coverage && npm run build`. | PASS |
| XII/XIII. PRs y commits | Rama `feat/...` desde `sprint-5`, commits convencionales en español. | PASS |

**Post-design re-check**: la fase 1 no introduce violaciones nuevas; la única decisión estructural (columna de anclaje) queda justificada en Complexity Tracking y en `research.md` D2.

## Project Structure

### Documentation (this feature)

```text
specs/029-finalizar-y-ver-recurrentes/
├── plan.md              # Este archivo
├── research.md          # Fase 0 (/speckit.plan)
├── data-model.md        # Fase 1 (/speckit.plan)
├── quickstart.md        # Fase 1 (/speckit.plan)
├── contracts/           # Fase 1 (/speckit.plan)
│   ├── group-recurring-expenses-management.md
│   └── expense-detail-origin.md
└── tasks.md             # Fase 2 (/speckit.tasks — NO se crea acá)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/amano/backend/
│   ├── expense/
│   │   ├── RecurrenceStatus.java        # ya incluye FINISHED (009) — sin cambios
│   │   └── RecurrenceFrequency.java     # aritmética anclada — sin cambios
│   ├── groupexpense/
│   │   ├── GroupExpense.java            # + recurrenceAnchorDate
│   │   ├── GroupExpenseRepository.java  # + suma de acumulado y min de vencimiento
│   │   ├── GroupRecurringExpenseService.java   # lógica de estados y reanclaje
│   │   ├── GroupRecurringExpenseController.java # PATCH acepta FINISHED
│   │   ├── RecurringGroupExpenseResponse.java   # + accumulatedAmount, nextDueDate
│   │   ├── GroupExpenseResponse.java    # + origen de recurrencia (FR-013)
│   │   └── RecurringActivityDetails.java (nuevo)
│   └── groups/
│       ├── GroupActivity.java           # tipos de evento nuevos (enum interno)
│       └── ActivityEventService.java    # + recordRecurrenceChange
├── src/main/resources/db/migration/
│   ├── V35__group_expense_recurrence_anchor.sql   # columna de anclaje + backfill + CHECK
│   └── V36__group_activity_recurrence_events.sql  # amplía el CHECK de event_type
└── src/test/java/com/amano/backend/groupexpense/
    ├── GroupRecurringExpenseIT.java     # estados, confirmaciones semánticas, 409, acumulado
    └── GroupRecurringExpenseServiceTest.java (nuevo, unitario con Clock.fixed)

frontend/
├── src/
│   ├── pages/groups/
│   │   ├── GroupRecurringExpensesPage.jsx  # matriz de acciones + confirmaciones + acumulado
│   │   ├── GroupRecurringExpensesPage.test.jsx
│   │   ├── GroupExpenseDetailView.jsx      # bloque de origen (FR-013)
│   │   └── activityDescriptions.js         # textos de los nuevos eventos
│   ├── api/groupsClient.js                 # parseo de campos nuevos
│   ├── api/groupExpensesClient.js          # origen en el detalle
│   └── validation/expenseCatalog.js        # STATUS_OPTIONS ya incluye FINISHED
└── (tests *.test.jsx existentes, extendidos)
```

**Structure Decision**: monorepo web existente (opción 2 del template): `backend/` (Spring Boot) + `frontend/` (React). Todos los cambios viven en los paquetes y archivos listados arriba; no se crean módulos nuevos.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Columna nueva `recurrence_anchor_date` en `group_expenses` (V35) | La aritmética de fechas (`RecurrenceFrequency.firstAfter/firstOnOrAfter`) es anclada al inicio de la serie (`series.expenseDate`), que es la fecha del gasto histórico original. FR-003 exige reanclar el ciclo a la fecha de reanudación sin mutar ese gasto histórico (FR-006), por lo que el anclaje debe vivir en un campo propio que V35 retrocompata con `expense_date`. | (a) Mutar `expense_date` de la serie al reanudar: corrompería el gasto original (viola FR-006). (b) Avanzar fechas con `date.plus(1, unit)` sin anclaje: rompe el clamp de meses cortos (31/01 → 28/02 → 31/03) que FR-013 de la spec conserva de 009. |
| Ampliación del CHECK de `group_activity.event_type` (V36) | FR-018 exige eventos de cambio de estado con actor; la BD limita `event_type` a un catálogo cerrado (patrón existente de V20/V22/V28). | No aplica: es la única vía permitida (no se editan migraciones integradas). |
