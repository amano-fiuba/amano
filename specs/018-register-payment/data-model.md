# Data Model: Registrar un pago (018)

**Fecha**: 2026-11-05 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

**Alcance de datos**: una migración nueva, `V14__create_payments.sql`, con una tabla (`payments`) y sus índices. **Solo `CREATE TABLE` y `CREATE INDEX`**: no modifica tablas existentes ni filas (regla del equipo; constitución, "Bases de datos y migraciones"). La tabla `debts` de 017 **no cambia de esquema**: 017 ya dejó `paid_amount`, `status`, `updated_at` y el CHECK que ata el estado al monto pagado (017 SC-006). El DDL completo está en §Cambios de esquema.

## Diagrama

```text
groups 1 ──< debts (017) 1 ──< payments
               │                 │  │  │
               │                 │  │  └─ registered_by_user_id ─▶ users   (quien cargó el pago)
               │                 │  └──── receiver_user_id ──────▶ users   (= debts.creditor_user_id)
               │                 └─────── payer_user_id ─────────▶ users   (= debts.debtor_user_id, REGISTERED)
               │                          payer_simulated_profile_id ─▶ simulated_profiles (SET NULL, SIMULATED)
               └── group_id ◀──────────── payments.group_id (copia del grupo de la deuda)
```

- Una deuda tiene **0..n pagos**. La suma de sus pagos es siempre igual a `debts.paid_amount` (regla 1).
- Un pago pertenece a **exactamente una deuda**. No hay pagos "sueltos" ni pagos que repartan un monto entre varias deudas (fuera de alcance, spec).
- Quien paga y quien recibe **se copian de la deuda**: el cliente no los elige (plan.md D3).

## Entidades

### Payment (tabla `payments`, nueva)

| Columna | Tipo | Restricciones | Notas |
|---------|------|---------------|-------|
| `id` | `BIGSERIAL` | PK | |
| `group_id` | `BIGINT` | NOT NULL, FK `groups(id)` | Copia de `debts.group_id`, para el historial y un futuro listado sin join |
| `debt_id` | `BIGINT` | NOT NULL, FK `debts(id)` | La deuda que salda (total o parcialmente) |
| `payer_type` | `VARCHAR(10)` | NOT NULL, `CHECK IN ('REGISTERED','SIMULATED')` | Copia de `debts.debtor_type`. Mismos valores que 014 y 017 |
| `payer_user_id` | `BIGINT` | NULL, FK `users(id)` | Solo `REGISTERED`. Copia de `debts.debtor_user_id` |
| `payer_simulated_profile_id` | `BIGINT` | NULL, FK `simulated_profiles(id)` `ON DELETE SET NULL` | Solo `SIMULATED`. Queda nulo si el perfil se quita después de saldar |
| `payer_name` | `VARCHAR(255)` | NOT NULL | Copia de `debts.debtor_name` al momento del pago |
| `receiver_user_id` | `BIGINT` | NOT NULL, FK `users(id)` | Copia de `debts.creditor_user_id`. Un perfil simulado nunca cobra (017) |
| `receiver_name` | `VARCHAR(255)` | NOT NULL | Nombre visible del acreedor al momento del pago (`ActivityEventService.displayName`) |
| `registered_by_user_id` | `BIGINT` | NOT NULL, FK `users(id)` | Usuario de la sesión: el deudor, el acreedor o un ADMIN (FR-004). Es el actor de `PAYMENT_MADE` |
| `amount` | `NUMERIC(11,2)` | NOT NULL, `CHECK (amount >= 0.01)` | Como `debts.amount`. Nunca supera el saldo al momento del pago (regla 2) |
| `currency` | `VARCHAR(3)` | NOT NULL, `CHECK IN ('ARS','USD','EUR')` | Copia de `debts.currency`. No viaja en el cuerpo (sin conversión) |
| `payment_date` | `DATE` | NOT NULL | Fecha en que se pagó. No posterior a hoy ni anterior a la fecha del gasto (FR-007) |
| `idempotency_key` | `UUID` | NOT NULL | Cabecera `Idempotency-Key` (patrón de 007/017) |
| `created_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT `now()` | Momento de registro (no editable) |

- `CHECK ck_payments_payer`: `(payer_type = 'REGISTERED' AND payer_user_id IS NOT NULL AND payer_simulated_profile_id IS NULL) OR (payer_type = 'SIMULATED' AND payer_user_id IS NULL)`. Es la misma forma que `ck_debts_debtor` de V13: un `SIMULATED` puede quedar con el perfil nulo por el `SET NULL`.
- `CHECK ck_payments_not_self`: `payer_user_id IS NULL OR payer_user_id <> receiver_user_id`. Espejo de `ck_debts_not_self`.
- `UNIQUE (registered_by_user_id, idempotency_key)` (`uq_payments_registered_by_idempotency_key`). Como en `group_expenses`, la clave es por quien registra.
- Índice `ix_payments_debt (debt_id, created_at)`: pagos de una deuda en orden (detalle futuro y verificación de la regla 1 en las pruebas).
- Índice `ix_payments_group (group_id, created_at DESC)`: para el futuro listado "Ver pagos del grupo" (S5) y para auditorías por grupo.

**Sin columna de estado.** En 018 un pago registrado siempre se aplica a la deuda en el momento (spec §Clarifications, `payments_require_validation`). Si una spec posterior agrega la validación de pagos, puede sumar `status` con un `ALTER TABLE payments ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED'`, que respeta la regla de migraciones (no reescribe filas a mano).

