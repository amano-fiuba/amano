# Data Model: Eliminar amigo (016)

**Fecha**: 2026-11-05 | **Spec**: [spec.md](./spec.md)

**Alcance de datos**: la feature **no crea ni modifica tablas, columnas, índices ni migraciones**. Elimina una fila existente de `friendship_requests` (`V12`, 011) y lee `users` (001) y, a través de un puerto, el futuro estado de deuda directa. **No escribe ni borra** `expenses`, `group_members`, `group_activity`, `groups` ni datos de cuentas.

## Entidades

### FriendshipRequest (existente — 011/012/013)

| Columna | Uso en 016 |
|---------|------------|
| `sender_id` / `recipient_id` | Definen la pareja; la amistad se busca en cualquier dirección |
| `status` | Solo `ACCEPTED` representa una amistad; es la única fila que 016 elimina |
| `responded_at` | No se usa para la eliminación; se conserva mientras exista la fila |

Restricciones existentes en las que se apoya:

| Nombre | Efecto en 016 |
|--------|---------------|
| `uq_friendship_requests_accepted_pair` | A lo sumo una amistad por pareja: el borrado afecta como máximo una fila |
| `ck_friendship_requests_not_self` | No existe una amistad de una persona consigo misma |
| `fk_friendship_requests_*` (`ON DELETE CASCADE`) | No interviene: 016 no borra cuentas |
| `uq_friendship_requests_pending_pair` | Las solicitudes pendientes no se tocan |

### User (existente — 001/003, solo lectura)

Solo se usa el id de la sesión y el id del amigo de la ruta. No se leen ni modifican datos del perfil.

### DirectDebtPort (nuevo — puerto de dominio)

| Elemento | Tipo | Descripción |
|----------|------|-------------|
| `hasPendingDirectDebt(Long userId, Long friendId)` | `boolean` | `true` si existe una deuda directa pendiente entre ambas personas por un gasto compartido sin grupo |

Implementación actual:

- `NoOpDirectDebtPort` (`@Component`): siempre `false`, porque el módulo de gastos compartidos/deudas todavía no existe. El futuro módulo reemplazará esta implementación sin cambiar la interfaz ni la spec.
- No depende de `DebtPort` de 010: ese puerto consulta deudas dentro de un grupo (`groupId`, `userId`), no deudas directas entre dos personas.

### DeletionEligibilityResponse (DTO nuevo, solo lectura)

| Campo | Tipo | Origen |
|-------|------|--------|
| `canDelete` | `boolean` | `true` si hay amistad `ACCEPTED` y no hay deuda directa pendiente |
| `reason` | `String` (nullable) | Motivo cuando `canDelete` es `false`; `null` cuando es `true` |

## Operaciones

### Consulta de elegibilidad

```sql
-- Amistad: fila ACCEPTED entre la sesión y el amigo, en cualquier dirección.
SELECT CASE WHEN COUNT(r) > 0 THEN TRUE ELSE FALSE END
FROM friendship_requests r
WHERE r.status = 'ACCEPTED'
  AND ((r.sender_id = :userId AND r.recipient_id = :friendId)
    OR (r.sender_id = :friendId AND r.recipient_id = :userId));
```

Luego se consulta `DirectDebtPort.hasPendingDirectDebt(userId, friendId)`.

### Eliminación

```sql
-- Borrado condicional de la amistad (como máximo una fila por el índice único).
DELETE FROM friendship_requests
WHERE status = 'ACCEPTED'
  AND ((sender_id = :userId AND recipient_id = :friendId)
    OR (sender_id = :friendId AND recipient_id = :userId));
```

- Filas afectadas `1` → éxito (`204`).
- Filas afectadas `0` → la amistad no existe o ya fue eliminada (`404` neutro).

## Reglas de negocio

1. La persona sale de la sesión; el amigo sale de la ruta. No se acepta un id de emisor por cuerpo (FR-002).
2. Solo se elimina la fila `ACCEPTED`; `PENDING` y `REJECTED` no se modifican (FR-009).
3. Con deuda directa pendiente no se elimina nada (FR-011/FR-012).
4. Las deudas de grupo no bloquean ni se modifican (FR-014).
5. Gastos, historial y grupos no se tocan (FR-017/FR-018).
6. La amistad desaparece para ambos al borrarse la fila única de la pareja (FR-007).
7. Después de eliminar, una nueva solicitud aceptada puede restablecer la amistad (FR-010).

## Cambios de esquema

Ninguno. No hay migración nueva.
