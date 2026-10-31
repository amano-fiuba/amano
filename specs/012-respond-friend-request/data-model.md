# Data Model: Aceptar y rechazar solicitudes de amistad (012)

**Fecha**: 2026-10-30 | **Spec**: [spec.md](./spec.md)

**Alcance de datos**: la feature **no crea ni modifica ninguna tabla ni migración**. Reutiliza la tabla `friendship_requests` de `V6__create_friendship_requests.sql` (011), que ya soporta los estados finales y la fecha de respuesta especialmente preparados para esta historia. No se agregan columnas.

## Entidades

### FriendshipRequest (existente — 011, ahora con transición activa)

En 011 solo se insertaban filas `PENDING`; esta historia introduce el único `UPDATE` del ciclo de vida: la transición `PENDING → ACCEPTED` (aceptar) o `PENDING → REJECTED` (rechazar). Las filas con estado final son estables: ningún flujo posterior vuelve a modificarlas.

| Columna | Uso en 012 |
|---------|------------|
| `id` | Identificador de la solicitud; sale de la ruta del endpoint |
| `sender_id` | Emisor; nunca puede responder (FR-007) |
| `recipient_id` | Destinataria; única persona autorizada a responder y condición del `UPDATE` |
| `status` | `PENDING` → `ACCEPTED` o `REJECTED`; los estados finales son fijos |
| `created_at` | Conservado sin cambios |
| `responded_at` | `NULL` → `now()` en la misma sentencia del `UPDATE` |

Restricciones existentes que esta historia activa (todas de `V6`, sin cambios):

| Nombre | Regla | Efecto en 012 |
|--------|-------|---------------|
| `ck_friendship_requests_responded` | `PENDING` ⇒ `responded_at IS NULL`; `ACCEPTED`/`REJECTED` ⇒ `responded_at IS NOT NULL` | Hace imposible una transición sin fecha o una pendiente con fecha |
| `ck_friendship_requests_status` | Catálogo `PENDING/ACCEPTED/REJECTED` | Ninguna acción puede crear estados nuevos |
| `uq_friendship_requests_accepted_pair` | Única fila `ACCEPTED` por pareja no ordenada | La aceptación no puede duplicar una amistad existente |
| `fk_friendship_requests_sender` / `fk_friendship_requests_recipient` | FK a `users` con `ON DELETE CASCADE` | Solicitud de una cuenta eliminada desaparece y responde 404 |

Índice existente sin cambio de propósito:

| Nombre | Uso en 012 |
|--------|------------|
| `uq_friendship_requests_pending_pair` | `PENDING` ya no es terminal; tras responder la fila sale del índice parcial y la pareja puede reenviarse solicitudes si se rechazó (regla 011) |

### FriendshipRequestAction (enum nuevo)

| Código | Etiqueta | Efecto |
|--------|----------|--------|
| `ACCEPT` | Aceptar | Transición `PENDING → ACCEPTED`; crea la amistad |
| `REJECT` | Rechazar | Transición `PENDING → REJECTED`; no crea amistad |

Solo estos dos valores son válidos en el cuerpo (FR-010); la comparación es exacta, sin normalización de mayúsculas.

### User (existente — 001/003, solo lectura)

La sesión resuelve a la persona que responde (`recipient_id` debe coincidir). El resumen del emisor se lee de `users` solo cuando ya está en el registro de la solicitud. No se modifica la tabla.

## Reglas de negocio

**Responder una solicitud** (`FriendshipRequestService.respond(responderId, requestId, action)`):

1. `requestId` no numérico en la ruta ⇒ `400` del framework (sin acceso a datos); `requestId <= 0` ⇒ `400` con `properties.fields.id` "La solicitud no es válida" (FR-010).
2. `action` nulo, vacío o distinto de `ACCEPT`/`REJECT` ⇒ `400` con `properties.fields.action` "La acción no es válida", sin consultar la base (FR-010).
3. `@Transactional`: se ejecuta la actualización condicional
   `UPDATE friendship_requests SET status = :newStatus, responded_at = :now WHERE id = :requestId AND recipient_id = :responderId AND status = 'PENDING'`.
4. Si el `UPDATE` no afecta filas, se relee la fila por `id` para distinguir el motivo: fila inexistente o con `recipient_id` distinto al de la sesión ⇒ `404` "No se encontró la solicitud de amistad" (FR-007/FR-009); fila con `ACCEPTED` o `REJECTED` ⇒ `409` "Esta solicitud de amistad ya fue respondida" (FR-008). En ambos casos no se escribe nada.
5. Con éxito se devuelve `200` con el estado final, `respondedAt` y el resumen del emisor.

**Amistad**: la aceptación crea la primera relación de amistad real del sistema (fila `ACCEPTED` entre dos cuentas). Su listado queda para la historia posterior; el rechazo no crea amistad (FR-005).

## Relaciones

- Sin cambios respecto de 011: `User` 1 — 0..N `FriendshipRequest` (emisor y destinataria); a lo sumo una `PENDING` y una `ACCEPTED` por pareja; varias `REJECTED` históricas posibles.
- La única mutación nueva es la transición de estado; no cambian cardinalidades ni FKs.

## DTOs

### RespondFriendshipRequestRequest (entrada)

```json
{ "action": "ACCEPT" }
```

| Campo | Tipo | Regla |
|-------|------|-------|
| `action` | string | Obligatorio; exactamente `ACCEPT` o `REJECT` (sin normalización de mayúsculas) |

### AnsweredFriendshipRequestResponse (salida)

```json
{
  "id": 40,
  "status": "ACCEPTED",
  "createdAt": "2026-10-30T14:05:00Z",
  "respondedAt": "2026-10-30T15:12:00Z",
  "sender": {
    "id": 31,
    "firstName": "Bruno",
    "lastName": null,
    "email": "bruno@example.com"
  }
}
```

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | number | Identificador de la solicitud |
| `status` | string | `ACCEPTED` o `REJECTED` según la acción confirmada |
| `createdAt` | string (ISO-8601) | Fecha de creación original |
| `respondedAt` | string (ISO-8601) | Fecha de la respuesta recién registrada |
| `sender.id` | number | Identificador de la cuenta emisora |
| `sender.firstName` | string | Nombre del emisor |
| `sender.lastName` | string \| null | Apellido del emisor; puede ser `null` |
| `sender.email` | string | Correo del emisor |

El DTO nunca incluye CBU/CVU, monedas, contraseña, hash, avatar ni fecha de verificación; el correo y el nombre del emisor ya eran visibles en la bandeja (011, FR-011).

## Cambios de esquema

```sql
-- Ninguno. La historia opera sobre friendship_requests (V6, Feature 011):
--   status          PENDING -> ACCEPTED | REJECTED
--   responded_at    NULL    -> now()
-- Restricciones e índices exactamente como fueron creados por 011.
```

La restricción `ck_friendship_requests_responded` es la garantía de coherencia de la transición: un `UPDATE` que dejara un estado final sin fecha, o una pendiente con fecha, sería rechazado por la base incluso ante un error de código.

Mapeo JPA: no se alteran las entidades existentes. La transición se implementa como consulta `@Modifying @Query` en `FriendshipRequestRepository` (filas afectadas como `int`), sin cargar la entidad para escribir; la relectura usa `findById` + verificación de `recipient_id` y `status`.