### Debt (tabla `debts`, de 017, sin cambios de esquema)

018 solo **escribe** tres columnas que 017 dejó preparadas, a través de `Debt.applyPayment` y el *dirty checking* de JPA/Hibernate (no hay SQL a mano):

| Columna | Antes del pago | Después del pago | Quién la cambia |
|---------|----------------|------------------|-----------------|
| `paid_amount` | `p` | `p + amount` | `Debt.applyPayment(amount)` |
| `status` | `PENDING` o `PARTIALLY_PAID` | `DebtStatus.of(amount, p + amount)` → `PARTIALLY_PAID` o `PAID` | `Debt.applyPayment(amount)` |
| `updated_at` | `t0` | `now()` | `@UpdateTimestamp` de Hibernate al hacer *flush* |

- El `UPDATE debts …` lo genera Hibernate al confirmar la transacción. **No es parte de la migración**: la regla "solo `CREATE`/`ALTER`/`INDEX`" aplica a los archivos de Flyway, no al acceso a datos en tiempo de ejecución.
- `ck_debts_paid_range` y `ck_debts_status_matches_paid` (V13) siguen siendo la red de seguridad: si por un error del servicio se intentara pagar de más o dejar un estado incoherente, la base rechaza el `UPDATE` y la transacción completa (pago + evento) se revierte.

## Transiciones de estado que dispara un pago

| Estado actual | Pago `x` (con saldo `s = amount − paid_amount`) | Estado nuevo | Resultado HTTP |
|---------------|------------------------------------------------|--------------|----------------|
| `PENDING` | `0 < x < s` | `PARTIALLY_PAID` | 201 |
| `PENDING` | `x = s` | `PAID` | 201 |
| `PARTIALLY_PAID` | `0 < x < s` | `PARTIALLY_PAID` | 201 |
| `PARTIALLY_PAID` | `x = s` | `PAID` | 201 |
| `PENDING` / `PARTIALLY_PAID` | `x > s` | sin cambios | 400 en `amount` |
| `PENDING` / `PARTIALLY_PAID` | `x ≤ 0` o más de dos decimales | sin cambios | 400 en `amount` (mensajes de 007) |
| `PAID` | cualquiera | sin cambios | 409 `DEBT_ALREADY_PAID` |

No hay transiciones hacia atrás (017 FR-012): 018 no anula, edita ni borra pagos.

## Concurrencia

Dos pagos simultáneos sobre la misma deuda (por ejemplo, el deudor y el acreedor registrando el mismo pago desde dos teléfonos) no pueden sumar más que el saldo:

1. El servicio lee la deuda con **bloqueo pesimista** (`@Lock(LockModeType.PESSIMISTIC_WRITE)` → `SELECT … FOR UPDATE`) mediante `DebtRepository.findByIdForUpdate(id)` (plan.md D5).
2. El segundo pedido espera a que termine la transacción del primero y vuelve a leer `paid_amount` y `status` ya actualizados. Si el saldo no alcanza, responde 400 en `amount`; si la deuda quedó `PAID`, 409.
3. Dos envíos con la **misma** `Idempotency-Key` quedan serializados por el mismo bloqueo: el segundo encuentra el pago del primero y devuelve 200. Si igual llegaran a chocar en `uq_payments_registered_by_idempotency_key`, el controlador captura `DataIntegrityViolationException` y relee el ganador (`resolveConcurrentRegistration`, patrón de 007/017).

