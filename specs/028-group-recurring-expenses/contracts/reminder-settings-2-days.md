# Contrato: Configuración de recordatorios — opción de 2 días antes

**Spec**: specs/028-group-recurring-expenses/spec.md · **Generated**: 2026-11-08

No se agrega ningún endpoint. Se extiende el contrato de 025
(`specs/025-debt-reminders/contracts/group-reminder-settings.md`) con el valor `2`.

## Endpoints afectados

- `GET  /api/groups/{id}/reminder-settings` (sin cambios en la firma).
- `PUT  /api/groups/{id}/reminder-settings` (sin cambios en la firma).

## Semántica de `reminderSendDays`

| Valor | Significado |
|---|---|
| `7` | Una semana antes del vencimiento |
| `5` | Cinco días antes |
| `3` | Tres días antes |
| `2` *(nuevo)* | Dos días antes |
| `1` | Un día antes |

- Disponible para **todos los grupos**, con o sin gastos recurrentes.
- `sendDate = dueDate - reminderSendDays`. El `dueDate` ya vive en la `GroupExpense`
  (ver `contracts/recurrence-due-calendar.md`).

## Validación

- Cliente: el `<select>` de `GroupReminderSettingsPanel.jsx` ofrece las cinco opciones.
- Servidor: `GroupReminderSettingsService.ALLOWED_SEND_DAYS = [7, 5, 3, 2, 1]`; con
  cualquier otro valor devuelve el mismo error de 400 que ya existe para valores
  inválidos (`422` según la convención de 002 y 025).
- Base de datos: la restricción `ck_groups_reminder_send_days` de V26 se reemplaza en V34
  para aceptar `2` además de `7, 5, 3, 1`.
