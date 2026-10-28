# Implementation Plan: Pausar o finalizar un gasto recurrente

**Branch**: `feat/definir-tipo-gasto` | **Date**: 2026-10-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-pause-recurring-expense/spec.md`

## Summary

La tarea pide pausar o finalizar la "generación automática periódica" de un gasto, que no existía: en 007 "Recurrente" es solo una clasificación. La feature agrega lo mínimo para que exista y se pueda controlar. **US1**: `POST /api/expenses` exige `frequency` (`WEEKLY`/`MONTHLY`/`YEARLY`) para `RECURRING` y guarda el gasto como serie `ACTIVE` con su próxima fecha, sin retroactividad. **US2**: `RecurringExpenseScheduler` corre a diario y `RecurringExpenseService` registra las repeticiones vencidas de las series activas, con clave determinística y un índice único contra duplicados. **US3/US4**: `GET /api/expenses/recurring` lista las series y `PATCH /api/expenses/{id}/status` las pausa, reanuda (sin recuperar la pausa) o finaliza (definitivo). En el frontend, `ExpenseForm` gana el campo "Frecuencia" y la nueva pantalla `RecurringExpensesPage` (`/expenses/recurring`, accesible desde `SiteNav`) ofrece "Pausar", "Reanudar" y "Finalizar" con estados de carga, confirmación y notificaciones. Una migración (`V5`) que solo agrega columnas, restricciones e índices a `expenses`; sin dependencias nuevas.

## Technical Context

**Language/Version**: Java 25 (backend); JavaScript con React 19.2 (frontend)

**Primary Dependencies**: Spring Boot 4.1.1 (Web MVC, Data JPA, Flyway, Security, `@Scheduled`); React 19.2, react-router 8.4, react-hook-form 7.90 (`useWatch`), Vite 8.3. **Sin dependencias nuevas.**

**Storage**: PostgreSQL + Flyway. `V5__expense_recurrence.sql` altera `expenses` (4 columnas nullable, 6 CHECK, 1 FK, 1 UNIQUE, 2 índices parciales). Sin `UPDATE`/`DELETE` de datos.

**Testing**: JUnit 5 + Mockito (`RecurrenceFrequencyTest`, `ExpenseRecurrenceRegistrationTest`, `RecurringExpenseServiceTest`), Spring Boot Test + Testcontainers (`RecurringExpenseIT`), Vitest + Testing Library (`RecurringExpensesPage.test.jsx` y ampliaciones de `ExpenseForm`, `expenseClient`, `expenseCatalog`, `SiteNav`, más `expenseFormat.test.js`). Cobertura JaCoCo y Vitest ≥ 85 %.

**Target Platform**: Servidor Linux vía Docker Compose; navegador web desktop y móvil.

**Project Type**: Aplicación web monorepo (backend REST + frontend SPA).

**Performance Goals**: la generación diaria usa un índice parcial sobre las series activas; SC-004 (cambio de estado < 10 s) no requiere optimización.

**Constraints**: no alterar filas de 007 (FR-015); contrato de 007 intacto para `ONE_TIME`; sesión verificada; mensajes en voseo; montos sin punto flotante; cobertura ≥ 85 %.

**Scale/Scope**: 1 migración, 2 endpoints nuevos y 1 modificado, 1 tarea programada, 1 pantalla nueva y 1 modificada; ~12 archivos nuevos y ~14 modificados (ver Project Structure).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principio | Estado | Evidencia |
|---|-----------|--------|-----------|
| I | Arquitectura y separación de responsabilidades | PASS | Todo dentro del paquete `expense/` de 007; reutiliza `Expense`, `ExpenseRepository`, `ExpenseResponse`, `ExpenseController`, `ProblemDetailExceptionHandler`, `SelectField`, `expenseCatalog`; la tarea programada sigue el patrón de `UnverifiedAccountCleanupScheduler` |
| II | Desarrollo guiado por especificaciones | PASS | `specs/009-pause-recurring-expense/` completa; FR citados en código y tests |
| III | Entrega incremental por historia | PASS | US1 → US2 → US3 → US4 con checkpoint por historia (tasks.md) |
| IV | Alcance controlado | PASS (con justificación) | La recurrencia (US1/US2) es prerrequisito explícito del pedido ("si no existe, definila"). Fuera de alcance: historial de gastos, edición/eliminación, fecha de fin, otras frecuencias (spec §Assumptions) |
| V | Calidad, pruebas y cobertura | PASS | 33 unitarios + 11 IT nuevos en backend, 33 tests nuevos en frontend; `mvn -B verify` y `npm run test:coverage` en verde |
| VI | Integridad de las reglas de negocio | PASS | La app solo registra (no mueve dinero); los gastos de 007 no se alteran; las repeticiones son del mismo dueño |
| VII | Contratos REST y compatibilidad | PASS | `contracts/recurring-expenses-api.md`; `ONE_TIME` responde igual que en 007; campos nuevos opcionales (`NON_NULL`) |
| VIII | Validación y manejo de errores | PASS | Validación en cliente y servidor; 400 por campo, 404 neutro, 409 con código; restricciones de base como respaldo |
| IX | Seguridad y control de acceso | PASS | `/api/expenses/**` exige cuenta verificada; el dueño sale de la sesión; series ajenas = inexistentes |
| X | Código mantenible y convenciones | PASS | Convenciones `*Test`/`*IT`, `*.test.jsx`, comentarios con trazabilidad; lint sin errores ni advertencias nuevas |
| XI | Entorno reproducible | PASS | Propiedades nuevas con valor por defecto; la migración corre sola al arrancar |
| XII | Revisión por pares | PENDIENTE (posterior) | PR aún no abierto |
| XIII | Commits y trazabilidad | PENDIENTE (posterior) | Cambios sin commitear; los commits los hace el equipo |

**Gate resultado**: PASS. IV se cumple con la ampliación documentada en spec.md (Relación con otras especificaciones) y research.md §D2–D4.

## Project Structure

### Documentation (this feature)

```text
specs/009-pause-recurring-expense/
├── plan.md                           # Este archivo (/speckit.plan)
├── research.md                       # Fase 0
├── data-model.md                     # Fase 1
├── quickstart.md                     # Fase 1
├── contracts/
│   └── recurring-expenses-api.md     # Fase 1
├── checklists/
│   └── requirements.md               # /speckit.specify
└── tasks.md                          # Fase 2 (/speckit.tasks)
```

### Source Code (repository root)

```text
backend/src/main/
├── resources/db/migration/V5__expense_recurrence.sql      # A: columnas, CHECKs, FK, UNIQUE, índices
├── resources/application.properties                       # M: recurrence.zone, recurrence.cron
└── java/com/amano/backend/
    ├── BackendApplication.java                            # M: registra RecurrenceProperties
    ├── common/ProblemDetailExceptionHandler.java          # M: 404 y 409 de recurrencia
    └── expense/
        ├── RecurrenceFrequency.java                       # A: WEEKLY/MONTHLY/YEARLY + aritmética de fechas
        ├── RecurrenceStatus.java                          # A: ACTIVE/PAUSED/FINISHED
        ├── RecurrenceProperties.java                      # A: zona y cron
        ├── RecurringExpenseService.java                   # A: listado, cambio de estado, generación
        ├── RecurringExpenseScheduler.java                 # A: @Scheduled diario
        ├── RecurringExpenseNotFoundException.java         # A: 404 neutro
        ├── RecurringExpenseFinishedException.java         # A: 409 RECURRENCE_FINISHED
        ├── Expense.java                                   # M: 4 columnas nuevas + isRecurringSeries()
        ├── ExpenseResponse.java                           # M: frequency, status, nextOccurrenceDate
        ├── ExpenseRepository.java                         # M: consultas de series y vencidas
        ├── ExpenseService.java                            # M: validación de frecuencia, alta de la serie
        ├── RegisterExpenseRequest.java                    # M: campo frequency
        └── ExpenseController.java                         # M: GET /recurring, PATCH /{id}/status

backend/src/test/java/com/amano/backend/expense/
├── RecurrenceFrequencyTest.java                           # A: 8
├── ExpenseRecurrenceRegistrationTest.java                 # A: 10
├── RecurringExpenseServiceTest.java                       # A: 15
├── RecurringExpenseIT.java                                # A: 11
├── ExpenseServiceTest.java                                # M: mutación RECURRING con frecuencia
├── RegisterExpenseIT.java                                 # M: payload RECURRING con frecuencia
└── ExpenseSchemaIT.java                                   # M: columnas de V5 en la lista exacta

frontend/src/
├── api/expenseClient.js                  # M: listRecurringExpenses, updateRecurringExpenseStatus
├── validation/expenseCatalog.js          # M: FREQUENCY_OPTIONS, STATUS_OPTIONS
├── validation/expenseFormat.js           # A: formatAmount, formatIsoDate (sin floats)
├── components/ExpenseForm.jsx            # M: campo "Frecuencia" (useWatch)
├── components/SiteNav.jsx                # M: acceso "Gastos recurrentes"
├── pages/RecurringExpensesPage.jsx       # A: /expenses/recurring
├── App.jsx                               # M: ruta protegida
├── index.scss                            # M: estilos de la lista
└── **/*.test.js(x)                       # A: RecurringExpensesPage, expenseFormat; M: ExpenseForm, expenseClient, expenseCatalog, SiteNav
```

**Structure Decision**: monorepo existente; todo el backend nuevo vive en el paquete de dominio `expense/` de 007 y el frontend en `pages/`, `api/` y `validation/`.

## Complexity Tracking

| Elemento | Detalle | Justificación / acción |
|----------|---------|------------------------|
| Cambio de regla de 007 | FR-010 de 007 ("Recurrente no exige frecuencia") deja de valer para gastos nuevos | Sin frecuencia no hay generación que pausar; los datos viejos se conservan (FR-015) |
| Tests de 007 modificados | `RegisterExpenseIT.absentDescriptionIsOmittedAndPersistedAsNull` (payload `RECURRING` + `frequency`), `ExpenseServiceTest.anyDifferentNormalizedFieldIsAnIdempotencyConflictWithoutInserting` (mutación de tipo + frecuencia, y `storedExpense` copia la frecuencia) y `ExpenseSchemaIT.flywayCreatesTheExpensesTableWithTheExpectedColumns` (4 columnas nuevas) | Consecuencia directa de V5 y FR-001; ningún test se eliminó ni se debilitó |
| Duplicación menor | El helper de "hoy en la zona del dispositivo" de `ExpenseService` se parece a `ExpenseDateValidator.limitOf`, pero sin la tolerancia de un día | Semántica distinta (límite superior vs. día actual); se deja separado y documentado |
| Repeticiones sin pantalla | Las repeticiones generadas no se ven todavía: no existe un listado de gastos | Fuera de alcance; será parte del historial de gastos |
