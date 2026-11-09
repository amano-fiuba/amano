# Quickstart: Calendario de vencimientos de gastos compartidos recurrentes

**Generated**: 2026-11-08

Este quickstart verifica la historia US3 de la spec 028: la primera ocurrencia vence como
gasto normal, las ocurrencias generadas vencen en su propia fecha y ambos respaldan la
opción de recordatorio de 2 días antes.

## 1. Levantar el entorno

```bash
docker compose up -d
./gradlew backend:bootRun   # o el wrapper equivalente de este repo
npm run dev --prefix frontend
```

Relevancia de los jobs de fondo para una demo rápida:

```bash
export REMINDERS_CRON='0 * * * * *'
export GROUP_RECURRENCE_CRON='0 * * * * *'
```

DB PostgreSQL local y MailPit en `http://localhost:8025`.

## 2. Configurar el grupo

En la UI del grupo (o vía `PUT /api/groups/{id}/reminder-settings`):

- `recurrenceFrequency`: `MONTHLY` (registrado el `08/11/2026`).
- `debtDueDays`: `7`.
- `reminderSendDays`: `2` (nueva opción, válida para grupos recurrentes y no recurrentes).

## 3. Gasto de referencia (serie 028)

`POST /api/groups/{id}/expenses` con:

```json
{
  "concept": "Alquiler",
  "amount": 50000.00,
  "expenseDate": "2026-11-08",
  "expenseType": "RECURRING",
  "recurrenceFrequency": "MONTHLY"
}
```

Esperado:

- el gasto queda visible en el detalle del grupo como primera ocurrencia,
- `dueDate` del gasto = `15/11/2026`,
- no se genera otra ocurrencia para el `08/11/2026`.

## 4. Cadencia de recordatorio

- Con `reminderSendDays = 2`, el job de recordatorios intenta el envío el `13/11/2026`
  (dos días antes del vencimiento `15/11/2026`).
- En MailPit `http://localhost:8025` aparece el correo al deudor.

## 5. Segunda ocurrencia

- Dentro de la ventana de anticipación de 7 días (a partir del `01/12/2026`) el job
  genera la siguiente ocurrencia (`recurrence_source_id` apunta a la serie,
  `recurrence_frequency NULL`), fechada en el `08/12/2026`.
- Su `dueDate` es el propio `08/12/2026` (y no se recalculan cuando cambian los días del grupo).
- Con `reminderSendDays = 2` el recordatorio sale el `06/12/2026`.

## 6. Deuda vencida

- Si el deudor no paga hasta el `2026-11-16`, el `OverdueDebtJob` transiciona la
  `Debts` a `OVERDUE` el mismo día en que la `dueDate` del gasto expira.

## 7. Cambios posteriores de configuración

- Cambiar `debtDueDays` del grupo a `0` después de creada la serie **no** modifica la
  `dueDate` ya persistida del gasto ni la de las ocurrencias generadas.
- Cambiar `reminderSendDays` a `1` ajusta la fecha de **envío** (no el vencimiento).
