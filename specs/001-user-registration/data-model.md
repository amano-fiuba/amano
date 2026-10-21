# Data Model: Registro de usuario

**Feature**: `001-user-registration` | **Fecha**: 2026-10-18 | **Spec**: [spec.md](../spec.md)

Modelo físico provisto por Flyway en la migración `backend/src/main/resources/db/migration/V1__create_user_registration.sql`. El JPA está en modo `validate` (ddl-auto), por lo tanto el esquema aquí es la fuente de verdad.

## Diagrama de relaciones

```text
users 1 ──── 1 user_avatars   (cuando el usuario cargó avatar)
      │
      └── 0..3 user_currencies (subconjunto de {ARS, USD, EUR}; 0 filas ⇒ ARS por defecto)
```

## Entidades

### User (tabla `users`)

Representa la cuenta registrada y su perfil (entidades **Cuenta de Usuario** y **Perfil** de la spec).

| Columna | Tipo | Regla | Fuente |
|---|---|---|---|
| `id` | BIGINT identity | PK | Sistema |
| `first_name` | VARCHAR(50) | NOT NULL, longitud 2–50 tras trim (FR-002) | Formulario |
| `last_name` | VARCHAR(50) | NULL, si se informa longitud 2–50 tras trim (FR-003, Q4) | Formulario opcional |
| `email` | VARCHAR(255) | NOT NULL, UNIQUE (índice), guardado trim+lowercase (FR-004/FR-005, SC-004) | Formulario |
| `password_hash` | VARCHAR(255) | NOT NULL, hash BCrypt; nunca expuesto (FR-006/FR-007/FR-008) | Formulario |
| `cbu` | VARCHAR(22) | NULL, exactamente 22 dígitos luego de normalizar espacios/guiones (FR-009, Q3) | Formulario opcional |
| `has_avatar` | — | **No es una columna**: se elimina del esquema físico; el dato se expone como atributo computado `hasCustomAvatar` desde el `UserMapper` (ver Reglas derivadas) | — |
| `terms_accepted_at` | TIMESTAMPTZ | NOT NULL, fecha de aceptación legal (FR-012) | Sistema al aceptar |
| `created_at` | TIMESTAMPTZ | NOT NULL, default `now()` | Sistema |
| `updated_at` | TIMESTAMPTZ | NOT NULL, default `now()` | Sistema |

**CHECKs de BD (defensa en profundidad, la validación primaria es Bean Validation):**

```sql
CHECK (char_length(first_name) BETWEEN 2 AND 50)
CHECK (last_name IS NULL OR char_length(last_name) BETWEEN 2 AND 50)
CHECK (cbu IS NULL OR cbu ~ '^[0-9]{22}$')
CREATE UNIQUE INDEX uq_users_email ON users (email)  -- sobre el valor ya normalizado
```

**Reglas de normalización aplicadas antes de persistir** (capa de servicio, `RegistrationService`):

- `email = email.trim().toLowerCase()`.
- `first_name`, `last_name`: `.trim()` y ese valor es el que se guarda.
- `cbu`: se eliminan espacios y guiones separadores; se valida el resultado (22 dígitos).

### UserAvatar (tabla `user_avatars`)

Uno a uno con `users`, asociación `@OneToOne` con `FetchType.LAZY` desde `User`.

| Columna | Tipo | Regla |
|---|---|---|
| `user_id` | BIGINT | PK y FK → `users(id)` `ON DELETE CASCADE` |
| `content_type` | VARCHAR(50) | NOT NULL, `'image/jpeg'` o `'image/png'` (frío: verificación de MIME real + magic bytes en servicio) |
| `data` | BYTEA | NOT NULL, `octet_length(data) <= 2097152` (2 MB, FR-010) |
| `created_at` | TIMESTAMPTZ | NOT NULL default `now()` |

- Solo existe fila si el usuario cargó un archivo. Si no existe, el frontend resuelve `/avatar-default.png` (recurso estático del propio frontend; no hay fila por defecto en BD — D5).
- La URL pública del binario es `GET /api/users/{id}/avatar` (ver `contracts/users-api.md`).

### UserCurrency (tabla `user_currencies`)

Colección de monedas preferidas, modelada como `@ElementCollection` (D7). Sin default persistido: 0 filas ⇒ ARS por Async como valor de lectura (FR-011, Q1).

| Columna | Tipo | Regla |
|---|---|---|
| `user_id` | BIGINT | PK compuesta y FK → `users(id)` `ON DELETE CASCADE` |
| `currency_code` | VARCHAR(3) | PK compuesta, `CHECK (currency_code IN ('ARS','USD','EUR'))` |

## Reglas derivadas (no columnas)

- **Atributo computado `hasCustomAvatar`**: se calcula exclusivamente en `backend/src/main/java/com/amano/backend/user/UserMapper.java` a partir de la existencia o ausencia de fila en `user_avatars` (`true` cuando hay fila). No se persiste ninguna columna derivada ni redundante en la base de datos; los contratos (contracts/auth-api.md, contracts/users-api.md) lo exponen en el JSON del perfil.

- **Unicidad de correo**: identidad de cuenta = email normalizado; duplicado ⇒ `409` con mensaje "El correo electrónico ya se encuentra registrado" (índice UNIQUE como última línea ante carrera concurrente).
- **Credencial**: `password_hash` solo se escribe con `BCryptPasswordEncoder`; nunca aparece en DTOs de respuesta, logs ni `toString`.
- **Aceptación legal**: se registra `terms_accepted_at`; `false` en el formulario impide crear la cuenta (FR-012).
- **Visibilidad de datos**: `cbu` es visible únicamente para el dueño de la sesión (`GET /api/users/me`); su exposición a terceros queda deferida a la funcionalidad de gestión de grupos (spec, fuera de alcance).

## Ciclo de vida / estados

```text
[No existe] --registro exitoso (POST /api/auth/register)--> [Cuenta activa con sesión iniciada]
[Cuenta activa] --> (sin cambios en este feature; las modificaciones de perfil/desactivación,
                      reclamos de invitados, 2FA y recuperación de contraseña están fuera de alcance)
```

Si el registro falla (validación, 409, 5xx, red), **no persiste nada**: la transacción de
`RegistrationService` es atómica (una o dos filas: `users` + `user_avatars` según archivo, +
`user_currencies`), y el envío multipart del frontend conservando datos no crea estados intermedios.

## Volumen y escala

Proyecto universitario sin metas de volumen especificadas; supuestos razonables documentados en la
espec: miles de usuarios y avatares ≤ 2 MB en BYTEA son completamente viables en PostgreSQL 18.
La carga LAZY evita que el perfil traiga el binario innecesariamente (D5).
