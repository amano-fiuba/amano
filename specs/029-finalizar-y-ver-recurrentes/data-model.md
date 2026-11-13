# Data Model: Finalizar y ver gastos recurrentes de grupo

**Feature**: specs/029-finalizar-y-ver-recurrentes | **Date**: 2026-11-13

Modelo de datos de la feature sobre las entidades existentes. Nada de esto reemplaza a las entidades de 028: solo agrega campos y reglas.

## GroupExpense (serie y ocurrencia) — tabla `group_expenses`

Campos de recurrencia vigentes (V32) más el nuevo anclaje (V35):

| Campo | Tipo | Regla | Fuente |
|-------|------|-------|--------|
| `recurrence_frequency` | VARCHAR(10) NULL | `WEEKLY`/`MONTHLY`/`YEARLY`; `NULL` = gasto normal u ocurrencia | 028 |
| `recurrence_status` | VARCHAR(10) NULL | `ACTIVE`/`PAUSED`/`FINISHED`; `NULL` si no hay frecuencia | 028 (FINISHED ya admitido por el CHECK de V32) |
| `recurrence_next_date` | DATE NULL | NOT NULL si `ACTIVE`/`PAUSED`; `NULL` si `FINISHED` o sin frecuencia | 028 |
| `recurrence_source_id` | BIGINT NULL | FK a `group_expenses.id`; `NOT NULL` solo en ocurrencias; único por `(source_id, expense_date)` | 028 |
| **`recurrence_anchor_date`** | **DATE NULL (nuevo, V35)** | **Inicio del ciclo actual de la serie; NOT NULL si hay frecuencia. Backfill: `= expense_date` para las series existentes. Al reanudar: `= fecha de reanudación`.** | 029 FR-003 |
| **`recurrence_created_via`** | **VARCHAR(8) NULL (nuevo, V35)** | **Solo en ocurrencias: `'JOB'` (generada por el job) o `'RESUME'` (creada al reanudar). Backfill: `'JOB'` para las ocurrencias existentes. CHECK: `NULL` si y solo si `recurrence_source_id IS NULL`.** | 029 FR-013 |
| `due_date` | DATE NULL | Gasto normal y primera ocurrencia: `expense_date + groups.debt_due_days` vigente al crearse; ocurrencias generadas: su propia fecha; nunca se recalcula | 028 (V33) |

### Reglas del ciclo de vida (FR-001 a FR-008)

**Estados**: `ACTIVE`, `PAUSED`, `FINISHED`. Solo las series `ACTIVE` generan ocurrencias (el job consulta por `recurrence_status = 'ACTIVE'`, línea existente).

**Transiciones** (FR-007) y efecto en los campos:

| Desde | Acción | Estado final | `anchor` | `next_date` | Ocurrencias/deudas previas |
|-------|--------|--------------|----------|-------------|----------------------------|
| ACTIVE | Pausar | `PAUSED` | sin cambio | se conserva en la fila pero **deja de regir** (la UI no la muestra) | se conservan |
| PAUSED | Reanudar | `ACTIVE` | **`= hoy`** | **`= frequency.firstAfter(hoy, hoy)`** (primer período posterior a la reanudación) | se conservan |
| ACTIVE o PAUSED | Finalizar | `FINISHED` | sin cambio | **`= NULL`** | se conservan |
| — | Cambiar frecuencia (serie ACTIVE) | `ACTIVE` | sin cambio | `frequency.firstOnOrAfter(anchor, hoy)` (regla de 028 FR-022, ahora sobre `anchor`) | se conservan |

**Ocurrencia inmediata de la reanudación** (FR-003): al confirmar la reanudación se crea, en la misma transacción, una ocurrencia con:

- `expense_date = hoy`, `due_date = hoy + groups.debt_due_days` vigente (si el grupo no tiene plazo, `NULL`), `recurrence_source_id = serie`, `idempotency_key` determinística (`occurrenceKey(seriesId, hoy)` — el índice único evita duplicados si la operación se repite), copia de monto/moneda/conversión/pagador/participantes/reparto/clasificación de la serie.
- Deudas `PENDING` por participante distinto del pagador, en la moneda de deuda de la serie (`debtCurrencyOf`).
- Evento `EXPENSE_CREATED` con `actorUserId` del miembro que reanudó.
- Caso borde (idempotencia): si ya existía una ocurrencia de la serie fechada hoy (p. ej. la serie se reanuda el mismo día en que el job generó una), **no** se crea otra: la operación la respeta y solo cambia el estado y el anclaje. Consistente con el `findByRecurrenceSourceIdAndExpenseDate` que usa el job.

