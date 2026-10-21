# Data Model: Registro de gasto personal (007)

**Feature**: `007-register-expense` | **Plan**: [plan.md](./plan.md) | **Spec**: [spec.md](./spec.md)

Fuente de verdad del esquema. La migración `V4__create_expenses.sql` y la entidad JPA deben
coincidir con este documento (`spring.jpa.hibernate.ddl-auto=validate`).

## Entidad: `Expense` (tabla `expenses`)

Gasto registrado por un usuario. El gasto es **estrictamente personal**: pertenece a un único
usuario creador y no tiene participantes, divisiones, deudas, amigos ni grupos (FR-006).

| Columna | Tipo PostgreSQL | Nulo | Restricción / origen |
|---|---|---|---|
| `id` | `BIGINT GENERATED ALWAYS AS IDENTITY` | No | PK |
| `user_id` | `BIGINT` | No | FK → `users(id)` `ON DELETE CASCADE` (FR-003/FR-005) |
| `amount` | `NUMERIC(11,2)` | No | `CHECK (amount >= 0.01)`; el máximo `999999999.99` lo fija la precisión (FR-014/FR-023) |
| `currency` | `VARCHAR(3)` | No | `CHECK (currency IN ('ARS','USD','EUR'))` (FR-011) |
| `expense_date` | `DATE` | No | Fecha sin hora (FR-009/FR-016) |
| `category` | `VARCHAR(20)` | No | `CHECK` con los códigos de `ExpenseCategory` (FR-011) |
| `expense_type` | `VARCHAR(20)` | No | `CHECK (expense_type IN ('RECURRING','ONE_TIME'))` (FR-010) |
| `payment_method` | `VARCHAR(20)` | No | `CHECK` con los códigos de `PaymentMethod` (FR-011) |
| `description` | `VARCHAR(200)` | Sí | `CHECK (description IS NULL OR char_length(description) <= 200)` (FR-012) |
| `idempotency_key` | `UUID` | No | Clave de la operación de guardado (FR-027) |
| `created_at` | `TIMESTAMPTZ` | No | `DEFAULT now()` (metadato, no forma parte de los requisitos) |

**Índice/constraint único**:

```sql
CONSTRAINT uq_expenses_user_idempotency_key UNIQUE (user_id, idempotency_key)
```

Cubre la búsqueda de replay idempotente `(user_id, idempotency_key)` y los accesos por
`user_id` (prefijo). No se agregan otros índices (no hay listado en esta iteración).

### Forma de la migración `V4__create_expenses.sql`

```sql
-- V4__create_expenses.sql
-- Data model: specs/007-register-expense/data-model.md (source of truth, ddl-auto=validate)
-- Covers FR-003, FR-006, FR-009, FR-010..FR-012, FR-014, FR-023, FR-025, FR-027.
-- Feature 007-register-expense.
-- Sin DELETE (regla del equipo): solo CREATE TABLE.

CREATE TABLE expenses (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    amount          NUMERIC(11,2) NOT NULL,
    currency        VARCHAR(3) NOT NULL,
    expense_date    DATE NOT NULL,
    category        VARCHAR(20) NOT NULL,
    expense_type    VARCHAR(20) NOT NULL,
    payment_method  VARCHAR(20) NOT NULL,
    description     VARCHAR(200) NULL,
    idempotency_key UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_expenses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_expenses_amount CHECK (amount >= 0.01),
    CONSTRAINT ck_expenses_currency CHECK (currency IN ('ARS', 'USD', 'EUR')),
    CONSTRAINT ck_expenses_category CHECK (category IN
        ('FOOD','TRANSPORT','HOUSING','SERVICES','HEALTH','EDUCATION','LEISURE','SHOPPING','PETS','OTHER')),
    CONSTRAINT ck_expenses_type CHECK (expense_type IN ('RECURRING', 'ONE_TIME')),
    CONSTRAINT ck_expenses_payment_method CHECK (payment_method IN
        ('CASH','DEBIT_CARD','CREDIT_CARD','TRANSFER','VIRTUAL_WALLET','OTHER')),
    CONSTRAINT ck_expenses_description CHECK (description IS NULL OR char_length(description) <= 200),
    CONSTRAINT uq_expenses_user_idempotency_key UNIQUE (user_id, idempotency_key)
);
```

> Nota: los valores exactos de los `CHECK` de catálogo deben coincidir con los enums de D6
> (research.md). Si el equipo prefiere etiquetas estables en español en lugar de códigos en
> inglés, la migración y los enums deben cambiar juntos; la decisión es persistir códigos.

## Mapeo JPA

- `Expense` con `@Entity @Table(name = "expenses")`.
- `id` con `@GeneratedValue(strategy = IDENTITY)`.
- `user_id`: se modela como `Long userId` (columna) o `@ManyToOne(LAZY)`; se recomienda **solo
  la columna** `Long userId` para no cargar `User` completo, siguiendo la simplicidad de
  `UserAvatar` (PK compartida sin asociación de lectura).
