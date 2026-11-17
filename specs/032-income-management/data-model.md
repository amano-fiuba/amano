# Modelo de datos: ingresos personales (032)

## Decisión

Tabla propia `incomes` (nueva migración Flyway, siguiente a `V37`), en lugar de un discriminador `TYPE` en `expenses`. Motivos: `expenses` está acoplada a gastos de grupo, deudas, recurrentes y a la búsqueda de 031; mezclar signos obligaría a tocar todas esas consultas y arriesga el requisito de que los grupos no cambien (FR-014, SC-006).

## Tabla `incomes`

| Columna | Tipo | Notas |
|---------|------|-------|
| `id` | bigint PK | Generado |
| `user_id` | bigint FK → users, NOT NULL, `ON DELETE CASCADE` | Dueño; sale de la sesión |
| `amount` | numeric(19,2) NOT NULL | CHECK `amount > 0` |
| `currency` | varchar NOT NULL | Código del catálogo de monedas |
| `income_date` | date NOT NULL | Fecha del ingreso (año/mes/rango) |
| `category` | varchar NOT NULL | CHECK en `SALARY, FREELANCE, SALE, REFUND, OTHER` |
| `payment_method` | varchar NOT NULL | CHECK en `CASH, TRANSFER, VIRTUAL_WALLET, OTHER` (medio de cobro) |
| `description` | varchar(N) NULL | Mismo largo máximo que `expenses.description` |
| `created_at` | timestamptz NOT NULL | Registro |

Índices: `(user_id, income_date DESC, id DESC)` para búsqueda, años y balance.

## Tabla `income_idempotency_keys`

Misma forma y política que la usada por gastos: `(user_id, idempotency_key)` único, `income_id` FK (`ON DELETE CASCADE`) y huella (hash) del cuerpo para detectar `422`.

Los registros son inmutables: no hay columna de última edición ni operaciones de actualización o borrado desde la aplicación (solo se eliminan en cascada con la cuenta).

## Derivados (no persistidos)

- `projected`: `income_date > hoy`.
- Balance: suma de `incomes` con `income_date ≤ hoy` convertidas a la moneda principal, menos suma de gastos personales del período convertidos a la misma moneda.

## Sin cambios

`expenses`, tablas de grupos, deudas y pagos no se modifican.
