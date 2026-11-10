# Data Model: Recordatorios automáticos de deudas pendientes

**Feature**: `025-debt-reminders` | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md)

**Created**: 2026-11-07

La 025 no crea estados de deuda ni modifica las entidades de gastos, deudas o pagos. Extiende `groups`, agrega `reminders` y amplía los tipos del historial de actividad.

## Entity: Group (existente, extendida)

**Source**: `backend/src/main/java/com/amano/backend/groups/Group.java`

| Campo | Tipo | Nulo | Descripción | Restricciones |
|-------|------|------|-------------|---------------|
| `id` | `Long` | No | Clave primaria | — |
| `name` | `String` | No | Nombre del grupo | — |
| `description` | `String` | Sí | Descripción | — |
| `photo` | `String` | Sí | Foto del grupo | — |
| `primaryCurrency` | `Currency` | No | Moneda principal (022) | `ARS`, `USD`, `EUR` |
| `debtDueDays` | `Integer` | Sí | Días hasta el vencimiento (022) | `NULL` = nunca vence; `>= 0` |
| `reminderSendDays` | `Integer` | No | Días antes del vencimiento en que se envía el recordatorio | Nuevo; por defecto `5`; `CHECK IN (7, 5, 3, 1)` |
| `reminderMessage` | `String` | Sí | Mensaje personalizado del recordatorio | Nuevo; máximo 500 caracteres; `NULL` = mensaje predeterminado |
| `createdAt` / `updatedAt` | `OffsetDateTime` | No | Auditoría | — |

**Reglas**:
- `reminderSendDays` siempre tiene un valor; no existe "sin configurar". El valor 5 representa tanto el predeterminado como una elección explícita.
- `reminderMessage` en blanco se normaliza a `NULL` antes de guardar.
- Solo un `ADMIN` activo puede consultar o modificar estos dos campos.
- Si `debtDueDays` es `NULL`, la configuración de recordatorios existe pero no genera envíos.

## Entity: Reminder (nueva)

**Source**: nueva, `backend/src/main/java/com/amano/backend/groupexpense/reminder/Reminder.java`
**Table**: `reminders`

Representa tanto el recordatorio de una deuda (`kind = 'DEBT'`) como el aviso al pagador de un gasto (`kind = 'PAYER_NOTICE'`). La spec los describe como dos entidades con reglas y estados compartidos; una sola tabla con discriminador evita duplicar la programación, el reclamo y el ciclo de vida.

| Campo | Tipo | Nulo | Descripción | Restricciones |
|-------|------|------|-------------|---------------|
| `id` | `Long` | No | Clave primaria | `GenerationType.IDENTITY` |
| `groupId` | `Long` | No | Grupo al que pertenece | FK a `groups(id)` |
| `kind` | `ReminderKind` | No | `DEBT` o `PAYER_NOTICE` | `CHECK` |
| `debtId` | `Long` | Sí | Deuda recordada | FK a `debts(id)`; obligatorio si `kind = DEBT` |
| `expenseId` | `Long` | Sí | Gasto avisado al pagador | FK a `group_expenses(id)`; obligatorio si `kind = PAYER_NOTICE` |
| `sendDate` | `LocalDate` | No | Fecha calculada de envío | `dueDate - group.reminderSendDays` |
| `status` | `ReminderStatus` | No | Estado del envío | `SCHEDULED`, `SENT`, `CANCELLED`, `FAILED` |
| `claimedAt` | `OffsetDateTime` | Sí | Instante en que se reclamó para envío | Interno; una fila reclamada no se reintenta |
| `sentAt` | `OffsetDateTime` | Sí | Instante del envío exitoso | Obligatorio si `status = SENT` |
| `subject` | `String` | Sí | Asunto realmente enviado | Instantánea; `TEXT`; no cambia después |
| `body` | `String` | Sí | Cuerpo realmente enviado | Instantánea; no cambia después |
| `createdAt` / `updatedAt` | `OffsetDateTime` | No | Auditoría | — |

