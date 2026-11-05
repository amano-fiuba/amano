# Data Model: Crear estados de deuda (017)

**Fecha**: 2026-11-05 | **Spec**: [spec.md](./spec.md)

**Alcance de datos**: una migración nueva, `V13__create_group_expenses_and_debts.sql`, con tres tablas (`group_expenses`, `group_expense_shares` y `debts`) y sus índices. Solo `CREATE TABLE` y `CREATE INDEX`: no modifica tablas existentes ni filas (regla del equipo; constitución, "Bases de datos y migraciones"). `expenses` (007/009) no se toca (research.md D2). El DDL completo está en §Cambios de esquema.

## Diagrama

```text
groups 1 ──< group_expenses 1 ──< group_expense_shares 1 ──o| debts
                 │  │                    │                       │  │
 users ◀─ payer ─┘  └─ created_by ─▶ users   user_id ─▶ users     │  └─ creditor_user_id ─▶ users
                                     simulated_profile_id ─▶ simulated_profiles (SET NULL)
```

- Un gasto tiene **n partes** (una por participante, n ≥ 2 contando al pagador si participa, o n ≥ 1 si no participa).
- Cada parte de un participante distinto del pagador tiene **exactamente una deuda**. La parte del pagador no tiene deuda.

## Entidades

### GroupExpense (tabla `group_expenses`, nueva)

| Columna | Tipo | Restricciones | Notas |
|---------|------|---------------|-------|
| `id` | `BIGSERIAL` | PK | |
| `group_id` | `BIGINT` | NOT NULL, FK `groups(id)` | |
| `created_by_user_id` | `BIGINT` | NOT NULL, FK `users(id)` | Quien registró (actor del historial). Siempre el usuario de la sesión |
| `payer_user_id` | `BIGINT` | NOT NULL, FK `users(id)` | Miembro registrado activo al momento del alta |
| `amount` | `NUMERIC(11,2)` | NOT NULL, `CHECK (amount >= 0.01)` | Como `expenses.amount` (007) |
| `currency` | `VARCHAR(3)` | NOT NULL, `CHECK (currency IN ('ARS','USD','EUR'))` | Como `V4` |
| `expense_date` | `DATE` | NOT NULL | No posterior a hoy (validación en el servicio, 007) |
| `description` | `VARCHAR(200)` | NULL, `CHECK (description IS NULL OR char_length(description) <= 200)` | Como `V4` |
| `idempotency_key` | `UUID` | NOT NULL | |
| `created_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT `now()` | |

- `UNIQUE (created_by_user_id, idempotency_key)` (`uq_group_expenses_creator_idempotency_key`).
- Índice `ix_group_expenses_group (group_id, created_at DESC)`, para el futuro listado y para resolver el 404 por grupo.

### GroupExpenseShare (tabla `group_expense_shares`, nueva)

| Columna | Tipo | Restricciones | Notas |
|---------|------|---------------|-------|
| `id` | `BIGSERIAL` | PK | |
| `expense_id` | `BIGINT` | NOT NULL, FK `group_expenses(id)` | |
| `position` | `SMALLINT` | NOT NULL, `CHECK (position >= 0)` | Orden de la solicitud (define el reparto del resto, research.md D4) |
| `participant_type` | `VARCHAR(10)` | NOT NULL, `CHECK IN ('REGISTERED','SIMULATED')` | Mismos valores que el historial (014) |
| `user_id` | `BIGINT` | NULL, FK `users(id)` | Solo `REGISTERED` |
| `simulated_profile_id` | `BIGINT` | NULL, FK `simulated_profiles(id)` `ON DELETE SET NULL` | Solo `SIMULATED`; queda nulo si el perfil se borra |
| `participant_name` | `VARCHAR(255)` | NOT NULL | Copia del nombre visible al momento del gasto |
| `amount` | `NUMERIC(11,2)` | NOT NULL, `CHECK (amount >= 0.01)` | Parte que le corresponde |

- `CHECK ck_group_expense_shares_participant`: `(participant_type = 'REGISTERED' AND user_id IS NOT NULL AND simulated_profile_id IS NULL) OR (participant_type = 'SIMULATED' AND user_id IS NULL)`.
- `UNIQUE (expense_id, position)`.
- `UNIQUE (expense_id, user_id)` y `UNIQUE (expense_id, simulated_profile_id)`: sin participantes repetidos. PostgreSQL admite varios `NULL`.
- Índice `ix_group_expense_shares_expense (expense_id)`.

### Debt (tabla `debts`, nueva)

| Columna | Tipo | Restricciones | Notas |
|---------|------|---------------|-------|
| `id` | `BIGSERIAL` | PK | |
| `group_id` | `BIGINT` | NOT NULL, FK `groups(id)` | Copia del grupo del gasto, para las consultas de `DebtPort` sin join |
| `expense_id` | `BIGINT` | NOT NULL, FK `group_expenses(id)` | |
| `share_id` | `BIGINT` | NOT NULL, UNIQUE, FK `group_expense_shares(id)` | Una deuda por parte |
| `debtor_type` | `VARCHAR(10)` | NOT NULL, `CHECK IN ('REGISTERED','SIMULATED')` | |
| `debtor_user_id` | `BIGINT` | NULL, FK `users(id)` | Solo `REGISTERED` |
| `debtor_simulated_profile_id` | `BIGINT` | NULL, FK `simulated_profiles(id)` `ON DELETE SET NULL` | Solo `SIMULATED` |
| `debtor_name` | `VARCHAR(255)` | NOT NULL | Copia, como en la parte |
| `creditor_user_id` | `BIGINT` | NOT NULL, FK `users(id)` | El pagador del gasto |
| `currency` | `VARCHAR(3)` | NOT NULL, `CHECK IN ('ARS','USD','EUR')` | La del gasto |
| `amount` | `NUMERIC(11,2)` | NOT NULL, `CHECK (amount >= 0.01)` | Igual a la parte |
| `paid_amount` | `NUMERIC(11,2)` | NOT NULL DEFAULT 0, `CHECK (paid_amount >= 0 AND paid_amount <= amount)` | Lo modifica 018 |
| `status` | `VARCHAR(20)` | NOT NULL DEFAULT `'PENDING'`, `CHECK IN ('PENDING','PARTIALLY_PAID','PAID')` | |
| `created_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT `now()` | |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT `now()` | La actualiza 018 al registrar un pago |

