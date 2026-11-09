# Data Model: Calendario de vencimientos de gastos compartidos recurrentes

**Generated**: 2026-11-08

## GroupExpense (modificado — `group_expenses`)

| Campo | Tipo | Semántica |
|---|---|---|
| `due_date` (nuevo) | `DATE NULL` | Vencimiento persistido del gasto. Non-recurrente y primera ocurrencia: `expense_date + groups.debt_due_days` vigente al crear. Ocurrencia generada: su propia `expense_date`. NULL si el grupo no tiene `debt_due_days`. |

Columnas de recurrencia existentes (V32, sin cambios): `recurrence_frequency`,
`recurrence_status`, `recurrence_next_date`, `recurrence_source_id`.

### Reglas de derived-state

- El campo nunca se recalcula en lecturas ni cuando cambia la configuración del grupo.
- `ReminderSchedule.dueDate(expenseDate, debtDueDays)` queda como helper **de construcción**
  (gasto normal / primera ocurrencia); las lecturas ya no lo invocan.
- `ReminderMaterializer`, `ReminderDispatcher` y el cálculo de deudas vencidas leen
  `expense.getDueDate()`.

## GroupReminderSettings (modificado — `groups`)

| Campo | Tipo | Semántica |
|---|---|---|
| `reminder_send_days` (V34) | `INTEGER NOT NULL DEFAULT 5` | Valores permitidos: `7, 5, 3, 2, 1` (se agrega `2`, disponible para todos los grupos). |

- La restricción `ck_groups_reminder_send_days` de V26 se reemplaza por una que incluye `2`.
- `sendDate = dueDate - reminderSendDays` (sin cambios).

## Debt (sin cambios)

- Estados `PENDING`, `PARTIALLY_PAID`, `PAYMENT_REPORTED`, `OVERDUE` idénticos a 002/022.
- El `dueDate` mostrado en recordatorios/avisos proviene del `GroupExpense` al que
  pertenece la deuda, no se duplica por deudor.

## Relaciones afectadas

- `GroupExpense.recurrence_source_id -> GroupExpense.id` (V32): la ocurrencia generada
  hereda el concepto, pero `due_date` **no** se copia: se construye con su propia fecha.
- `groups.debt_due_days` sigue existiendo, pero se usa solo al crear el vencimiento del
  gasto/primera ocurrencia, no al leerlo.

## Invariants

1. `due_date` es constante por fila desde su creación (o el backfill de V33) hasta que
   el gasto se elimine.
2. `due_date` de una ocurrencia generada (`recurrence_source_id IS NOT NULL`) es
   `expense_date` de esa fila.
3. Si `debt_due_days` del grupo es NULL o 0 y el gasto no es ocurrencia generada,
   `due_date` es NULL y no hay recordatorio ni vencimiento.
4. `groups.reminder_send_days ∈ {7, 5, 3, 2, 1}`; el valor 2 es válido para grupos
   recurrentes y no recurrentes.
