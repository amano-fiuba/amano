# Data Model: Ver listado de amigos (013)

**Fecha**: 2026-11-01 | **Spec**: [spec.md](./spec.md)

**Alcance de datos**: la feature **no crea ni modifica tablas, columnas, índices ni migraciones**, y **no escribe datos**. Lee la tabla `friendship_requests` de `V12__create_friendship_requests.sql` (011) y la tabla `users` (001).

## Entidades

### FriendshipRequest (existente — 011/012, solo lectura)

| Columna | Uso en 013 |
|---------|------------|
| `sender_id` / `recipient_id` | La sesión debe ser una de las dos; la otra es el amigo |
| `status` | Solo `ACCEPTED` cuenta como amistad |
| `responded_at` | Fecha de aceptación, expuesta como `friendsSince` |

Restricciones existentes en las que se apoya:

| Nombre | Efecto en 013 |
|--------|---------------|
| `uq_friendship_requests_accepted_pair` | A lo sumo una amistad por pareja: la lista no tiene duplicados |
| `ck_friendship_requests_responded` | Toda fila `ACCEPTED` tiene `responded_at` no nulo: `friendsSince` siempre existe |
| `fk_friendship_requests_*` (`ON DELETE CASCADE`) | Una cuenta eliminada desaparece de la lista |

### User (existente — 001/003, solo lectura)

Solo se leen `id`, `first_name`, `last_name` y `email` de la otra cuenta.

### FriendResponse (DTO nuevo)

| Campo | Tipo | Origen |
|-------|------|--------|
| `id` | `Long` | `users.id` de la otra cuenta |
| `firstName` | `String` | `users.first_name` |
| `lastName` | `String` (nullable) | `users.last_name` |
| `email` | `String` | `users.email` |
| `friendsSince` | `OffsetDateTime` | `friendship_requests.responded_at` |

## Consulta

Equivalente SQL de la consulta JPQL `findFriendsOf(:userId)`:

```sql
SELECT u.id, u.first_name, u.last_name, u.email, r.responded_at
FROM friendship_requests r
JOIN users u
  ON u.id = CASE WHEN r.sender_id = :userId THEN r.recipient_id ELSE r.sender_id END
WHERE r.status = 'ACCEPTED'
  AND (r.sender_id = :userId OR r.recipient_id = :userId)
ORDER BY lower(u.first_name), lower(u.last_name) NULLS LAST, u.id;
```

## Reglas de negocio

1. La persona sale de la sesión; nunca de un parámetro (FR-014).
2. Solo `ACCEPTED` y con participación de la sesión (FR-002, FR-003).
3. Se devuelve siempre la otra cuenta (FR-002).
4. Orden de FR-005.
5. Sin escritura (FR-015).

## Cambios de esquema

Ninguno.