- `CHECK ck_debts_debtor`: igual que en las partes (`REGISTERED` → `debtor_user_id` no nulo y perfil nulo; `SIMULATED` → `debtor_user_id` nulo).
- `CHECK ck_debts_not_self`: `debtor_user_id IS NULL OR debtor_user_id <> creditor_user_id`.
- `CHECK ck_debts_status_matches_paid` (research.md D5):

```sql
(status = 'PENDING'        AND paid_amount = 0)
OR (status = 'PARTIALLY_PAID' AND paid_amount > 0 AND paid_amount < amount)
OR (status = 'PAID'           AND paid_amount = amount)
```

- Índices parciales para `DebtPort` (research.md D7):
  - `ix_debts_open_debtor_user (group_id, debtor_user_id) WHERE status <> 'PAID'`
  - `ix_debts_open_debtor_profile (group_id, debtor_simulated_profile_id) WHERE status <> 'PAID'`
  - `ix_debts_open_creditor (group_id, creditor_user_id) WHERE status <> 'PAID'`
- Índice `ix_debts_expense (expense_id)` para el detalle.

## Estados de deuda

| Estado | `paid_amount` | Etiqueta en la UI | Se alcanza desde |
|--------|---------------|-------------------|------------------|
| `PENDING` | `0` | "Pendiente" | Alta (017) |
| `PARTIALLY_PAID` | `0 < x < amount` | "Pago parcial · faltan $ {saldo}" | `PENDING` (018) |
| `PAID` | `= amount` | "Pagada" | `PENDING` o `PARTIALLY_PAID` (018) |

