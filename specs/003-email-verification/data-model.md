# Data Model: Verificación de correo electrónico

**Feature**: `003-email-verification` | **Fecha**: 2026-10-19 | **Spec**: [spec.md](../spec.md)

Este documento describe el modelo de datos nuevo o modificado por esta feature. La migración
Flyway `V2__email_verification.sql` es el modelo físico definitivo (`ddl-auto=validate` exige
coincidencia exacta con las entidades JPA); este documento es la fuente de verdad semántica.

## Diagrama de relaciones

```text
users (MODIFICADA - columna nueva)
├── id (PK)
├── … columnas existentes de 001 …
└── email_verified_at (NUEVA, NULL = no verificada)
        ▲
        │ 1
        │
        │ N
email_verification_tokens (NUEVA)
├── id (PK)
├── user_id (FK → users, ON DELETE CASCADE)
├── token_hash (UNIQUE, SHA-256 del token en claro)
├── created_at
├── expires_at (= created_at + 24 h)
└── used_at (NULL = vigente; seteado al canjear o al invalidarse)

email_resend_limits (NUEVA - sin FK: aplica exista o no la cuenta)
├── email_hash (PK, SHA-256 del correo normalizado)
└── last_resend_at
```

## Entidades

### users (modificada)

Nueva columna respecto de 001:

| Columna | Tipo | Regla | Fuente |
|---|---|---|---|
| `email_verified_at` | `TIMESTAMPTZ NULL` | `NULL` = cuenta "no verificada" (bloqueada); con valor = instante en que el token válido fue canjeado. Se establece únicamente por el canje de token. | FR-001, FR-005, FR-004 |

El resto de la tabla (columnas, CHECKs, índice `uq_users_email`) no cambia. El índice
UNIQUE de correo sigue garantizando una sola cuenta por dirección normalizada; ahora esa
cuenta puede estar verificada o no.

### email_verification_tokens (nueva)

