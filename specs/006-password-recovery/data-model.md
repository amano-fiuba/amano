# Data Model: Recuperación de contraseña (006)

**Fecha**: 2026-10-21 | **Spec**: [spec.md](./spec.md)

**Alcance de datos**: la feature crea **una tabla nueva** (`password_reset_tokens`, migración `V3__password_reset_tokens.sql`) y modifica un único dato existente: `users.password_hash`, que el canje reemplaza. No agrega columnas a tablas existentes.

## Entidades

### PasswordResetToken (nueva — tabla `password_reset_tokens`)

Credencial temporal de un solo uso que autoriza a cambiar la contraseña de una cuenta. Entidad JPA `com.amano.backend.passwordreset.PasswordResetToken`; las reglas de negocio viven en `PasswordResetService`, la entidad es solo mapeo.

| Columna | Tipo | Restricciones | Regla |
|---------|------|---------------|-------|
| `id` | `BIGINT` | PK, `GENERATED ALWAYS AS IDENTITY` | — |
| `user_id` | `BIGINT` | `NOT NULL`, FK `users(id)` `ON DELETE CASCADE` (`fk_password_reset_tokens_user`) | Dueño del token; si se elimina la cuenta, sus tokens caen en cascada |
| `token_hash` | `VARCHAR(64)` | `NOT NULL`, `UNIQUE` (`uq_password_reset_tokens_hash`), no actualizable | SHA-256 en hex del token en claro (FR-005); **nunca** el valor en claro |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Inicio de validez (`@CreationTimestamp`) |
| `expires_at` | `TIMESTAMPTZ` | `NOT NULL`, `CHECK (expires_at > created_at)` (`ck_password_reset_tokens_expires_at`) | `created_at + password-reset.token-expiration` (3600 s por defecto, FR-006) |
| `used_at` | `TIMESTAMPTZ` | `NULL` | `NULL` = vigente; con valor = invalidado por una solicitud más nueva (FR-007) |

**Índices**:

- `uq_password_reset_tokens_hash` (implícito por `UNIQUE`): búsqueda del canje por hash (FR-011).
- `ix_password_reset_tokens_user_id`: invalidación de los tokens vigentes de una cuenta al emitir uno nuevo (FR-007) y borrado por cuenta en el canje exitoso (FR-015).

**Valor en claro (no persistido)**: 32 bytes de `SecureRandom` codificados en Base64URL sin relleno, es decir 43 caracteres `[A-Za-z0-9_-]`. Solo existe en memoria durante la emisión y dentro del enlace del correo `${APP_BASE_URL}/reset-password?token=<valor>`.

### User (existente — 001/003)

| Campo | Uso en 006 |
|-------|------------|
| `email` | Búsqueda de la cuenta en la solicitud, contra el correo **normalizado** (trim + minúsculas, `Locale.ROOT`) — FR-003 |
| `emailVerifiedAt` | `null` = pendiente: no recibe enlace (FR-004). Con valor = verificada: recibe enlace |
| `firstName` | Saludo del correo ("Hola Ana,"); se omite si está vacío |
| `passwordHash` | **Reemplazado** en el canje exitoso por `PasswordEncoder.encode(nueva)` (BCrypt, bean de `SecurityConfig`) — FR-015 |

### Solicitud de recuperación (dato de solicitud, no persistido)

- `email` (string) enviado a `POST /api/auth/forgot-password`. No se guarda, ni en claro ni hasheado, y no se registra si correspondía a una cuenta (a diferencia del límite de reenvío de 003, esta feature no tiene tabla por correo — ver research.md §D6).

### Canje (dato de solicitud, no persistido)

- `token`, `password` y `confirmPassword` enviados a `POST /api/auth/reset-password`. La contraseña en claro solo existe durante la validación y el `encode`; no se registra ni se devuelve.

## Reglas de negocio

**Emisión (US1)**, dentro de una transacción de `PasswordResetService.requestReset`:

1. Normalizar el correo y buscar la cuenta. Si no existe o `emailVerifiedAt` es `null`, terminar sin efectos (FR-004).
2. Marcar con `used_at = now()` todos los tokens vigentes (`used_at IS NULL`) de la cuenta (FR-007).
3. Generar el valor en claro, guardar un token nuevo con `token_hash = sha256(valor)` y `expires_at = now() + 1 h` (FR-005, FR-006).
4. Disparar el correo asíncrono con el valor en claro (FR-008).
5. Devolver siempre el mensaje neutro (FR-002).

**Canje (US2)**, dentro de una transacción de `PasswordResetService.resetPassword` (`noRollbackFor` = token vencido):

1. Buscar por `token_hash = sha256(token)`. Si no existe o `used_at` no es `null` → enlace no válido (FR-011, FR-012). Un token ausente se hashea como cadena vacía y no coincide con nada.
2. Si `expires_at <= now()` → **eliminar ese token** y responder enlace vencido; la eliminación se confirma pese a la excepción (FR-013).
3. Validar `password` con la política de 001 y `confirmPassword` por igualdad. Si falla → errores por campo; **el token queda intacto** (FR-014).
4. `user.passwordHash = BCrypt(password)` y **eliminar todos los tokens de la cuenta** (`deleteByUserId`), incluidos los invalidados por solicitudes anteriores (FR-015).

## Transiciones de estado del token

```text
                 solicitud (US1)
[inexistente] ─────────────────────▶ [vigente]  (used_at NULL, expires_at > now)
                                        │
          nueva solicitud de la cuenta  │──────────▶ [invalidado] (used_at = now) ──┐
                                        │                                          │
                 canje con éxito (US2)  │──────────▶ [eliminado] ◀─────────────────┤ canje con éxito de
                                        │                                          │ otro token de la cuenta
            canje tras expires_at       └──────────▶ [eliminado] (410)             │
                                                                                   │
      canje de un token invalidado ──▶ 400 (sigue en [invalidado] hasta ───────────┘
                                        el próximo canje exitoso)

errores de campo en el canje ──▶ el token NO cambia de estado
eliminación de la cuenta ──▶ todos sus tokens se eliminan (ON DELETE CASCADE)
```

## Relaciones

- `User` 1 — 0..N `PasswordResetToken` en la tabla, pero como máximo **uno vigente** (`used_at IS NULL`) por cuenta (FR-007). La regla vive en el servicio, no en una restricción de base.
- Independiente de `email_verification_tokens` (003): son tablas, vencimientos y ciclos de vida distintos.

## Cambios de esquema

```sql
-- V3__password_reset_tokens.sql (Feature 006). Sin DELETE (regla del equipo).
CREATE TABLE password_reset_tokens (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ NULL,
    CONSTRAINT fk_password_reset_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_password_reset_tokens_expires_at CHECK (expires_at > created_at)
);

CREATE INDEX ix_password_reset_tokens_user_id ON password_reset_tokens (user_id);
```

`spring.jpa.hibernate.ddl-auto=validate`: Hibernate valida la entidad contra este esquema al arrancar. Número de versión: la rama de 005 (`feat/edit-profile`) no agrega migraciones, por lo que `V3` no colisiona.
