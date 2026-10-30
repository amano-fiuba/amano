# Data Model: Enviar solicitud de amistad (011)

**Fecha**: 2026-10-30 | **Spec**: [spec.md](./spec.md)

**Alcance de datos**: la feature crea **una tabla nueva**, `friendship_requests`, mediante `V6__create_friendship_requests.sql`. No modifica la tabla `users` ni ninguna fila existente de otras features.

## Entidades

### FriendshipRequest (nueva)

Relación dirigida entre dos cuentas. En 011 solo se insertan y consultan filas `PENDING`.

| Columna | Tipo | Restricciones | Regla |
|---------|------|---------------|-------|
| `id` | `BIGINT` | `GENERATED ALWAYS AS IDENTITY PRIMARY KEY` | Identificador de la solicitud |
| `sender_id` | `BIGINT` | `NOT NULL`; FK `users(id)` `ON DELETE CASCADE` | Cuenta que envía; sale de la sesión (FR-002) |
| `recipient_id` | `BIGINT` | `NOT NULL`; FK `users(id)` `ON DELETE CASCADE` | Cuenta destinataria; debe existir y estar verificada (FR-007) |
| `status` | `VARCHAR(10)` | `NOT NULL`; `CHECK IN ('PENDING','ACCEPTED','REJECTED')` | Estado del ciclo de vida (FR-003) |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Fecha de creación; ordena la bandeja (FR-012) |
| `responded_at` | `TIMESTAMPTZ` | `NULL` | Reservada para cuando se implemente aceptar/rechazar; en 011 queda `NULL` |

Restricciones:

| Nombre | Regla |
|--------|-------|
| `ck_friendship_requests_not_self` | `sender_id <> recipient_id` (respaldo de FR-004) |
| `ck_friendship_requests_status` | catálogo de estados |
| `ck_friendship_requests_responded` | `PENDING` ⇒ `responded_at IS NULL`; `ACCEPTED`/`REJECTED` ⇒ `responded_at IS NOT NULL` (preparado para la historia futura; 011 no crea esos estados) |

Índices:

| Nombre | Definición | Propósito |
|--------|------------|-----------|
| `uq_friendship_requests_pending_pair` | `UNIQUE (LEAST(sender_id, recipient_id), GREATEST(sender_id, recipient_id)) WHERE status = 'PENDING'` | A lo sumo una solicitud pendiente por pareja, sin importar la dirección (FR-005, FR-009, SC-006) |
| `uq_friendship_requests_accepted_pair` | `UNIQUE (LEAST(sender_id, recipient_id), GREATEST(sender_id, recipient_id)) WHERE status = 'ACCEPTED'` | A lo sumo una amistad por pareja cuando exista la aceptación (FR-006) |
| `ix_friendship_requests_recipient_pending` | `(recipient_id, created_at DESC, id DESC) WHERE status = 'PENDING'` | Bandeja "Solicitudes" del destinatario (FR-012) |

### FriendshipRequestStatus (enum nuevo)

| Código | Etiqueta | Significado en 011 |
|--------|----------|--------------------|
| `PENDING` | Pendiente | Único estado que se crea y se lista |
| `ACCEPTED` | Aceptada | Amistad existente; no se crea en 011, pero se consulta para FR-006 |
| `REJECTED` | Rechazada | Histórica; no bloquea un envío nuevo (Assumptions) |

### User (existente — 001/003, solo lectura)

Se usa para resolver al emisor (por sesión) y al destinatario (por `recipientId`). Campos relevantes: `id`, `first_name`, `last_name`, `email`, `email_verified_at`. No se modifica.

## Reglas de negocio

**Enviar una solicitud** (`FriendshipRequestService.send(senderId, recipientId)`):

1. `recipientId` ausente, nulo, no numérico o `<= 0` ⇒ `400` con `properties.fields.recipientId` (FR-008).
2. `recipientId == senderId` ⇒ `400` "No podés enviarte una solicitud de amistad a vos mismo" (FR-004).
3. El destinatario no existe o `email_verified_at IS NULL` ⇒ `404` "No se encontró el usuario" (FR-007).
4. Existe una solicitud `ACCEPTED` entre la pareja ⇒ `409` "Ya son amigos" (FR-006).
5. Existe una solicitud `PENDING` entre la pareja en cualquier dirección ⇒ `409` "Ya existe una solicitud de amistad pendiente entre ambos usuarios" (FR-005).
6. Se inserta `PENDING` con `created_at = now()` y `responded_at = NULL`; si el `INSERT` choca con `uq_friendship_requests_pending_pair` por una carrera, se traduce al mismo `409` del paso 5 (FR-009).
7. Se devuelve `201` con `id`, `status`, `createdAt` y el resumen del destinatario.