- **Saldo** (`outstanding`) = `amount − paid_amount`. Se calcula, no se guarda.
- **Sin saldar** = `PENDING` o `PARTIALLY_PAID`. Es lo que consulta `DebtPort`.
- **Transiciones**: solo hacia adelante. `Debt.applyPayment(x)` exige `0 < x ≤ saldo`. En otro caso, `IllegalArgumentException` (monto inválido) o `IllegalStateException` (deuda ya `PAID`), sin cambios.

## Clases Java nuevas (paquete `com.amano.backend.groupexpense`)

| Clase | Tipo | Responsabilidad |
|-------|------|-----------------|
| `GroupExpense` | `@Entity` | Mapeo de `group_expenses` |
| `GroupExpenseShare` | `@Entity` | Mapeo de `group_expense_shares` |
| `Debt` | `@Entity` | Mapeo de `debts` y `applyPayment` |
| `DebtStatus` | `enum` | `PENDING`, `PARTIALLY_PAID`, `PAID`, más `of(amount, paid)` |
| `ParticipantType` | `enum` | `REGISTERED`, `SIMULATED` |
| `EqualSplit` | utilidad pura | División en centavos y reparto del resto (D4) |
| `GroupExpenseRepository`, `GroupExpenseShareRepository`, `DebtRepository` | Spring Data | `DebtRepository` incluye `existsOpenForUser` y `existsOpenForSimulatedProfile` (D7) |
| `GroupExpenseService` | `@Service` | Validación, permisos, idempotencia, creación de partes, deudas y evento |
| `GroupExpenseController` | `@RestController` | `POST` y `GET` (contrato) |
| `CreateGroupExpenseRequest`, `GroupExpenseResponse` | DTO | Contrato |

En `groups/`:

| Clase | Cambio |
|-------|--------|
| `port/DebtPort` | M: `hasPendingDebtsForUser` y `hasPendingDebtsForSimulatedProfile` en lugar de `hasPendingDebts` |
| `port/LedgerDebtPort` | A: implementación con `DebtRepository` |
| `port/NoOpDebtPort` | D: eliminado en US2 (T028); antes implementa los dos métodos (fix aislado T012) |
| `GroupPermissionChecker` | M: `canCreateExpenses`, `canExpenseForOthers` |
| `GroupService`, `GroupMemberService` | M: llaman a los métodos nuevos del puerto; `GroupService.getGroup` informa `expensesCount` real (FR-027) |
| `GroupRepository` | M: `countGroupExpenses(groupId)` (JPQL sobre `GroupExpense`) |
| `dto/GroupDetailResponse` | M: solo Javadoc de `expensesCount` |

## Reglas de negocio

1. `Σ shares.amount = expense.amount` (SC-001). Se verifica en el servicio antes de guardar.
2. Una deuda por parte de un participante distinto del pagador. Ninguna para el pagador (`ck_debts_not_self`).
3. Deuda nueva: `paid_amount = 0`, `status = PENDING`, `amount = share.amount`, `currency = expense.currency`, `creditor = payer`.
4. Gasto, partes, deudas y `EXPENSE_CREATED` en una transacción (`@Transactional`; `recordExpenseCreated` es `MANDATORY`).
5. Nombres copiados al momento del gasto. Nunca se actualizan.
6. Sin `UPDATE` ni `DELETE` desde 017. `paid_amount`, `status` y `updated_at` solo los modifica 018 mediante `applyPayment`.

## Claves foráneas y borrados

Convención de `V6`–`V12`: `BIGSERIAL` para las PK y `REFERENCES` sin `ON DELETE` (es decir, `NO ACTION`) salvo que se indique otra cosa.

