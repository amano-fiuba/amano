# Contrato: Calendario de vencimientos de gastos compartidos recurrentes

**Spec**: specs/028-group-recurring-expenses/spec.md · **Generated**: 2026-11-08

No se agrega ningún endpoint. Este contrato describe el comportamiento derivado que la
implementación ya expone vía REST de gastos compartidos, recordatorios y deudas vencidas.

## Fórmula general

La fecha de vencimiento (`dueDate`) vive en la fila del `GroupExpense`:

| Gasto | `dueDate` |
|---|---|
| Gasto normal del grupo o primera ocurrencia de la serie | `expense_date + groups.debt_due_days` vigente al crear (NULL si el grupo no tiene `debt_due_days`) |
| Ocurrencia generada (`recurrence_source_id IS NOT NULL`) | su propia `expense_date` |

El `dueDate` vive internamente en la `GroupExpense` y alimenta recordatorios y deudas
vencidas; el contrato REST no lo expone por ahora. Si la UI necesita mostrarlo en el
detalle del gasto, se agrega en una historia aparte.

## Recordatorios (025 US1/US3)

- `ReminderMaterializer` y `ReminderDispatcher` usan `expense.getDueDate()` como
  vencimiento del gasto y calculan `sendDate = dueDate - group.reminderSendDays`.
- Aviso al pagador financiero: reutiliza la regla ya definida en 025; su `dueDate` es la
  del gasto persistida.
- Perfil del grupo sin `debtDueDays`: `dueDate` es NULL, no se materializa recordatorio ni
  aviso.

## Deudas vencidas (022 US1)

- El `OverdueDebtJob` detección de `OVERDUE` viaja por el `dueDate` persistido en el
  `GroupExpense`: una deuda queda vencida cuando la fecha del servidor excede el
  vencimiento, sin depender de `groups.debt_due_days` al momento del chequeo.

## Cascada de generación de ocurrencias

- `GroupRecurringExpenseService.generateOccurrence` crea la `GroupExpense` de la
  repetición con `dueDate = expenseDate` (FR-016) y la deudas correspondientes.
- El job **anticipa** la generación: corre cuando `recurrence_next_date <= today + 7`
  (ventana de anticipación, máx del catálogo de días de recordatorio). Así la deuda
  existe antes de `sendDate = dueDate - reminderSendDays` y 025 puede programar el envío.
- `recurrence_next_date` de la serie avanza conforme a la frecuencia (V32, ya vigente).
- Cada ocurrencia genera sus `Debts` con los mismos estados que un gasto normal; la
  `dueDate` visible por los recordatorios es la de la `GroupExpense` generada.