No se agrega una columna `version` a `debts` (bloqueo optimista) porque requeriría tocar el esquema de 017 y reintentos en el cliente; el bloqueo pesimista alcanza para el volumen de un grupo (plan.md D5).

## Clases Java nuevas (paquete `com.amano.backend.payment`)

| Clase | Tipo | Responsabilidad |
|-------|------|-----------------|
| `Payment` | `@Entity` | Mapeo de `payments`. Fábrica `Payment.of(debt, amount, date, registeredBy, receiverName, key)` que copia pagador, receptor, grupo y moneda de la deuda |
| `PaymentRepository` | Spring Data | `findByRegisteredByUserIdAndIdempotencyKey`, `findByDebtIdOrderByCreatedAtAsc` |
| `PaymentService` | `@Service` | Orden de plan.md §Flujo del backend: deuda, membresía, permiso, validación, bloqueo, idempotencia, aplicación, historial |
| `PaymentController` | `@RestController` | `POST /api/payments` (plan.md §Contrato REST) |
| `RegisterPaymentRequest` | DTO | `debtId`, `amount`, `date`, `deviceTimeZone` |
| `PaymentResponse` | DTO (record) | Pago creado más la deuda actualizada (`GroupExpenseResponse.DebtView`) |
| `DebtNotFoundException` | excepción | 404 neutral "No existe la deuda solicitada o no sos miembro del grupo" |
| `DebtAlreadyPaidException` | excepción | 409 `DEBT_ALREADY_PAID` "La deuda ya está pagada" |

Clases modificadas:

| Clase | Cambio |
|-------|--------|
| `groupexpense/DebtRepository` | M: `findByIdForUpdate(Long id)` con `@Lock(PESSIMISTIC_WRITE)` |
| `groupexpense/GroupExpenseResponse` | M: `DebtView` suma `canRegisterPayment` (boolean, calculado para el usuario de la sesión) |
| `groupexpense/GroupExpenseService` | M: `get` calcula `canRegisterPayment` por deuda con `GroupPermissionChecker` |
| `groups/GroupPermissionChecker` | M: `canRegisterPayment(callerRole, callerUserId, debtorUserId, creditorUserId)` |
| `groups/PaymentActivityDetails` | M: suma `expenseId` y `debtId` opcionales (al final del record) |
| `groups/ActivityEventService` | M: `recordPaymentMade` agrega `expenseId` y `debtId` al mapa de detalles, solo si vienen |
| `common/ProblemDetailExceptionHandler` | M: `DebtNotFoundException` → 404 y `DebtAlreadyPaidException` → 409 con `code` |
| `config/SecurityConfig` | M: `/api/payments/**` exige cuenta verificada (plan.md §Seguridad) |

## Reglas de negocio

1. **Conservación**: para toda deuda, `Σ payments.amount WHERE debt_id = d.id` = `d.paid_amount`. Se cumple porque el pago y `applyPayment` se guardan en la misma transacción. Lo verifica `PaymentServiceIT`.
2. **Tope**: `payment.amount ≤ outstanding` de la deuda **leída con bloqueo**. Nunca se paga de más.
3. **Copias**: `payer_*`, `receiver_user_id`, `currency` y `group_id` salen de la deuda; `payer_name` de `debts.debtor_name`; `receiver_name` del usuario acreedor en ese momento. No se actualizan después (patrón de preservación de 010/014).
4. **Atomicidad**: pago, actualización de la deuda y `PAYMENT_MADE` en una transacción (`@Transactional`; `recordPaymentMade` es `MANDATORY`). Si cualquiera falla, no queda nada.
5. **Inmutabilidad**: los pagos no se editan ni se borran. No hay `UPDATE` ni `DELETE` sobre `payments` en ningún flujo de 018.
6. **Efecto en las bajas**: cuando una deuda pasa a `PAID`, deja de contar para `LedgerDebtPort` (017 FR-017/FR-018, índices `ix_debts_open_*`). 018 no toca el puerto: el desbloqueo es consecuencia del estado.

