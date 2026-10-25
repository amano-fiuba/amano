# Data Model: Pausar o finalizar un gasto recurrente (009)

**Fecha**: 2026-10-24 | **Spec**: [spec.md](./spec.md)

**Alcance de datos**: la feature **no crea tablas**. Altera `expenses` (creada por `V4__create_expenses.sql`, feature 007) con la migración `V5__expense_recurrence.sql`, que agrega cuatro columnas nullable, sus restricciones y dos índices. No modifica filas existentes.

## Entidades

### Expense (existente — 007, extendida)

Una misma tabla representa tres tipos de fila, distinguidos por las columnas nuevas:

| Tipo de fila | `expense_type` | `recurrence_frequency` | `recurrence_status` | `recurrence_next_date` | `recurrence_source_id` |
|---|---|---|---|---|---|
| Gasto único | `ONE_TIME` | `NULL` | `NULL` | `NULL` | `NULL` |
| **Serie** (gasto recurrente registrado desde 009) | `RECURRING` | `WEEKLY` / `MONTHLY` / `YEARLY` | `ACTIVE` / `PAUSED` / `FINISHED` | fecha si `ACTIVE`/`PAUSED`; `NULL` si `FINISHED` | `NULL` |
| **Repetición** (generada automáticamente) | `RECURRING` | `NULL` | `NULL` | `NULL` | id de la serie |
| Recurrente heredado de 007 (sin frecuencia) | `RECURRING` | `NULL` | `NULL` | `NULL` | `NULL` |

Columnas nuevas:

| Columna | Tipo | Restricciones | Regla |
|---------|------|---------------|-------|
| `recurrence_frequency` | `VARCHAR(10)` | `NULL`; `CHECK IN ('WEEKLY','MONTHLY','YEARLY')` | Solo en series (FR-001); nunca en `ONE_TIME` (FR-002) ni en repeticiones |
| `recurrence_status` | `VARCHAR(10)` | `NULL`; `CHECK IN ('ACTIVE','PAUSED','FINISHED')` | Presente si y solo si hay frecuencia (FR-007) |
| `recurrence_next_date` | `DATE` | `NULL` | Próxima fecha a generar. Obligatoria en `ACTIVE`/`PAUSED`, `NULL` en `FINISHED` (FR-009) |
| `recurrence_source_id` | `BIGINT` | `NULL`; FK `expenses(id)` `ON DELETE SET NULL` | Serie de origen de una repetición |

Restricciones nuevas (`V5`):

| Nombre | Regla |
|--------|-------|
| `ck_expenses_recurrence_frequency` | catálogo de frecuencias |
| `ck_expenses_recurrence_status` | catálogo de estados |
| `ck_expenses_recurrence_series` | `(recurrence_frequency IS NULL) = (recurrence_status IS NULL)` |
| `ck_expenses_recurrence_type` | una serie es siempre `RECURRING`; un `ONE_TIME` no tiene frecuencia ni serie de origen |
| `ck_expenses_recurrence_next_date` | `ACTIVE`/`PAUSED` ⇒ próxima fecha no nula; `FINISHED` o sin estado ⇒ próxima fecha nula |
| `ck_expenses_recurrence_occurrence` | una repetición (`recurrence_source_id` no nulo) no tiene frecuencia propia |
| `fk_expenses_recurrence_source` | `recurrence_source_id → expenses(id) ON DELETE SET NULL` |
| `uq_expenses_recurrence_occurrence` | `UNIQUE (recurrence_source_id, expense_date)`: a lo sumo una repetición por serie y fecha (FR-006) |

Índices nuevos:

- `ix_expenses_recurrence_due (recurrence_next_date) WHERE recurrence_status = 'ACTIVE'`: consulta diaria de series vencidas (FR-005).
- `ix_expenses_recurrence_owner (user_id, created_at DESC) WHERE recurrence_frequency IS NOT NULL`: listado "Gastos recurrentes" (FR-012).

Mapeo JPA (`com.amano.backend.expense.Expense`): `recurrenceFrequency` (`@Enumerated STRING`, enum `RecurrenceFrequency`), `recurrenceStatus` (`@Enumerated STRING`, enum `RecurrenceStatus`), `recurrenceNextDate` (`LocalDate`), `recurrenceSourceId` (`Long`, sin relación JPA para no cargar la serie con cada repetición).

### RecurrenceFrequency (enum nuevo)