| FK | `ON DELETE` | Motivo |
|----|-------------|--------|
| `group_expenses.group_id`, `debts.group_id` → `groups` | `NO ACTION` | La app no borra grupos. Si algún día lo hace, tiene que decidir antes qué pasa con las deudas |
| `group_expenses.created_by_user_id`, `payer_user_id` → `users` | `NO ACTION` | Las deudas no pueden quedar sin acreedor |
| `group_expense_shares.user_id`, `debts.debtor_user_id`, `debts.creditor_user_id` → `users` | `NO ACTION` | Ídem |
| `group_expense_shares.expense_id`, `debts.expense_id` → `group_expenses` | `NO ACTION` | Los gastos no se borran en 017 (FR-026) |
| `debts.share_id` → `group_expense_shares` | `NO ACTION` | Ídem |
| `group_expense_shares.simulated_profile_id`, `debts.debtor_simulated_profile_id` → `simulated_profiles` | **`SET NULL`** | 010 borra la fila del perfil al quitarlo (`removeSimulatedProfile`). El nombre copiado conserva la historia. US2 garantiza que solo se borra con sus deudas saldadas |

## Cambios de esquema

`V13__create_group_expenses_and_debts.sql`: tres `CREATE TABLE` y seis `CREATE INDEX`. **Solo `CREATE TABLE` y `CREATE INDEX`**: ningún `ALTER` sobre tablas existentes, ningún `INSERT`, `UPDATE` ni `DELETE`. Próxima migración libre después de 017: `V14` (reservada para 018).

### Boceto del DDL

Es la referencia para la tarea de la migración (tasks.md T006). Los nombres de restricciones e índices son los que verifican `GroupExpenseMigrationIT` y `ddl-auto=validate`.