**Inmutabilidades** (FR-006): pausar, reanudar o finalizar no modifican monto, fecha, vencimientos ni deudas del gasto original ni de las ocurrencias ya generadas. Solo cambian `recurrence_status`, `recurrence_anchor_date` (solo reanudar) y `recurrence_next_date`.

## GroupActivity — tabla `group_activity`

Sin cambios de estructura; la migración V36 amplía el CHECK `group_activity_event_type_check` con tres valores nuevos:

| Evento | Actor | `details` (JSON) | Cuándo |
|--------|-------|------------------|--------|
| `RECURRENCE_PAUSED` | miembro que pausó | `{ expenseId, description, status: "PAUSED" }` | confirmación de pausa |
| `RECURRENCE_RESUMED` | miembro que reanudó | `{ expenseId, description, status: "ACTIVE" }` | confirmación de reanudación |
| `RECURRENCE_FINISHED` | miembro que finalizó | `{ expenseId, description, status: "FINISHED" }` | confirmación de finalización |

`EXPENSE_CREATED` sigue siendo el evento de las ocurrencias: actor `NULL` ("Sistema") para las generadas por el job y `actorUserId` del miembro para la ocurrencia inmediata de la reanudación (FR-018). `actorUserId` null continúa significando "Sistema" en el feed.

## GroupExpenseShare / Debt

Sin cambios. La reanudación copia los shares de la serie con el mismo mecanismo del job (`GroupExpenseShare.registered/simulated`) y crea `Debt.pendingFor(...)` por participante distinto del pagador. Las deudas existentes no se tocan al pausar/finalizar (FR-006).

## Agregados de la vista (FR-009 a FR-012)

Calculados al listar (no persistidos):

- **Acumulado (`accumulatedAmount`)**: `SUM(amount)` sobre `id = serie OR recurrence_source_id = serie`, en la moneda de la serie; `0` si no hay ocurrencias generadas.
- **Próximo vencimiento (`nextDueDate`)**: solo para `ACTIVE` — `min(recurrence_next_date, min(due_date de las ocurrencias de la serie con due_date >= hoy))`; `NULL` si no hay ni `next_date` ni ocurrencias futuras (p. ej. grupo sin `debt_due_days` y sin ocurrencias futuras creadas).
- **`nextOccurrenceDate`**: `NULL` para `PAUSED`/`FINISHED`; para `ACTIVE`, `recurrence_next_date` (igual que hoy).

## Origen de un gasto (FR-013)

Campo nuevo en `GroupExpenseResponse` (detalle de gasto), `NULL` si el gasto no pertenece a ninguna serie:

```json
"recurrence": { "seriesId": 42, "origin": "SERIE" | "SISTEMA" | "MIEMBRO" }
```

- `SERIE`: el gasto es el que dio origen a la serie (fila con frecuencia).
- `SISTEMA`: ocurrencia con `recurrence_created_via = 'JOB'` (generada por el job).
- `MIEMBRO`: ocurrencia con `recurrence_created_via = 'RESUME'` (creada al reanudar).

La lectura se resuelve con datos persistidos en la fila (`recurrence_created_via`, V35), no infiriendo del histórico de actividad; el job y la reanudación fijan el valor al crear la ocurrencia.

## Migraciones nuevas

1. **`V35__group_expense_recurrence_anchor.sql`**: `ALTER TABLE group_expenses ADD COLUMN recurrence_anchor_date DATE` + backfill `UPDATE ... SET recurrence_anchor_date = expense_date WHERE recurrence_frequency IS NOT NULL` + CHECK `ck_group_expenses_recurrence_anchor ((recurrence_frequency IS NULL) = (recurrence_anchor_date IS NULL))`; y `ALTER TABLE group_expenses ADD COLUMN recurrence_created_via VARCHAR(8)` + backfill `UPDATE ... SET recurrence_created_via = 'JOB' WHERE recurrence_source_id IS NOT NULL` + CHECK `ck_group_expenses_recurrence_created_via ((recurrence_source_id IS NULL) = (recurrence_created_via IS NULL))`.
2. **`V36__group_activity_recurrence_events.sql`**: `ALTER TABLE group_activity DROP CONSTRAINT group_activity_event_type_check; ADD CONSTRAINT ... CHECK (event_type IN (<lista vigente de V28> + 'RECURRENCE_PAUSED', 'RECURRENCE_RESUMED', 'RECURRENCE_FINISHED'))`.

Ambas siguen la regla del proyecto: migraciones nuevas, sin editar las ya integradas.