| Código | Etiqueta | Fecha n de la serie (inicio `d`) |
|--------|----------|----------------------------------|
| `WEEKLY` | Semanal | `d + 7·n días` |
| `MONTHLY` | Mensual | `d.plusMonths(n)` (último día del mes si `d` no existe) |
| `YEARLY` | Anual | `d.plusYears(n)` (29/02 → 28/02 en años no bisiestos) |

Las fechas siempre se calculan desde el inicio `d` (FR-004), nunca sumando un período a la fecha anterior: con inicio 31/01, la serie mensual es 28/02, 31/03, 30/04 y no 28/02, 28/03, 28/04.

### RecurrenceStatus (enum nuevo)

`ACTIVE`, `PAUSED`, `FINISHED` (etiquetas: Activo, Pausado, Finalizado).

## Reglas de negocio

**Registro de una serie** (`ExpenseService.registerExpense`, extiende 007):

1. `type = RECURRING` ⇒ `frequency` obligatoria y del catálogo; `type = ONE_TIME` ⇒ `frequency` ausente (FR-001/FR-002). Los errores se acumulan con los de 007 en un único 400.
2. Se guarda `recurrence_status = ACTIVE` y `recurrence_next_date = primera fecha de la serie > hoy` (hoy en la zona del dispositivo; UTC si la zona es inválida). Como la fecha del gasto nunca es futura (007), esto excluye cualquier fecha retroactiva (FR-003).
3. La frecuencia forma parte del payload comparado por la idempotencia de 007.

**Generación diaria** (`RecurringExpenseService.generateDueOccurrences(hoy)`, disparada por `RecurringExpenseScheduler`):

1. Series con `recurrence_status = 'ACTIVE' AND recurrence_next_date <= hoy`.
2. Por cada fecha vencida `f` (desde `recurrence_next_date` hasta hoy): insertar una repetición con los datos de la serie, `expense_date = f`, `recurrence_source_id = serie.id` e `idempotency_key = UUID v3("amano-recurrence:" + serie.id + ":" + f)`, determinística.
3. `recurrence_next_date = primera fecha de la serie > f`.
4. Doble protección contra duplicados: `uq_expenses_recurrence_occurrence` y `uq_expenses_user_idempotency_key` (de 007) con la clave determinística (FR-006).

**Cambio de estado** (`RecurringExpenseService.changeStatus(usuario, id, estado)`):

| Desde \ Hacia | `ACTIVE` | `PAUSED` | `FINISHED` |
|---|---|---|---|
| `ACTIVE` | sin cambios (200) | `next_date` se conserva | `next_date = NULL` |
| `PAUSED` | `next_date = primera fecha de la serie ≥ hoy` | sin cambios (200) | `next_date = NULL` |
| `FINISHED` | 409 | 409 | sin cambios (200) |

"Hoy" para reanudar se toma del `Clock` de la recurrencia (zona `recurrence.zone`).

## Transiciones de estado

```text
           registrar (US1)
[—] ───────────────────────▶ ACTIVE ◀──────── reanudar ────────┐
                               │  │                             │
                               │  └─────── pausar ──────▶ PAUSED
                               │                             │
                               └── finalizar ──▶ FINISHED ◀──┘ finalizar
                                                 (definitivo)

Solo ACTIVE genera repeticiones (job diario). Pedir el estado actual = éxito sin cambios.
```

## Relaciones

- `User` 1 — 0..N `Expense` (sin cambios, 007).
- Serie 1 — 0..N repeticiones (`recurrence_source_id`). Si la serie llegara a eliminarse (hoy no hay operación de borrado), las repeticiones se conservan como gastos sueltos (`ON DELETE SET NULL`).

## Cambios de esquema

```sql
-- V5__expense_recurrence.sql (Feature 009). Sin DELETE ni UPDATE (regla del equipo).
ALTER TABLE expenses
    ADD COLUMN recurrence_frequency VARCHAR(10) NULL,
    ADD COLUMN recurrence_status    VARCHAR(10) NULL,
    ADD COLUMN recurrence_next_date DATE NULL,
    ADD COLUMN recurrence_source_id BIGINT NULL;
-- + CHECKs, FK, UNIQUE e índices de la tabla anterior (ver archivo de migración).
```

Las filas existentes quedan con las cuatro columnas en `NULL`, lo que todas las restricciones admiten (FR-015, SC-006).
