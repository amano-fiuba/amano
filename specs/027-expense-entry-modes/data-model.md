# Data Model: Formas de registrar un gasto (027)

**Fecha**: 2026-11-08 | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md)

Los cambios son **aditivos**: ninguna columna existente cambia de tipo y no se reescriben datos. Las migraciones ya integradas no se editan.

## Currency (enum, modificado)

`backend/src/main/java/com/amano/backend/user/Currency.java`

| Cambio | Detalle |
|---|---|
| Constantes | De `ARS, USD, EUR` a 32 monedas: `ARS, AUD, AWG, BOB, BRL, CAD, CHF, CLP, CNY, COP, CZK, DKK, EUR, GBP, HKD, ILS, INR, JPY, MXN, NIO, NOK, NZD, PEN, PYG, RSD, RUB, SEK, SGD, TRY, USD, UYU, ZAR` (R1) |
| `bcraCode()` | Código que usa el BCRA. Igual al nombre, salvo `MXN` → `MXP` |
| `fromBcraCode(String)` | Inverso; `Optional.empty()` para códigos no soportados |
| `GROUP_PRIMARY` | `EnumSet.of(ARS, USD, EUR)`: monedas válidas como moneda principal de grupo (R3) |

Persistencia: sin cambios (`@Enumerated(STRING)` / `CurrencyConversion`, `VARCHAR(3)`).

## group_expenses (tabla existente, modificada)

| Columna | Tipo | Antes | Ahora | Regla |
|---|---|---|---|---|
| `group_id` | `BIGINT` | `NOT NULL` | nullable | Exactamente uno de `group_id` / `friend_user_id` (CHECK `ck_group_expenses_context`) |
| `friend_user_id` | `BIGINT` FK `users(id)` | — | nueva, nullable | El otro participante de un gasto con amigo |
| `category` | `VARCHAR(20)` | — | nueva, nullable | `CHECK` con los valores de `ExpenseCategory`. Obligatoria en altas nuevas (servicio) |
| `expense_type` | `VARCHAR(10)` | — | nueva, nullable | `CHECK IN ('RECURRING','ONE_TIME')`. Obligatoria en altas nuevas |
| `payment_method` | `VARCHAR(20)` | — | nueva, nullable | `CHECK` con los valores de `PaymentMethod`. Obligatorio en altas nuevas |
| `currency` | `VARCHAR(3)` | CHECK 3 monedas | CHECK 32 monedas | |

Índice nuevo: `(friend_user_id, created_by_user_id) WHERE group_id IS NULL` para la consulta de deuda directa.

Entidad `GroupExpense`: `groupId` deja de ser `nullable = false`; campos nuevos `friendUserId`, `category` (`ExpenseCategory`), `expenseType` (`ExpenseType`), `paymentMethod` (`PaymentMethod`). Método `isDirect()` = `groupId == null`.

Filas existentes: `category`, `expense_type`, `payment_method` quedan `NULL`; las respuestas los devuelven `null` y el frontend muestra "—".

## debts (tabla existente, modificada)

| Columna | Antes | Ahora |
|---|---|---|
| `group_id` | `NOT NULL` | nullable (`NULL` = deuda directa de un gasto con amigo) |
| `currency` | CHECK 3 monedas | CHECK 32 monedas |

Regla de consistencia (servicio, cubierta por prueba): `debts.group_id` es igual al `group_id` de su gasto. Una deuda directa siempre tiene `debtor_type = REGISTERED`, `debtor_user_id` y `creditor_user_id` iguales al par (creador, amigo) en algún orden.

**Invariante de consultas** (R5): toda consulta de deudas o gastos de grupo MUST filtrar por `group_id`. Las existentes ya lo hacen (`findByGroupId`, `findOpenForUserInGroup`, `findPendingDueBefore`, `markPendingOverdue`, `existsOpenForUser`, `existsOpenForSimulatedProfile`, `findByIdAndGroupId`).

## payments, expenses, user_currencies (modificadas)

Solo cambia el `CHECK` de moneda a las 32 monedas. `groups.primary_currency` **no cambia**.

## Deuda directa (concepto, sin tabla propia)

Existe deuda directa entre A y B si hay una fila en `debts` con `group_id IS NULL`, `status NOT IN ('PAID','VERIFIED')` y `{debtor_user_id, creditor_user_id} = {A, B}`. Consulta nueva en `DebtRepository`:

```text
existsOpenDirectBetween(userA, userB): boolean
```

Implementación de `DirectDebtPort` (`groupexpense.DebtDirectDebtAdapter`) que reemplaza a `NoOpDirectDebtPort` como bean.

Estados: los mismos de 022, pero en esta spec una deuda directa solo nace en `PENDING`; ninguna operación existente la puede mover (pagos, validación y vencimiento están acotados a grupo).

## Catálogo de monedas (lectura, sin persistencia)

`CurrencyCatalogEntry { code, bcraName }`: resultado de cruzar `GET Maestros/Divisas` del BCRA con `Currency` (se deduplica NIO y se mapea MXP→MXN). Caché en memoria de 24 h en `CachingCurrencyCatalogProvider`; un fallo del BCRA sin caché → `CurrencyCatalogUnavailableException` (503).

## Migración

Una migración por historia (una migración aplicada no se edita): `V29__expense_entry_modes.sql` (US2: punto 1), `V30__group_expense_classification.sql` (US3: columnas de clasificación del punto 2) y `V31__friend_expenses.sql` (US4: resto del punto 2 y punto 3). Verificar que los números sigan libres al integrar con las ramas de 025/026:

1. `DROP CONSTRAINT` + `ADD CONSTRAINT` de moneda en `expenses`, `group_expenses`, `debts`, `payments`, `user_currencies`.
2. `group_expenses`: `ALTER COLUMN group_id DROP NOT NULL`, columnas `friend_user_id`, `category`, `expense_type`, `payment_method` con sus `CHECK` y FK; `CHECK ck_group_expenses_context ((group_id IS NULL) <> (friend_user_id IS NULL))`; índice parcial.
3. `debts`: `ALTER COLUMN group_id DROP NOT NULL`.

Verificado (T002): `group_expenses.group_id` y `debts.group_id` tienen FK inline a `groups(id)` (nombre automático de PostgreSQL); `DROP NOT NULL` no las toca y un `NULL` no las viola. Nombres de los `CHECK` de moneda: `ck_expenses_currency` (V4), `ck_group_expenses_currency` y `ck_debts_currency` (V13), `ck_payments_currency` (V14), `ck_user_currencies_code` (V1). `ck_groups_primary_currency` (V17) no se toca. La rama de la 025 usa `V26`, `V27` y `V28` (configuración de recordatorios, tabla `reminders` y tipos de evento del historial), por eso la 027 usa `V29`, `V30` y `V31`.

## Frontend (estado de formulario)

| Formulario | Campos |
|---|---|
| Personal (`ExpenseForm`) | Sin cambios de campos: monto, moneda, fecha, categoría, tipo, frecuencia (si recurrente), medio de pago, descripción |
| Grupo / amigo (`SharedExpenseForm`) | Los de hoy (monto, moneda, fecha, descripción, pagador, método, participantes) + `category`, `type`, `paymentMethod`. Sin `frequency` |

Validaciones de los campos nuevos: las de `validation/expenseValidation.js` del 007 (select obligatorio).
