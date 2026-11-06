# Data Model: Extensión de grupos y buscador (021)

**Date**: 2026-11-06 | **Spec**: [spec.md](./spec.md)

## Migración — `V15__group_permissions_edit_group_data.sql`

```sql
ALTER TABLE group_permissions
    ADD COLUMN members_can_edit_group_data BOOLEAN NOT NULL DEFAULT FALSE;
```

## Migración — `V16__drop_members_cannot_edit_settings.sql`

```sql
ALTER TABLE group_permissions
    DROP COLUMN members_cannot_edit_settings;
```

Elimina los valores guardados; no es reversible. Se aplica junto con el código que deja de mapear la columna (`ddl-auto=validate`).

## Entidades

### GroupPermissions (existente — 010)

- Nuevo: `membersCanEditGroupData`.
- Eliminado: `membersCannotEditSettings` (`V16`).

### GroupPhoto (existente — 010)

La fila se borra al eliminar la foto y `groups.photo` queda nulo. Sigue valiendo que la fila existe solo con foto propia.

### UserSearchResult (existente — 008)

Suma `friendshipStatus`. No es una columna: se calcula en cada búsqueda.

| Solicitudes entre ambos | `friendshipStatus` |
|---|---|
| Ninguna, o solo `REJECTED` | `NONE` |
| `PENDING` enviada por quien busca | `PENDING_SENT` |
| `PENDING` enviada por el otro | `PENDING_RECEIVED` |
| `ACCEPTED` | `FRIENDS` |

## Reglas de autorización

| Acción | ADMIN | MEMBER con casilla | MEMBER sin casilla |
|---|---|---|---|
| Foto, nombre, descripción | Sí | Sí | No |
| Permisos | Sí | No | No |

## Sin cambios

`friendship_requests`, `simulated_profiles` y `group_activity` no cambian. `PHOTO_CHANGED` ya existía.