**Relaciones**:
- `N:1` con `Group`.
- `N:1` con `Debt` cuando `kind = DEBT` (una fila por deuda).
- `N:1` con `GroupExpense` cuando `kind = PAYER_NOTICE` (una fila por gasto).

**Invariantes**:
- `(kind = 'DEBT' AND debt_id IS NOT NULL AND expense_id IS NULL) OR (kind = 'PAYER_NOTICE' AND expense_id IS NOT NULL AND debt_id IS NULL)`.
- Una deuda tiene como máximo una fila `DEBT` (índice único parcial).
- Un gasto tiene como máximo una fila `PAYER_NOTICE` (índice único parcial).
- `sendDate = dueDate - reminderSendDays`. Puede quedar antes de la fecha del gasto cuando `debtDueDays < reminderSendDays`; en ese caso el envío ocurre en la primera evaluación con `today <= dueDate` (caso límite de la spec).
- `status = SENT` implica `sentAt IS NOT NULL` y `subject/body` no nulos.
- `status = SCHEDULED` y `claimedAt IS NULL` es la única combinación reprogramable o cancelable.
- `status = CANCELLED` no tiene `sentAt`.
- `status = FAILED` conserva `claimedAt` (se intentó) y no se reintenta.

### Estados de `Reminder`

```text
                 crear
                   │
                   ▼
              SCHEDULED ──────────────► CANCELLED
                   │                      (objetivo saldado, vencido
                   │                       o sin fecha límite)
                   │ reclamar (claimed_at)
                   ▼
              (intento) ───────────────► FAILED
                   │                      (fallo de entrega; avisa al admin)
                   │ éxito
                   ▼
                 SENT
```

- `SCHEDULED`: programado, aún no reclamado. Se puede reprogramar o cancelar.
- `SENT`: envío exitoso; estado final. No cambia ante cambios de configuración.
- `CANCELLED`: ya no corresponde; estado final. No se envía.
- `FAILED`: se intentó y falló; estado final. No se reintenta.

`claimedAt` distingue "programado y nunca intentado" de "reclamado por una ejecución". Si una fila queda `SCHEDULED` con `claimedAt` no nulo (caída del proceso), la siguiente ejecución la pasa a `FAILED` y avisa al administrador; no la reenvía.

## Entity: Debt (existente, solo lectura)

**Source**: `backend/src/main/java/com/amano/backend/groupexpense/Debt.java`

La 025 no modifica `Debt` ni `DebtStatus`. Usa:

| Campo | Uso en la 025 |
|-------|---------------|
| `id` | Objetivo del recordatorio `DEBT` |
| `groupId` | Grupo del recordatorio |
| `expenseId` | Gasto originario; concepto y fecha del gasto |
| `debtorType` | `REGISTERED` o `SIMULATED`; resuelve el correo |
| `debtorUserId` / `debtorSimulatedProfileId` | Destinatario |
| `debtorName` | Nombre para el correo |
| `creditorUserId` | Pagador/acreedor; no recibe copia |
| `currency` / `amount` / `paidAmount` | Monto del recordatorio y total pendiente |
| `status` | Elegibilidad |

**Regla de elegibilidad**: `status IN (PENDING, PARTIALLY_PAID, PAYMENT_REPORTED)` y `expenseDate + group.debtDueDays >= today`. Se excluyen `PAID`, `VERIFIED` y `OVERDUE`.

## Entity: GroupExpense (existente, solo lectura)

**Source**: `backend/src/main/java/com/amano/backend/groupexpense/GroupExpense.java`

La 025 usa:

| Campo | Uso |
|-------|-----|
| `id` | Objetivo del aviso `PAYER_NOTICE` |
| `groupId` | Grupo |
| `payerUserId` | Destinatario del aviso |
| `expenseDate` | Base de la fecha límite |
| `description` | Concepto del correo |
| `currency` | Moneda del gasto (informativa) |

