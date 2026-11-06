# Data Model: Abandonar grupo (020)

**Date**: 2026-11-06 | **Spec**: [spec.md](./spec.md)

## Cambios de esquema

Ninguno. Sin migraciones.

## Entidades

### GroupMember (existente — 010)

Abandonar completa `left_at`. La fila nunca se borra.

### Grupo sin miembros activos (estado, no entidad nueva)

Todas sus filas de `group_members` tienen `left_at`. El grupo y sus datos relacionados siguen en la base:

| Tabla | Qué pasa al irse el último miembro |
|---|---|
| `groups` | Sin cambios |
| `group_members` | `left_at` de la persona |
| `group_activity` | Un evento `MEMBER_LEFT` más |
| `simulated_profiles`, `group_permissions`, `group_photos` | Sin cambios |
| `group_expenses`, `group_expense_shares`, `debts`, `payments` | Sin cambios |

## Operación: abandonar (`GroupService.leaveGroup`)

Entrada: `userId`, `groupId`. Una transacción.

1. Grupo y membresía activa, o 404 neutral.
2. `debtPort.hasPendingDebtsForUser` → `PendingDebtsException`.
3. `lastMember = countActiveByGroupId(groupId) <= 1`.
4. `!lastMember`, rol ADMIN y `countActiveAdminsByGroupId <= 1` → `LastAdminException`.
5. `left_at = now()` y `MEMBER_LEFT`.
6. Respuesta con `groupDeleted = lastMember`.

## Respuestas

- `LeaveGroupResponse { message, groupLeft, groupDeleted }` (publicado).
- `GroupDetailResponse`: `isLastMember` (publicado) e `isLastAdmin` (nuevo).

## Invariantes

- Abandonar no ejecuta ningún `DELETE`.
- Un grupo con miembros activos siempre tiene al menos un administrador activo, salvo salidas simultáneas (D7).