```sql
-- V13__create_group_expenses_and_debts.sql
-- Data model: specs/017-debt-states/data-model.md (fuente de verdad, ddl-auto=validate)
-- Solo CREATE TABLE / CREATE INDEX (regla del equipo): sin UPDATE ni DELETE.

CREATE TABLE group_expenses (
    id                 BIGSERIAL PRIMARY KEY,
    group_id           BIGINT        NOT NULL REFERENCES groups (id),
    created_by_user_id BIGINT        NOT NULL REFERENCES users (id),
    payer_user_id      BIGINT        NOT NULL REFERENCES users (id),
    amount             NUMERIC(11,2) NOT NULL,
    currency           VARCHAR(3)    NOT NULL,
    expense_date       DATE          NOT NULL,
    description        VARCHAR(200)  NULL,
    idempotency_key    UUID          NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_group_expenses_amount      CHECK (amount >= 0.01),
    CONSTRAINT ck_group_expenses_currency    CHECK (currency IN ('ARS', 'USD', 'EUR')),
    CONSTRAINT ck_group_expenses_description CHECK (description IS NULL OR char_length(description) <= 200),
    CONSTRAINT uq_group_expenses_creator_idempotency_key UNIQUE (created_by_user_id, idempotency_key)
);

CREATE TABLE group_expense_shares (
    id                   BIGSERIAL PRIMARY KEY,
    expense_id           BIGINT        NOT NULL REFERENCES group_expenses (id),
    position             SMALLINT      NOT NULL,
    participant_type     VARCHAR(10)   NOT NULL,
    user_id              BIGINT        NULL REFERENCES users (id),
    simulated_profile_id BIGINT        NULL REFERENCES simulated_profiles (id) ON DELETE SET NULL,
    participant_name     VARCHAR(255)  NOT NULL,
    amount               NUMERIC(11,2) NOT NULL,
    CONSTRAINT ck_group_expense_shares_position CHECK (position >= 0),
    CONSTRAINT ck_group_expense_shares_type     CHECK (participant_type IN ('REGISTERED', 'SIMULATED')),
    CONSTRAINT ck_group_expense_shares_participant CHECK (
        (participant_type = 'REGISTERED' AND user_id IS NOT NULL AND simulated_profile_id IS NULL)
        OR (participant_type = 'SIMULATED' AND user_id IS NULL)),
    CONSTRAINT ck_group_expense_shares_amount   CHECK (amount >= 0.01),
    CONSTRAINT uq_group_expense_shares_position UNIQUE (expense_id, position),
    CONSTRAINT uq_group_expense_shares_user     UNIQUE (expense_id, user_id),
    CONSTRAINT uq_group_expense_shares_profile  UNIQUE (expense_id, simulated_profile_id)
);

CREATE TABLE debts (
    id                          BIGSERIAL PRIMARY KEY,
    group_id                    BIGINT        NOT NULL REFERENCES groups (id),
    expense_id                  BIGINT        NOT NULL REFERENCES group_expenses (id),
    share_id                    BIGINT        NOT NULL REFERENCES group_expense_shares (id),
    debtor_type                 VARCHAR(10)   NOT NULL,
    debtor_user_id              BIGINT        NULL REFERENCES users (id),
    debtor_simulated_profile_id BIGINT        NULL REFERENCES simulated_profiles (id) ON DELETE SET NULL,
    debtor_name                 VARCHAR(255)  NOT NULL,
    creditor_user_id            BIGINT        NOT NULL REFERENCES users (id),
    currency                    VARCHAR(3)    NOT NULL,
    amount                      NUMERIC(11,2) NOT NULL,
    paid_amount                 NUMERIC(11,2) NOT NULL DEFAULT 0,
    status                      VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_at                  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_debts_share       UNIQUE (share_id),
    CONSTRAINT ck_debts_debtor_type CHECK (debtor_type IN ('REGISTERED', 'SIMULATED')),
    CONSTRAINT ck_debts_debtor CHECK (
        (debtor_type = 'REGISTERED' AND debtor_user_id IS NOT NULL AND debtor_simulated_profile_id IS NULL)
        OR (debtor_type = 'SIMULATED' AND debtor_user_id IS NULL)),
    CONSTRAINT ck_debts_not_self    CHECK (debtor_user_id IS NULL OR debtor_user_id <> creditor_user_id),
    CONSTRAINT ck_debts_currency    CHECK (currency IN ('ARS', 'USD', 'EUR')),
    CONSTRAINT ck_debts_amount      CHECK (amount >= 0.01),
    CONSTRAINT ck_debts_paid_range  CHECK (paid_amount >= 0 AND paid_amount <= amount),
    CONSTRAINT ck_debts_status      CHECK (status IN ('PENDING', 'PARTIALLY_PAID', 'PAID')),
    CONSTRAINT ck_debts_status_matches_paid CHECK (
        (status = 'PENDING'           AND paid_amount = 0)
        OR (status = 'PARTIALLY_PAID' AND paid_amount > 0 AND paid_amount < amount)
        OR (status = 'PAID'           AND paid_amount = amount))
);

CREATE INDEX ix_group_expenses_group         ON group_expenses (group_id, created_at DESC);
CREATE INDEX ix_group_expense_shares_expense ON group_expense_shares (expense_id);
CREATE INDEX ix_debts_expense                ON debts (expense_id);
CREATE INDEX ix_debts_open_debtor_user       ON debts (group_id, debtor_user_id)              WHERE status <> 'PAID';
CREATE INDEX ix_debts_open_debtor_profile    ON debts (group_id, debtor_simulated_profile_id) WHERE status <> 'PAID';
CREATE INDEX ix_debts_open_creditor          ON debts (group_id, creditor_user_id)            WHERE status <> 'PAID';
```

### Verificación de la regla de migraciones

- `grep -nEi '\b(UPDATE|DELETE|INSERT|DROP|TRUNCATE)\b' V13__*.sql` solo puede encontrar `ON DELETE SET NULL` (cláusula de FK, no borra datos). Es la tarea T049.
- La migración no modifica `expenses`, `groups`, `group_members`, `simulated_profiles` ni `group_activity`.