- `amount`: `BigDecimal` con `@Column(precision = 11, scale = 2)`.
- `currency`: enum `Currency` de `com.amano.backend.user` con `CurrencyConversion`.
- `category`, `type`, `paymentMethod`: enums propios con `@Enumerated(EnumType.STRING)`.
- `expenseDate`: `LocalDate`.
- `idempotencyKey`: `UUID`.
- `description`: `String` nulable.
- `createdAt`: `OffsetDateTime` con `@CreationTimestamp`.

## Catálogos cerrados

Códigos estables persistidos y expuestos por la API; las etiquetas en español viven solo en el
frontend (`expenseCatalog.js`).

| Catálogo | Códigos (persistidos) | Etiquetas visibles (frontend) |
|---|---|---|
| Moneda | `ARS`, `USD`, `EUR` | Peso argentino, Dólar estadounidense, Euro |
| Categoría | `FOOD`, `TRANSPORT`, `HOUSING`, `SERVICES`, `HEALTH`, `EDUCATION`, `LEISURE`, `SHOPPING`, `PETS`, `OTHER` | Alimentos, Transporte, Vivienda, Servicios, Salud, Educación, Ocio, Compras, Mascotas, Otros |
| Tipo | `RECURRING`, `ONE_TIME` | Recurrente, Único |
| Medio de pago | `CASH`, `DEBIT_CARD`, `CREDIT_CARD`, `TRANSFER`, `VIRTUAL_WALLET`, `OTHER` | Efectivo, Tarjeta de débito, Tarjeta de crédito, Transferencia, Billetera virtual, Otro |

## Reglas de validación y normalización (backend)

Orden: validar todo en un solo pase → normalizar → persistir. Cualquier error ⇒
`FieldValidationException` (400, `properties.fields`) y **nada persistido** (FR-024).

| Campo | Normalización | Validación | FR |
|---|---|---|---|
| `amount` (string canónico) | `new BigDecimal(texto).setScale(2)`: completa centavos (`10.1` → `10.10`), nunca redondea | numérico; `scale() <= 2`; `0.01 <= x <= 999999999.99`; estrictamente > 0 | FR-013, FR-014, FR-022, FR-023 |
| `currency` | `valueOf` sobre `Currency` | ∈ {ARS, USD, EUR} | FR-011 |
| `date` (ISO `yyyy-MM-dd`) | `LocalDate.parse` | formato válido; existe; no futura según `deviceTimeZone`; tolerancia de un día si falta/es inválida | FR-009, FR-016, FR-022 |
| `category` | `valueOf` sobre `ExpenseCategory` | ∈ catálogo | FR-011, FR-022 |
| `type` | `valueOf` sobre `ExpenseType` | ∈ {RECURRING, ONE_TIME} | FR-010, FR-022 |
| `paymentMethod` | `valueOf` sobre `PaymentMethod` | ∈ catálogo | FR-011, FR-022 |
| `description` | recorte Unicode + CRLF/CR→LF; vacío ⇒ `null` | code points ≤ 200 | FR-012, FR-017, FR-022 |
| `deviceTimeZone` | — | `ZoneId.of` válido; si falta o es inválido ⇒ tolerancia (no error) | FR-022 |
| propiedades reservadas | `@JsonAnySetter` | rechazo de `userId`/`creatorId`/`creator`/`user`/`participants`/`participantIds`/`splits`/`divisions`/`shares`/`debts`/`friends`/`groups` | FR-005, FR-006 |
| `Idempotency-Key` (cabecera) | — | presente y UUID válido (400 si no) | FR-027 |

## Reglas derivadas

- **Creador**: siempre `user_id` = principal de la sesión; nunca provisto por el cliente
  (FR-003/FR-004/FR-005).
- **Idempotencia**: existe a lo sumo un gasto por `(user_id, idempotency_key)`. Si la clave ya
  existe **y el payload normalizado coincide**, se devuelve ese gasto (replay `200`) sin
  insertar (FR-027/SC-003). Si la clave existe pero el payload **difiere** (comparando
  `amount`, `currency`, `expense_date`, `category`, `expense_type`, `payment_method` y
  `description` de la fila persistida contra el payload entrante normalizado), se responde
  `422` y no se inserta (FR-027, ver research.md §D1). No se agrega columna de hash.
- **Dos gastos idénticos deliberados**: al renovarse la clave tras cada éxito, producen dos
  filas válidas (Assumption del spec).
- **Precisión**: `amount` se almacena y se lee como `NUMERIC(11,2)`; la representación de API
  conserva la escala (`"10.10"`), sin punto flotante (SC-004).

## Estados

La entidad no tiene máquina de estados: un gasto se crea una vez y no se modifica ni elimina
en esta iteración (Out of Scope).

## Volumen y escala

- Una fila por gasto registrado; decenas por mes por usuario a escala personal.
- Sin operaciones de listado en esta iteración. El índice único `(user_id, idempotency_key)`
  sirve las lecturas de replay y filtra por usuario si en el futuro se agrega un listado.
- `ON DELETE CASCADE` garantiza que al eliminar una cuenta (limpieza de 003) sus gastos no
  queden huérfanos; no hay borrado de gastos individuales.

## Sin tablas relacionadas

No se crean tablas ni columnas de participantes, divisiones, deudas, amigos ni grupos
(FR-006). Tampoco se modifica ninguna tabla existente: el modelo es puramente aditivo.