**Listar solicitudes pendientes recibidas** (`FriendshipRequestService.listReceivedPending(recipientId)`):

1. Filtra `recipient_id = :sessionUserId AND status = 'PENDING'`.
2. Ordena por `created_at DESC, id DESC`.
3. Devuelve `id`, `status`, `createdAt` y el resumen del emisor.
4. No hay paginación en 011.

**Amistad**: existe si hay una fila `ACCEPTED` entre la pareja (en cualquier dirección). En 011 no se crean filas `ACCEPTED`.

## Relaciones

- `User` 1 — 0..N `FriendshipRequest` como emisor (`sender_id`).
- `User` 1 — 0..N `FriendshipRequest` como destinatario (`recipient_id`).
- Una pareja de usuarios tiene como máximo una solicitud `PENDING` y como máximo una `ACCEPTED` (índices únicos parciales). Puede acumular varias `REJECTED` históricas.

## DTOs

### SendFriendshipRequestRequest (entrada)

```json
{ "recipientId": 12 }
```

| Campo | Tipo | Regla |
|-------|------|-------|
| `recipientId` | number | Obligatorio, entero positivo; no puede ser el emisor |

### FriendshipRequestResponse (salida del envío)

```json
{
  "id": 40,
  "status": "PENDING",
  "createdAt": "2026-10-30T14:05:00Z",
  "recipient": {
    "id": 12,
    "firstName": "Ana",
    "lastName": "Pérez",
    "email": "ana.perez@example.com"
  }
}
```

### ReceivedFriendshipRequestResponse (elemento de la bandeja)

```json
{
  "id": 41,
  "status": "PENDING",
  "createdAt": "2026-10-30T14:06:00Z",
  "sender": {
    "id": 31,
    "firstName": "Bruno",
    "lastName": null,
    "email": "bruno@example.com"
  }
}
```

Ninguno de los DTOs incluye CBU/CVU, monedas, contraseña, hash, avatar ni fecha de verificación. El avatar por defecto lo decide el frontend (FR-011).

## Cambios de esquema

```sql
-- V6__create_friendship_requests.sql (Feature 011). Sin UPDATE ni DELETE.
CREATE TABLE friendship_requests (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sender_id    BIGINT NOT NULL,
    recipient_id BIGINT NOT NULL,
    status       VARCHAR(10) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ NULL,
    CONSTRAINT fk_friendship_requests_sender    FOREIGN KEY (sender_id)    REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_friendship_requests_recipient FOREIGN KEY (recipient_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_friendship_requests_not_self  CHECK (sender_id <> recipient_id),
    CONSTRAINT ck_friendship_requests_status    CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT ck_friendship_requests_responded CHECK (
        (status = 'PENDING' AND responded_at IS NULL)
        OR (status IN ('ACCEPTED', 'REJECTED') AND responded_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_friendship_requests_pending_pair
    ON friendship_requests (LEAST(sender_id, recipient_id), GREATEST(sender_id, recipient_id))
    WHERE status = 'PENDING';

CREATE UNIQUE INDEX uq_friendship_requests_accepted_pair
    ON friendship_requests (LEAST(sender_id, recipient_id), GREATEST(sender_id, recipient_id))
    WHERE status = 'ACCEPTED';

CREATE INDEX ix_friendship_requests_recipient_pending
    ON friendship_requests (recipient_id, created_at DESC, id DESC)
    WHERE status = 'PENDING';
```

Mapeo JPA (`com.amano.backend.friendship.FriendshipRequest`): `id`, `senderId` y `recipientId` como `Long` (sin relaciones JPA, para resolver los resúmenes con una consulta explícita y no cargar entidades completas), `status` con `@Enumerated(EnumType.STRING)`, `createdAt` y `respondedAt` como `OffsetDateTime`.
