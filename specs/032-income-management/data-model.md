# Modelo de datos: ingresos personales (032)

## Decisión

Tabla propia `incomes` (migración Flyway `V38__create_incomes.sql`, siguiente a `V37`), en lugar de un discriminador en `expenses`: `expenses` está acoplada a búsqueda 031, recurrentes (009) y grupos; mezclar signos arriesga FR-014 y SC-006. Ver `research.md` R1.

## Tabla `incomes`

| Columna | Tipo | Notas |
|---------|------|-------|
| `id` | bigint identity PK | |
| `user_id` | bigint NOT NULL, FK → `users(id)` `ON DELETE CASCADE` | Dueño; sale de la sesión |
| `amount` | numeric(11,2) NOT NULL | CHECK `amount >= 0.01`; tope 999.999.999,99 |
| `currency` | varchar(3) NOT NULL | CHECK en las 32 monedas del enum `Currency` (igual que `ck_expenses_currency` de V29) |
| `income_date` | date NOT NULL | Fecha del ingreso (año/mes/rango/proyectado) |
| `category` | varchar(20) NOT NULL | CHECK `SALARY, FREELANCE, SALE, REFUND, OTHER` |
| `payment_method` | varchar(20) NOT NULL | Medio de cobro. CHECK `CASH, TRANSFER, VIRTUAL_WALLET, OTHER` |
| `description` | varchar(200) NULL | CHECK `char_length <= 200` |
| `idempotency_key` | uuid NOT NULL | Embebida, como en `expenses` |
| `created_at` | timestamptz NOT NULL default `now()` | Registro |

Restricciones: `uq_incomes_user_idempotency_key UNIQUE (user_id, idempotency_key)` (respaldo de concurrencia). Índice: `ix_incomes_user_date (user_id, income_date DESC, id DESC)` para búsqueda, años y balance.

No hay `updated_at`: los registros son inmutables; solo se eliminan en cascada con la cuenta. No existe tabla de claves aparte (corrige la versión anterior de este documento).

## SQL

El script completo está en `plan.md` → "Esquema de base de datos y migración".

## Entidad JPA `Income`

Campos: `id`, `userId`, `amount` (`BigDecimal`), `currency` (`Currency`), `incomeDate` (`LocalDate`), `category` (`IncomeCategory`), `paymentMethod` (`IncomePaymentMethod`), `description`, `idempotencyKey` (`UUID`), `createdAt`. Solo getters públicos tras la creación; con `ddl-auto=validate`.

## Derivados (no persistidos)

- `projected`: `income_date > hoy`.
- Balance del período (moneda principal, cotización de la fecha de cada movimiento): `income` = ingresos con fecha ≤ hoy; `projectedIncome` = ingresos con fecha > hoy; `expenses` = gastos personales ya registrados; `net = income − expenses`.

## Sin cambios

`expenses`, tablas de grupos, deudas, pagos y recordatorios no se modifican.