**Regla**: un gasto genera aviso si tiene al menos una deuda en estado elegible y `expenseDate + group.debtDueDays >= today`.

## Entity: GroupActivity (existente, extendida)

**Source**: `backend/src/main/java/com/amano/backend/groups/GroupActivity.java`

Se agregan dos valores al enum `GroupActivityType` y al `CHECK` de `group_activity.event_type`:

- `DEBT_REMINDER_SENT`: se envió un recordatorio de deuda.
- `PAYER_DUE_NOTICE_SENT`: se envió un aviso al pagador.

**Detalles JSON** (el servicio omite las claves nulas):

Recordatorio de deuda:

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

Aviso al pagador:

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

Si el pagador no debe nada, `pendingTotals` es `[]`. El historial no registra fallos.

## Entity: User / SimulatedProfile (existentes, solo lectura)

- Deudor registrado: `User.email` (verificado por 003).
- Deudor simulado: `SimulatedProfile.email` (cargado por el administrador al agregarlo).
- Pagador: `User.email`; siempre es una cuenta registrada (017).
- Administradores para el aviso de fallo: filas activas de `group_members` con `role = 'ADMIN'` y `left_at IS NULL`; se resuelve su `User.email`.

## Validation rules

### Configuración (`PUT /api/groups/{id}/reminder-settings`)

| Campo | Regla | Error |
|-------|-------|-------|
| `sendDays` | Si viene, debe ser `7`, `5`, `3` o `1` | 400, `fields.sendDays = "El tiempo de envío debe ser 7, 5, 3 o 1 días antes"` |
| `message` | Si viene, se recorta; vacío → `NULL`; más de 500 caracteres → rechazo | 400, `fields.message = "El mensaje no puede superar los 500 caracteres"` |
| Permiso | El llamador debe ser `ADMIN` activo | 403 |
| Grupo | Debe existir y el llamador debe ser miembro activo | 404 neutral |

La actualización es atómica: si la validación falla, no se guarda nada y la configuración anterior se conserva.

### Programación

| Regla | Resultado |
|-------|-----------|
| `group.debtDueDays == null` | No se crean recordatorios; los existentes `SCHEDULED` se cancelan |
| `dueDate < today` o deuda `OVERDUE` | No se envía; `SCHEDULED` se cancela |
| `sendDate <= today <= dueDate` | Se envía en la evaluación |
| `sendDate > today` | Queda `SCHEDULED` |
| `sendDate < today` y `dueDate >= today` | Se envía en la evaluación (fecha pasada) |
| Deuda `PAID`/`VERIFIED` | `SCHEDULED` se cancela; no se envía |
| Deuda `PAYMENT_REPORTED` | Se envía con la leyenda "pagado a verificar" |
| Gasto sin deudas elegibles | `SCHEDULED` se cancela; no se envía aviso |
| Recordatorio ya `SENT`/`FAILED` | No se toca |

## SQL de las migraciones

### V26 — configuración de recordatorios en `groups`

```sql
ALTER TABLE groups
    ADD COLUMN reminder_send_days INTEGER NOT NULL DEFAULT 5;

ALTER TABLE groups
    ADD COLUMN reminder_message VARCHAR(500) NULL;

ALTER TABLE groups
    ADD CONSTRAINT ck_groups_reminder_send_days
    CHECK (reminder_send_days IN (7, 5, 3, 1));

ALTER TABLE groups
    ADD CONSTRAINT ck_groups_reminder_message
    CHECK (reminder_message IS NULL OR char_length(reminder_message) <= 500);
```

### V27 — tabla `reminders`

