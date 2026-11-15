# Data Model: Whistle — aviso manual a un deudor

**Feature**: specs/030-whistle | **Date**: 2026-11-15

Una tabla nueva y un valor nuevo en el catálogo de eventos. No se modifica ninguna tabla existente salvo el `CHECK` de `group_activity.event_type`.

## DebtWhistle — tabla `debt_whistles` (nueva, V37)

| Campo | Tipo | Regla |
|-------|------|-------|
| `id` | `BIGSERIAL PRIMARY KEY` | |
| `group_id` | `BIGINT NOT NULL REFERENCES groups (id)` | Grupo de la deuda; forma parte de la clave del límite |
| `debt_id` | `BIGINT NOT NULL REFERENCES debts (id)` | Deuda desde la que se silbó. Las deudas no se borran físicamente: `deletePersonal` rechaza gastos con deudas, y el patrón es el mismo de `reminders.debt_id` en V27 |
| `creditor_user_id` | `BIGINT NOT NULL REFERENCES users (id)` | Quien silba; copia de `debts.creditor_user_id` |
| `debtor_user_id` | `BIGINT NOT NULL REFERENCES users (id)` | Deudor registrado (por Q3 nunca es un simulado). Si Q3 cambia, pasa a nullable y se agrega `debtor_simulated_profile_id` |
| `status` | `VARCHAR(10) NOT NULL DEFAULT 'SENDING'` | `CHECK (status IN ('SENDING', 'SENT', 'FAILED'))` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` | Inicio del intento; base de la ventana de 24 h. El servicio lo asigna con el `Clock` de la aplicación, para que las IT con `@MockitoBean Clock` controlen la ventana. El `DEFAULT` es solo una red de seguridad |
| `sent_at` | `TIMESTAMPTZ NULL` | `CHECK (status <> 'SENT' OR sent_at IS NOT NULL)` |
| `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` | |

**Índice**: `ix_debt_whistles_pair ON debt_whistles (group_id, creditor_user_id, debtor_user_id, created_at DESC)`, para la consulta del límite (FR-007).

**Ciclo de vida**: `SENDING` → `SENT` (correo enviado) o `SENDING` → `FAILED` (falla del SMTP o sin correo). Las filas no se borran.

**Regla del límite** (FR-007, FR-008): un par (grupo, acreedor, deudor) está bloqueado mientras exista una fila con `status IN ('SENDING', 'SENT')` y `created_at > now − 24 h`. Tiempo restante = `max(created_at) + 24 h − now`, redondeado hacia arriba al segundo.

**Sin backfill**: es una tabla nueva y vacía. No hace falta `UPDATE` (NFR-004).

## GroupActivity — tabla `group_activity` (existente)

Sin cambios de estructura. V37 reemplaza `group_activity_event_type_check` con la lista vigente de V36 más `'DEBT_WHISTLED'`:

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
        'DEBT_REMINDER_SENT', 'PAYER_DUE_NOTICE_SENT',
        'RECURRENCE_PAUSED', 'RECURRENCE_RESUMED', 'RECURRENCE_FINISHED',
        'DEBT_WHISTLED'));
```

| Evento | Actor | `details` (JSON) | Cuándo |
|--------|-------|------------------|--------|
| `DEBT_WHISTLED` | acreedor | `{ whistleId, debtId, expenseId, amount, currency, fromName, toName, description }`: `amount` es el saldo pendiente al silbar, `fromName` el deudor, `toName` el acreedor y `description` el concepto | Transacción 2 de research D3, solo si el correo salió |

## Debt — tabla `debts` (existente, solo lectura)

Campos usados: `group_id`, `creditor_user_id`, `debtor_type`, `debtor_user_id`, `debtor_name`, `status`, `amount`, `paid_amount`, `currency` y `expense_id`. Whistle **no** escribe en `debts` (FR-006).

## GroupMember — tabla `group_members` (existente, sin cambios de esquema)

Se agrega un método de repositorio con `@Lock(PESSIMISTIC_WRITE)` sobre la fila (grupo, acreedor) para serializar los silbidos (research D3). También se usa para comprobar que el deudor sigue siendo miembro activo (`left_at IS NULL`, FR-009).
