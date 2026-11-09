# Implementation Plan: Gastos compartidos recurrentes

**Branch**: `feat/recurrence-due-dates` | **Date**: 2026-11-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/028-group-recurring-expenses/spec.md`

## Summary

Esta iteración no reimplenta la recurrencia de grupo. La base ya existe; el objetivo es
cerrar su calendario por período:

1. **Fecha de vencimiento determinista por gasto.** Una `GroupExpense` guarda su propia
   `dueDate`. Eso permite que la primera ocurrencia use el plazo del grupo al momento
   de crearse, y que cada ocurrencia generada use su propia fecha, sin recalcularse
   cuando cambian los días del grupo.
2. **Recordatorio de 2 días antes.** La lista de días de envío de recordatorios se
   extiende a `2` para **todos** los grupos, heredando el flujo ya existente de 025.
3. **Nada cambia en la lógica base de serie, ocurrencias, deudas o pagos.** El job de
   vencimiento y el de recordatorios pasan a usar el vencimiento ya calculado.

## Technical Context

**Language/Version**: Java 25 (backend); JavaScript con React 19.2 (frontend)

**Primary Dependencies**: Spring Boot 4.1.1; Spring Data JPA/Flyway/`@Scheduled`, React, Vite. **Sin dependencias nuevas.**

**Storage**: PostgreSQL con migraciones Flyway en `backend/src/main/resources/db/migration`. Se agregan **V33** y **V34**, sin editar migraciones ya integradas.

**Testing**: JUnit 5 + Mockito, Spring Boot Test + Testcontainers, Vitest + Testing Library. JaCoCo y `Vitest coverage` con umbral 85 %; no se baja.

**Target Platform**: Servidor Linux vía Docker Compose; navegador web desktop y móvil.

**Project Type**: Aplicación web monorepo (backend REST + frontend SPA).

**Performance Goals**: El job diario sigue siendo secuencial y se limita a la serie activa y a las deudas de un grupo; no se agrega caché ni paralelismo.

**Constraints**:
- Montos con `BigDecimal` con escala 2 y sin punto flotante.
- Fechas a nivel de día con `Clock` inyectable y zona `America/Argentina/Buenos_Aires`.
- La opción de **2 días antes** aplica a grupos con o sin gastos recurrentes.
- Las ocurrencias generadas **no** vuelven a validar membreza, reparto ni conversión.
- El vencimiento de cada gasto **ya se guarda**, así cambios de configuración futuros no lo recalculan.

**Scale/Scope** (estimado): backend con 2 migraciones nuevas, 1 helper de vencimiento, 2 modificaciones de `GroupExpense`/`ReminderSchedule` y ajustes en los jobs existentes. Frontend con 1 const de opciones y 1 test delabels. No se agregan endpoints nuevos; la configuración de recordatorios sigue usando el endpoint de 025.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principio | Estado | Evidencia |
|---|-----------|--------|-----------|
| I | Arquitectura | PASS | Extiende `groupexpense` y `groups`, reutilizando la recurrencia de 028, el job de recordatorios de 025 y el job de vencimiento de 022. No hay capas ni proyectos nuevos. |
| II | Desarrollo guiado por especificaciones | PASS | Plan derivado de la spec 028 y sus clarificaciones del 2026-11-08; cada decisión se remite a FR/SC. |
| III | Entrega incremental | PASS | La base ya está implementada. Este ciclo agrega solo lo pendiente del calendario y el recordatorio de 2 días antes. |
| IV | Alcance controlado | PASS | No se tocan pagos, estados de deuda ni la recurrencia personal. La opción de 2 días es la única extensión al flujo existente. |
| V | Calidad y cobertura | PASS | Unitarias y de integración para due date, reminder y overdue; pruebas de UI para la opción de 2 días. |
| VI | Integridad de reglas | PASS | Las deudas siguen siendo `PENDING`, `PARTIALLY_PAID`, `PAYMENT_REPORTED` o `OVERDUE`; no se inventan estados. |
| VII | Contratos REST | PASS | No se agregan endpoints. La sola validación de `reminder-settings` permite el valor 2 ya soportado por el contrato existente. |
| VIII | Validación y errores | PASS | El valor de 2 días se valida en el servidor; la UI solo lo muestra. |
| IX | Seguridad | PASS | No se cambian permisos; los recordatorios siguen siendo de un único `ADMIN` o sistema. |
| X | Código mantenible | PASS | `ReminderSchedule`/`GroupExpense` centralizan el cálculo; nombres en inglés, textos en español. |
| XI | Entorno reproducible | PASS | Migraciones versionadas con Flyway y cron por variable de entorno; pruebas con Testcontainers y GreenMail. |
| XII | Revisión por pares | PENDIENTE (posterior) | PR aún no abierto. |
| XIII | Commits y trazabilidad | PENDIENTE (posterior) | Conventional Commits al implementar. |

**Gate resultado**: PASS. No hay violaciones que justificar.

## Decisiones de diseño

**D1. Guardar la fecha de vencimiento en la propia `GroupExpense`.**
Se agrega `group_expenses.due_date DATE NULL`. Así:
- un gasto normal y la primera ocurrencia guardan el vencimiento calculado al momento de crearse;
- una ocurrencia generada guarda el vencimiento propio de su fecha (su expenseDate);
- un cambio posterior en `debtDueDays` del grupo **no** recalcula vencimientos ya creados.

**D2. Un único origen de vencimiento.**
`ReminderMaterializer` y `ReminderDispatcher` dejan de recalculaph `expenseDate + group.debtDueDays` y usan el vencimiento ya guardado. El `OverdueDebtJob` usa el mismo criterio para detectar deudas vencidas.

**D3. La primera ocurrencia es el gasto original.**
No se genera otra ocurrencia para el día de registro. La serie guarda `recurrence_next_date` en el período siguiente y la primera ocurrencia queda lista para deuda/recordatorio como un gasto normal.

**D4. Opción de 2 días en la configuración de recordatorios.**
`GroupReminderSettingsService` y la UI permiten `sendDays = 2` **para todos los grupos**, junto con `7`, `5`, `3` y `1`. No se agrega un flujo especial solo para recurrentes.

**D5. Sin endpoints nuevos.**
La configuración de recordatorios reutiliza `PUT /api/groups/{id}/reminder-settings`; el Habilitar/permiso y el error de valor inválido siguen los contratos ya existentes.

**D6. Sutilezas de fecha.**
- `dueDate` en la primera ocurrencia se calcula con el plazo vigente del grupo al crearla.
- `dueDate` de una ocurrencia generada se calcula al momento de crear la ocurrencia, con su propia fecha.
- Si el grupo no tiene `debtDueDays`, el gasto no genera vencimiento ni recordatorio de tipo deuda/aviso de pagador.

**D7. Casos límite relevantes.**
- Grupo sin `debtDueDays`: no se crean recordatorios.
- Grupo que cambia `debtDueDays`: la próxima ocurrencia se genera con el valor vigente, pero el vencimiento ya creado del gasto/ocurrencia no cambia.
- La primera y segunda ocurrencia siempre son dos gastos distintos, con su propia deuda y su propio vencimiento.

## Project Structure

### Documentation (this feature)

```text
specs/028-group-recurring-expenses/
├── plan.md                  # Este archivo
├── research.md              # Fase 0
├── data-model.md            # Fase 1
├── quickstart.md            # Fase 1
├── contracts/               # Fase 1
│   ├── recurrence-due-calendar.md
│   └── reminder-settings-2-days.md
├── checklists/requirements.md
└── tasks.md                 # Fase 2 (/speckit.tasks), no lo crea este comando
```

### Source Code (repository root)

```text
backend/src/main/java/com/amano/backend/
├── groupexpense/
│   ├── GroupExpense.java                     # modificado: dueDate
│   ├── GroupExpenseService.java              # modificado: asigna dueDate al crear
│   └── GroupRecurringExpenseService.java     # modificado: asigna dueDate a ocurrencias
└── groups/
    └── GroupReminderSettingsService.java     # modificado: extiende ALLOWED_SEND_DAYS con 2

backend/src/main/resources/db/migration/
├── V33__group_expense_due_date.sql           # nuevo
└── V34__groups_reminder_send_days_2.sql      # nuevo

backend/src/test/java/com/amano/backend/
├── groupexpense/...                          # pruebas de dueDate y vencimiento/reminder/overdue
└── groups/...                                # pruebas de reminder settings

frontend/src/
├── components/groups/GroupReminderSettingsPanel.jsx
└── components/groups/GroupReminderSettingsPanel.test.jsx
```

**Structure Decision**: continúa el monorepo backend/`frontend` vigente. No se agregan paquetes ni proyectos nuevos.

## Complexity Tracking

No hay violaciones que justificar. El diseño mantiene una sola tabla de gastos, una sola tabla de deudas y no agrega un sistema de recordatorios paralelo.