```sql
CREATE TABLE reminders (
    id          BIGSERIAL PRIMARY KEY,
    group_id    BIGINT       NOT NULL REFERENCES groups (id),
    kind        VARCHAR(20)  NOT NULL,
    debt_id     BIGINT       NULL REFERENCES debts (id),
    expense_id  BIGINT       NULL REFERENCES group_expenses (id),
    send_date   DATE         NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    claimed_at  TIMESTAMPTZ  NULL,
    sent_at     TIMESTAMPTZ  NULL,
    subject     TEXT         NULL,
    body        TEXT         NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_reminders_kind
        CHECK (kind IN ('DEBT', 'PAYER_NOTICE')),
    CONSTRAINT ck_reminders_status
        CHECK (status IN ('SCHEDULED', 'SENT', 'CANCELLED', 'FAILED')),
    CONSTRAINT ck_reminders_target
        CHECK (
            (kind = 'DEBT' AND debt_id IS NOT NULL AND expense_id IS NULL)
            OR (kind = 'PAYER_NOTICE' AND expense_id IS NOT NULL AND debt_id IS NULL)
        ),
    CONSTRAINT ck_reminders_sent
        CHECK (
            status <> 'SENT'
            OR (sent_at IS NOT NULL AND subject IS NOT NULL AND body IS NOT NULL)
        )
);

CREATE UNIQUE INDEX uq_reminders_debt
    ON reminders (debt_id)
    WHERE kind = 'DEBT';

CREATE UNIQUE INDEX uq_reminders_expense
    ON reminders (expense_id)
    WHERE kind = 'PAYER_NOTICE';

CREATE INDEX ix_reminders_due
    ON reminders (group_id, status, send_date);
```

### V28 — tipos de evento del historial

```sql
ALTER TABLE group_activity DROP CONSTRAINT group_activity_event_type_check;

ALTER TABLE group_activity ADD CONSTRAINT group_activity_event_type_check
    CHECK (event_type IN (
        'MEMBER_ADDED', 'MEMBER_REMOVED', 'MEMBER_LEFT',
        'PERMISSION_CHANGED', 'PHOTO_CHANGED',
        'EXPENSE_CREATED', 'EXPENSE_EDITED', 'EXPENSE_DELETED',
        'PAYMENT_MADE', 'PAYMENT_REGISTERED',
        'PAYMENT_CONFIRMED', 'PAYMENT_REJECTED',
        'DEBT_OVERDUE', 'LIQUIDATION_PROPOSED',
        'DEBT_REMINDER_SENT', 'PAYER_DUE_NOTICE_SENT'));
```

## Datos derivados (no persistidos)

| Dato | Cálculo |
|------|---------|
| Fecha límite (`dueDate`) | `GroupExpense.expenseDate.plusDays(group.debtDueDays)` |
| Fecha de envío (`sendDate`) | `dueDate.minusDays(group.reminderSendDays)` |
| Días restantes (aviso al pagador) | `ChronoUnit.DAYS.between(today, dueDate)` |
| Monto del recordatorio | `Debt.amount` en `Debt.currency` |
| Total pendiente del pagador | Suma de `Debt.getOutstanding()` de deudas no saldadas con `debtorUserId = payerUserId`, agrupada por moneda |
| Elegibilidad de deuda | Estado en `PENDING`, `PARTIALLY_PAID`, `PAYMENT_REPORTED` y `today <= dueDate` |
| Elegibilidad de aviso | Al menos una deuda elegible del gasto y `today <= dueDate` |

## Notas de consistencia

- La 025 no modifica `DebtStatus` ni las transiciones de 017/022.
- La 025 no modifica los flujos de pago de 018/022; solo lee el estado resultante.
- La 025 no configura `debtDueDays`; eso sigue siendo parte de 022.
- El historial de actividad conserva el patrón de filas independientes: guarda `groupId` y los nombres en los detalles, sin depender de que la deuda o el gasto sigan existiendo.
- Las instantáneas `subject`/`body` de las filas `SENT`/`FAILED` no se recalculan; las filas `SCHEDULED` se renderizan al momento del envío con la configuración vigente.