| Columna | Tipo | Regla | Fuente |
|---|---|---|---|
| `id` | `BIGINT GENERATED ALWAYS AS IDENTITY PK` | Idéntico al patrón de `users` (V1). | — |
| `user_id` | `BIGINT NOT NULL FK → users(id) ON DELETE CASCADE` | Un token siempre pertenece a una cuenta. Al eliminar la cuenta (limpieza a 3 días) el token desaparece en cascada. | FR-017, FR-018 |
| `token_hash` | `VARCHAR(64) NOT NULL UNIQUE` | SHA-256 en hex (64 chars) del token en claro. El valor en claro jamás se persiste; la búsqueda por canje es por hash. | D2 |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` | Inicio de la validez y referencia de la caducidad de 3 días de la cuenta. | FR-003, FR-017 |
| `expires_at` | `TIMESTAMPTZ NOT NULL` | `created_at + 24 h` (configurable vía properties para tests). | FR-003, FR-021 |
| `used_at` | `TIMESTAMPTZ NULL` | `NULL` = vigente y sin usar. Se setea al canjear exitosamente o al invalidarse por emisión de un token nuevo. | FR-007, FR-009 |

Restricciones: CHECK `expires_at > created_at`. Solo puede existir **un token vigente por
cuenta** (la emisión nueva invalida a la anterior, FR-009); se sostiene en el servicio (marcar
`used_at` de los vigentes previos en la misma transacción que emite el nuevo) y se apoya en
la UNIQUE de `token_hash`.

### email_resend_limits (nueva)

| Columna | Tipo | Regla | Fuente |
|---|---|---|---|
| `email_hash` | `VARCHAR(64) NOT NULL PK` | SHA-256 en hex del correo normalizado (trim + minúsculas). No se guarda el correo en claro: la fila existe aunque no haya cuenta. | D4, FR-011 |
| `last_resend_at` | `TIMESTAMPTZ NOT NULL` | Instante del último clic en "reenviar" que procesó (no el del correo del primer registro). Un clic durante el límite NO la actualiza. | FR-011, FR-013 |

Sin FK hacia `users`: el límite es por dirección de correo, exista o no la cuenta asociada
(FR-011) y con independencia de la sesión o el dispositivo (supuesto de la spec).

Tamaño de fila mínimo por diseño: no se almacena ningún otro dato del usuario no
registrado (privacidad).

## Reglas derivadas (no columnas)

- **Normalización de correo**: la misma de 001 (`trim` + `toLowerCase(Locale.ROOT)`) se
  aplica antes de calcular `email_hash` y de buscar la cuenta; una sola dirección normalizada
  es la identidad única (supuesto de 001, vigente).
- **Hash**: SHA-256 estándar (`java.security.MessageDigest`), hex lowercase. Se usa para
  `token_hash` (D2) y `email_hash` (D4); en ambos casos es una función determinista de un
  valor de alta entropía (token) o de baja sensibilidad (correo), no una contraseña.
- **Emisión de token (registro, reenvío o re-registro)**: en una única transacción —
  invalidar tokens vigentes de la cuenta (`used_at = now()`), insertar el token nuevo,
  upsert de `email_resend_limits` cuando corresponde; el envío del correo ocurre después,
  `@Async` (D1), nunca dentro de la transacción.
- **Límite de reenvío**: `now() - last_resend_at < 5 min` ⇒ no se envía y se devuelve el
  tiempo restante; si el clic se procesa (no había límite), se actualiza
  `last_resend_at = now()` (el reenvío cuenta desde el clic, FR-011).
- **Correo del primer registro**: nunca toca `email_resend_limits` (no cuenta para el
  límite, FR-011 y clarificación Q3).
- **Limpieza (3 días)**: `DELETE` (o cascade) de cuentas con `email_verified_at IS NULL` y
  sin ningún token vigente y cuyo token más reciente tiene `created_at < now() - 3 días`.
  Los límites de reenvío de esas direcciones NO se eliminan (siguen conteniendo el
  bombardeo); caducan solos por antigüedad en la práctica del límite de 5 min (la fila queda
  como huella de hash, sin datos sensibles).
- **El registro no crea sesión**: ninguna escritura produce cookie de sesión; la única vía
  de sesión de esta feature es el canje de token válido (FR-005) o el stub de login
  (FR-014), ambos reutilizando `JwtService`/`AuthCookie` de 001.

## Ciclo de vida / estados

### Cuenta de usuario

```text
                    registro válido (FR-001)
                            │
                            ▼
                   ┌─────────────────┐
                   │  NO VERIFICADA  │  ← no accede a /api/users/** (FR-004)
                   │ (email_verified │     no puede iniciar sesión (FR-014)
                   │    _at = NULL)  │     recibe correos de token (emitidos)
                   └────────┬────────┘
        canje de token      │              limpieza @Scheduled (>3 días
        válido (FR-005)     │              sin verificación, FR-017)
                            ▼                     │
                   ┌─────────────────┐            ▼
                   │   VERIFICADA    │     cuenta eliminada, correo
                   │(email_verified  │     liberado para nuevo registro
                   │  _at = fecha)   │     completo (SC-007)
                   └─────────────────┘
```

### Token de verificación

```text
   emisión (registro | reenvío | re-registro)
                │
                ▼
      ┌───────────────────┐   expira (24 h)     ┌──────────────┐
      │ VIGENTE           │ ──────────────────▶ │ VENCIDO      │
      │ (used_at = NULL)  │                     │ (used_at =   │
      └────────┬──────────┘                     │  seteado*)   │
               │ canje                          └──────────────┘
               │ válido
               │ (used_at = now(),             * vencido detectado en canje:
               │  cuenta → VERIFICADA)           se setea used_at y responde 410
               ▼
      ┌───────────────────┐   emisión de token nuevo (FR-009)
      │ USADO             │ ◀──────────────────────────────────
      │ (used_at seteado) │   (los vigentes previos quedan usados/invalidados)
      └───────────────────┘

  canje de USADO/VENCIDO  → 409 / 410 (la cuenta no cambia)
  canje de token inexistente o alterado → 400
```

## Volumen y escala

Proyecto universitario con tráfico bajo. Estimaciones:

- `email_verification_tokens`: 1 fila por emisión; un registro genera 1 y cada reenvío 1
  (limitado a 1/5 min por dirección). Pico practico: cientos de filas; las de cuentas
  verificadas quedan como histórico (podrían purgarse en una tarea futura, fuera de alcance).
- `email_resend_limits`: 1 fila por dirección que usó "reenviar" alguna vez; crecimiento
  lineal con solicitudes, filas de ~100 bytes sin datos sensibles.
- Queries relevantes: UNIQUE por hash/índice de correo → O(1); la limpieza horaria recorre
  un índice sobre `email_verified_at` parcial (`WHERE email_verified_at IS NULL`), conjunto
  pequeño.

## Compatibilidad y migración

- Migración única `V2__email_verification.sql` (nombre al patrón `V<n>__snake_case.sql` de
  V1); **sin DELETE** (regla del equipo): solo `ALTER TABLE` + `CREATE TABLE`.
- `ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ NULL;` — sin DEFAULT, por lo
  que las cuentas existentes quedan como "no verificadas"; la spec indica que no hay datos en
  producción (las cuentas de prueba se descartan y se recrean), por lo que no se requiere
  backfill ni script de transición.
- `spring.jpa.hibernate.ddl-auto=validate` exige que las entidades JPA (`User` con el campo
  nuevo, `VerificationToken`, `EmailResendLimit`) coincidan exactamente con V2.
- Rollback: fuera del alcance de esta historia (proyecto sin producción); si se requiriera,
  sería una migración V3 que revierte los cambios, nunca editar V2 ya aplicada.
