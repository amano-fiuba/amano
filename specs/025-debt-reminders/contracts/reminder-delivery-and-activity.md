# Contrato interno: generación, envío y registro de recordatorios

**Feature**: `025-debt-reminders` | **Spec**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md)

Este contrato describe el comportamiento de la tarea programada, el contenido de los correos y los eventos del historial. No expone endpoints nuevos de envío.

## 1. Disparador

- Tarea `@Scheduled` diaria `ReminderJob`, cron `${reminders.cron:0 45 0 * * *}`, zona `America/Argentina/Buenos_Aires`.
- Se ejecuta después del job de vencimiento de 022 (`0 30 0 * * *`), para que una deuda que vence ese día no se envíe si el job de vencimiento ya la pasó a `OVERDUE`.
- Usa el `Clock` inyectable del proyecto. "Hoy" es `LocalDate.now(clock)`.
- La ejecución es idempotente y segura con varias instancias.

## 2. Cálculo de fechas

Para una deuda:

```text
dueDate  = GroupExpense.expenseDate + Group.debtDueDays
sendDate = dueDate - Group.reminderSendDays
```

Para el aviso al pagador, `dueDate` y `sendDate` se calculan igual a partir del gasto.

Si `Group.debtDueDays` es `NULL`, no hay fecha límite y no se crean recordatorios.

## 3. Elegibilidad

### Deuda

Se crea/envía recordatorio cuando:

- `Group.debtDueDays` no es `NULL`;
- `Debt.status IN (PENDING, PARTIALLY_PAID, PAYMENT_REPORTED)`;
- `today <= dueDate`.

No se crea/envía cuando:

- `Debt.status IN (PAID, VERIFIED)` (saldada);
- `Debt.status = OVERDUE` o `today > dueDate` (vencida);
- el grupo no tiene `debtDueDays`.

### Aviso al pagador

Se crea/envía cuando:

- `Group.debtDueDays` no es `NULL`;
- el gasto tiene al menos una deuda elegible;
- `today <= dueDate`.

No se envía si todas las deudas del gasto están saldadas o vencidas.

## 4. Máquina de estados y concurrencia

Estados persistidos: `SCHEDULED`, `SENT`, `CANCELLED`, `FAILED`.

```text
SCHEDULED ──(objetivo saldado/vencido/sin fecha)──► CANCELLED
SCHEDULED ──(reclamar: claimed_at)──► intento ──(éxito)──► SENT
                                          └────(fallo)──► FAILED
```

Reglas:

- Una deuda tiene a lo sumo una fila `DEBT`; un gasto, a lo sumo una fila `PAYER_NOTICE` (índices únicos parciales).
- El envío se reclama con una actualización condicional: `WHERE status = 'SCHEDULED' AND claimed_at IS NULL`. Si otra instancia ya la reclamó, no se envía.
- El envío ocurre fuera de la transacción de reclamo. Después se marca `SENT` (con `sent_at`, `subject` y `body`) o `FAILED`.
- Una fila con `claimed_at` no se reintenta. Si el proceso se cae después de reclamar, la siguiente ejecución la marca `FAILED` y avisa a los administradores.
- `SENT`, `CANCELLED` y `FAILED` son estados finales.
- Reprogramar o cancelar solo aplica a `SCHEDULED` con `claimed_at IS NULL`.

## 5. Contenido de los correos

Todos los textos están en español con voseo y se envían en texto plano (`setText(body, false)`).

### 5.1 Recordatorio de deuda

**Asunto**: `Recordatorio de deuda - {nombre del grupo}`

**Cuerpo sin mensaje personalizado (predeterminado)**:

```text
Hola {nombre del deudor}:

Te recordamos que en el grupo «{nombre del grupo}» tenés una deuda pendiente
de {monto} {moneda} por «{concepto}», con fecha límite {fecha límite}.

{si la deuda está en PAYMENT_REPORTED}
Tu pago está informado y pendiente de verificación (pagado a verificar).
```

**Cuerpo con mensaje personalizado**:

```text
{mensaje personalizado}

Grupo: {nombre del grupo}
Concepto: {concepto}
Monto: {monto} {moneda}
Fecha límite: {fecha límite}

{si la deuda está en PAYMENT_REPORTED}
Tu pago está informado y pendiente de verificación (pagado a verificar).
```

Reglas:

- `concepto` es `GroupExpense.description`; si es nulo o vacío, se usa "Gasto del grupo".
- `monto` es `Debt.amount` formateado con dos decimales y `Debt.currency`.
- `fecha límite` es `dueDate` en formato `dd/MM/yyyy`.
- El mensaje personalizado acompaña a los datos; no los reemplaza.
- El recordatorio no incluye enlaces ni datos del acreedor.
- No se envía copia al acreedor.

### 5.2 Aviso al pagador

**Asunto**: `Aviso de vencimiento - {nombre del grupo}`

**Cuerpo (predeterminado)**:

```text
Hola {nombre del pagador}:

En el grupo «{nombre del grupo}», el gasto «{concepto}» tiene fecha límite
{fecha límite}: {n} días restantes para el vencimiento.

{si el pagador tiene deudas pendientes}
También tenés deudas pendientes en el grupo por {total} {moneda}.

{si el pagador no tiene deudas pendientes}
No tenés deudas pendientes en el grupo.
```

Reglas:

- `n` es `ChronoUnit.DAYS.between(today, dueDate)` al momento del envío; puede ser 0.
- El total pendiente suma `Debt.getOutstanding()` de deudas no saldadas con `debtorUserId = payerUserId`. Se agrupa por moneda; si hay varias, se lista una línea por moneda.
- La parte del pagador en su propio gasto no es deuda y no se suma.
- No se usa el mensaje personalizado del grupo.
- Si el pagador no debe nada, se dice explícitamente.

## 6. Fallos de entrega

- `ReminderMailService` propaga la excepción de `JavaMailSender`; no usa `@Async`.
- El despachador captura el fallo, marca `FAILED`, continúa con los demás recordatorios y no reintenta.
- Se envía un correo a **todos los `ADMIN` activos** del grupo:

**Asunto**: `No se pudo enviar un recordatorio - {nombre del grupo}`

**Cuerpo**:

```text
Hola:

No se pudo enviar el recordatorio de {tipo} del grupo «{nombre del grupo}»
a {destinatario} ({correo}).

Fecha límite: {fecha límite}.
Monto: {monto} {moneda}.
No se reintentará el envío.
```

- `tipo` es "deuda" o "aviso de vencimiento".
- Si el aviso a los administradores falla, solo se registra en el log; no se propaga.
- El fallo no se registra en el historial de actividad.
- Un fallo no bloquea los demás recordatorios del grupo ni de otros grupos.

## 7. Eventos del historial de actividad

Se registran solo los envíos exitosos, en la misma transacción que marca `SENT`.

### `DEBT_REMINDER_SENT`

```json
{
  "reminderId": 12,
  "debtId": 34,
  "expenseId": 56,
  "amount": "3333.33",
  "currency": "ARS",
  "fromName": "Juan Pérez",
  "toName": "Ana López",
  "dueDate": "2026-11-20"
}
```

- `actor` es `null` (evento del sistema).
- `fromName` es el deudor; `toName` el acreedor.
- `dueDate` en formato ISO `YYYY-MM-DD`.

### `PAYER_DUE_NOTICE_SENT`

```json
{
  "reminderId": 13,
  "expenseId": 56,
  "payerName": "Ana López",
  "concept": "Súper",
  "dueDate": "2026-11-20",
  "daysRemaining": 5,
  "pendingTotals": [
    { "currency": "ARS", "amount": "1200.00" }
  ]
}
```

- `actor` es `null` (evento del sistema).
- `pendingTotals` es `[]` si el pagador no debe nada.
- `daysRemaining` es el valor calculado al momento del envío.

El frontend muestra estos eventos con las frases:

- `DEBT_REMINDER_SENT`: "envió el recordatorio de la deuda de {monto} a {deudor}".
- `PAYER_DUE_NOTICE_SENT`: "envió el aviso de vencimiento del gasto «{concepto}» a {pagador}".

Ambos eventos aparecen en el filtro "Recordatorios" del historial y enlazan al detalle del gasto cuando el detalle trae `expenseId`.

## 8. Ejemplos de cálculo

| Escenario | `debtDueDays` | `reminderSendDays` | `expenseDate` | `dueDate` | `sendDate` | Resultado con `today = 2026-11-15` |
|-----------|---------------|--------------------|---------------|-----------|------------|-----------------------------------|
| Configuración predeterminada | 30 | 5 | 2026-10-21 | 2026-11-20 | 2026-11-15 | Se envía |
| Faltan 7 días | 30 | 7 | 2026-10-21 | 2026-11-20 | 2026-11-13 | Se envía (fecha pasada, vence el 20) |
| Aún no llega | 30 | 5 | 2026-10-25 | 2026-11-24 | 2026-11-19 | Queda programado |
| Deuda vencida | 30 | 5 | 2026-10-01 | 2026-10-31 | 2026-10-26 | No se envía; se cancela |
| Sin días de vencimiento | `NULL` | 5 | 2026-10-21 | — | — | No se crea |
| Vencimiento más cercano que el tiempo | 3 | 7 | 2026-11-13 | 2026-11-16 | 2026-11-09 | Se envía el 15 (fecha pasada, aún no venció) |

## 9. Idempotencia observable

- Ejecutar el job dos veces el mismo día envía como máximo un correo por deuda y uno por gasto.
- Reprogramar la configuración no reenvía recordatorios ya `SENT`.
- Un recordatorio `FAILED` no se reintenta.
- Cambiar el mensaje no altera los correos ya enviados.
