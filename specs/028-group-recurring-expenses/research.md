# Fase 0: Investigación — Calendario de vencimientos de gastos compartidos recurrentes

**Generated**: 2026-11-08

## Decisión 1: ¿Dónde vive la fecha de vencimiento?

**Decisión**: nueva columna `group_expenses.due_date DATE NULL` (V33).

**Alternativas descartadas**:
- Recalcular `expenseDate + group.debtDueDays` en cada lectura (estado actual): un cambio
  de `debt_due_days` del grupo mueve retroactivamente vencimientos ya creados; viola
  FR-019.
- Guardar la fecha en `debts`: duplicaría el dato por cada deudor de un mismo gasto
  cuando todos comparten el mismo vencimiento.

**Consecuencias**:
- Primera ocurrencia / gasto normal: `dueDate = expenseDate + debtDueDays` vigente al
  crearlo (o NULL si el grupo no tiene `debtDueDays`).
- Ocurrencia generada: `dueDate = expenseDate` (su propia fecha; FR-016).
- Cambios posteriores de `debt_due_days` no recalculan nada ya creado (FR-019).
- V33 rellena hacia atrás con la regla vigente del grupo; para filas generadas
  (`recurrence_source_id IS NOT NULL`) usa la fecha propia del gasto.

## Decisión 2: ¿Quién usa el vencimiento?

**Decisión**: `ReminderMaterializer` y `ReminderDispatcher` dejan de llamar
`ReminderSchedule.dueDate(expense.getExpenseDate(), group.getDebtDueDays())` y leen
`expense.getDueDate()`; `ReminderSchedule.dueDate(expenseDate, debtDueDays)` queda solo
para construir la fecha al crear un gasto común o la primera ocurrencia.

**Razón**: elimina la divergencia entre recordatorios y deudas vencidas, y deja un solo
origen de verdad (plan D2).

**El `OverdueDebtJob`** sigue su lógica de fechas ya existente basada en el vencimiento;
si detecta la deuda vencida vía `GroupExpense`, debe usar `dueDate` guardada.

## Decisión 3: Opción de recordatorio de 2 días antes

**Decisión**: agregar `2` a la lista blanca en `GroupReminderSettingsService.ALLOWED_SEND_DAYS`
(hoy `List.of(7, 5, 3, 1)`) y en el `<select>` de
`frontend/src/components/groups/GroupReminderSettingsPanel.jsx`
(hoy `const ALLOWED_SEND_DAYS = [7, 5, 3, 1]`), disponibles **para todos los grupos**.

**Base de datos**: V34 reemplaza la restricción de V26:

```sql
ALTER TABLE groups DROP CONSTRAINT ck_groups_reminder_send_days;
ALTER TABLE groups
    ADD CONSTRAINT ck_groups_reminder_send_days
    CHECK (reminder_send_days IN (7, 5, 3, 2, 1));
```

Aplica a grupos con y sin gastos recurrentes (clarificación 2026-11-08, punto 2).

## Decisión 4: La primera ocurrencia es el gasto original

**Decisión**: al registrar la serie, el gasto original es la primera ocurrencia. Su
`dueDate` sale de `debtDueDays` vigente al crearlo; no se genera una segunda fila para
el mismo día.

- Ejemplo (spec, escenario 1): gasto del `08/11/2026` con `debt_due_days=7` → vence
  `15/11/2026`; recordatorio de 2 días → `13/11/2026`.
- Segunda ocurrencia (generación mensual): `08/12/2026` → vence `08/12/2026`;
  recordatorio de 2 días → `06/12/2026`.

## Decisión 5: Cambio de plazo del grupo con la serie ya creada

FR-019 (versión normalizada) dice: los vencimientos ya creados no se recalculan; los
datos del grupo se usan sólo al crear un gasto (o la primera ocurrencia). Las
ocurrencias generadas fijan su vencimiento al crearse (FR-016) y no vuelven a leer el
plazo. Sin job ni callback que re-vence los vencimientos ya guardados cuando cambia
la configuración.

## Decisión 6: Ventana de anticipación de 7 días

**Decisión**: el job de recurrencia genera la ocurrencia cuando
`recurrence_next_date <= today + 7` (máximo del catálogo de `reminder_send_days`).

**Razón**: tal como estaba, la ocurrencia se creaba el mismo día que vencía; el
recordatorio de "2 días antes" (o de 7/5/3) no podría programarse porque
`ReminderMaterializer` solo arma recordatorios para deudas que ya existen
(ReminderMaterializer.java:62) y `sendDate = dueDate - sendDays` ya estaría en el
pasado. Con la ventana, la ocurrencia y sus deudas existen antes del vencimiento y el
envío se programa para la fecha correcta (ejemplo: vencimiento `08/12/2026` con recordatorio `2 días antes` → envío `06/12/2026`).

**Consecuencias**:
- La ocurrencia queda con `expense_date` futura (~7 días visibles antes de su fecha).
- El `OverdueDebtJob` no considera vencidas deudas cuya `dueDate` es futura; sin cambio.
- El backfill sigue recuperando fechas atrasadas (hasta hoy) y, además, crea las que
  entran en la ventana, aunque el límite de 1000 por serie y ejecución se mantiene.

## Riesgos observados

1. **Divergencia entre dos caminos que calculaban el due date por separado**
   (`ReminderMaterializer` y `ReminderDispatcher`): se resuelve leyendo la columna.
2. **Vencimientos históricos**: V33 los fija con la regla vigente del grupo como
   mejor estimación; luego quedan inmutables.
3. **Grupos sin `debt_due_days`**: `dueDate` queda NULL; sin recordatorio ni vencido.
   Cubre el caso ya contemplado en 025.
4. **Ventana vs. opción cambiante**: el margen de anticipación queda fijo en 7 días
   (máximo del catálogo); si el grupo baja su opción de recordatorio, la deuda ya
   existe y no hay problema; si sube, la ocurrencia ya se generó dentro de la ventana.