## Claves foráneas y borrados

Convención de `V6`–`V13`: `BIGSERIAL` para las PK y `REFERENCES` sin `ON DELETE` (`NO ACTION`) salvo que se indique otra cosa.

| FK | `ON DELETE` | Motivo |
|----|-------------|--------|
| `payments.group_id` → `groups` | `NO ACTION` | La app no borra grupos |
| `payments.debt_id` → `debts` | `NO ACTION` | Las deudas no se borran (017 FR-026) |
| `payments.payer_user_id`, `receiver_user_id`, `registered_by_user_id` → `users` | `NO ACTION` | Los usuarios no se borran; un pago no puede quedar sin partes |
| `payments.payer_simulated_profile_id` → `simulated_profiles` | **`SET NULL`** | 010 borra la fila del perfil al quitarlo. Solo puede quitarse con sus deudas saldadas (017 US2), así que el pago ya existe y conserva `payer_name` |

## Cambios de esquema

`V14__create_payments.sql`: un `CREATE TABLE` y dos `CREATE INDEX`. **Ningún `ALTER` sobre tablas existentes, ningún `INSERT`, `UPDATE` ni `DELETE`.** Próxima migración libre después de 018: `V15`.

### Boceto del DDL

Es la referencia para la tarea de la migración (tasks.md T007). Los nombres de restricciones e índices son los que verifican `PaymentMigrationIT` y `ddl-auto=validate`.

```sql
-- V14__create_payments.sql
-- Data model: specs/018-register-payment/data-model.md (fuente de verdad, ddl-auto=validate)
-- Solo CREATE TABLE / CREATE INDEX (regla del equipo): sin modificar filas existentes.

CREATE TABLE payments (
    id                         BIGSERIAL PRIMARY KEY,
    group_id                   BIGINT        NOT NULL REFERENCES groups (id),
    debt_id                    BIGINT        NOT NULL REFERENCES debts (id),
    payer_type                 VARCHAR(10)   NOT NULL,
    payer_user_id              BIGINT        NULL REFERENCES users (id),
    payer_simulated_profile_id BIGINT        NULL REFERENCES simulated_profiles (id) ON DELETE SET NULL,
    payer_name                 VARCHAR(255)  NOT NULL,
    receiver_user_id           BIGINT        NOT NULL REFERENCES users (id),
    receiver_name              VARCHAR(255)  NOT NULL,
    registered_by_user_id      BIGINT        NOT NULL REFERENCES users (id),
    amount                     NUMERIC(11,2) NOT NULL,
    currency                   VARCHAR(3)    NOT NULL,
    payment_date               DATE          NOT NULL,
    idempotency_key            UUID          NOT NULL,
    created_at                 TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_payments_payer_type CHECK (payer_type IN ('REGISTERED', 'SIMULATED')),
    CONSTRAINT ck_payments_payer CHECK (
        (payer_type = 'REGISTERED' AND payer_user_id IS NOT NULL AND payer_simulated_profile_id IS NULL)
        OR (payer_type = 'SIMULATED' AND payer_user_id IS NULL)),
    CONSTRAINT ck_payments_not_self CHECK (payer_user_id IS NULL OR payer_user_id <> receiver_user_id),
    CONSTRAINT ck_payments_amount   CHECK (amount >= 0.01),
    CONSTRAINT ck_payments_currency CHECK (currency IN ('ARS', 'USD', 'EUR')),
    CONSTRAINT uq_payments_registered_by_idempotency_key UNIQUE (registered_by_user_id, idempotency_key)
);

CREATE INDEX ix_payments_debt  ON payments (debt_id, created_at);
CREATE INDEX ix_payments_group ON payments (group_id, created_at DESC);
```

### Verificación de la regla de migraciones

- `grep -nEi '\b(UPDATE|DELETE|INSERT|DROP|TRUNCATE|ALTER)\b' V14__*.sql` solo puede encontrar `ON DELETE SET NULL` (cláusula de FK, no borra datos). Es la tarea T060.
- La migración no modifica `debts`, `group_expenses`, `groups`, `group_members`, `simulated_profiles` ni `group_activity`.
- `PAYMENT_MADE` ya está admitido por el CHECK de `group_activity.event_type` (V10, 014): no hace falta tocar esa tabla.
