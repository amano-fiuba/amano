# Research: Extensión de grupos y buscador (021)

**Date**: 2026-11-06 | **Spec**: [spec.md](./spec.md)

## D1 — Documentar en una spec nueva

018 fija la costumbre: las specs anteriores no se editan y sus extensiones se documentan aparte. La tabla de `spec.md` indica qué punto de cada spec queda reemplazado.

## D2 — `friendshipStatus` en la búsqueda

011 había descartado cambiar el contrato de 008 y se apoyaba en el 409 del backend. El error reportado muestra que no alcanza: quien recibió una solicitud veía "Agregar". El campo es aditivo y sin datos privados.

`findActiveBetween` trae en una consulta las solicitudes `PENDING` y `ACCEPTED` entre quien busca y los resultados. Las `REJECTED` no se consultan.

## D3 — Una sola casilla positiva

Primero se implementaron dos casillas (descripción y foto) y luego se unificaron en "Pueden editar datos del grupo". Se agregó una columna nueva en lugar de reutilizar `members_cannot_edit_settings`, porque su sentido es el opuesto y sus valores guardados no se pueden reinterpretar.

## D4 — Autorización por campo en `PUT /api/groups/{id}`

Un mismo `PUT` admite datos y permisos, así que se evalúa por lo que trae: `permissions` exige `canManagePermissions` y `name` o `description` exigen `canEditGroupData`. `canManagePhoto` delega en `canEditGroupData`.

## D5 — Cambios pendientes hasta guardar

La foto se cargaba al elegirla, lo que no permite cancelar. `GroupEditForm` retiene los cambios y `GroupPhotoPanel` pasa a ser controlado. Guardar son hasta dos solicitudes sucesivas (`PUT` y foto), no atómicas: aceptado porque un fallo deja datos válidos y un error visible.

## D6 — Etiqueta solo en el frontend

`GroupMemberView` ya distingue los perfiles con `simulatedProfileId`. No hace falta cambiar el contrato.

## D7 — Eliminar la casilla reemplazada

`members_cannot_edit_settings` quedó sin efecto cuando se publicó `V15`. Se elimina del todo, con `V16`, para que nadie crea que sigue funcionando:

- Columna (`V16`), campo de `GroupPermissions` y de `UpdateGroupRequest`, copia en `GroupService.updateGroupSettings` y `GroupPermissionChecker.canEditSettings`.
- Va en una migración nueva porque `V15` ya está integrada, y hace falta quitar el mapeo en el mismo cambio porque con `ddl-auto=validate` una columna eliminada impide arrancar.
- El borrado no es reversible: los valores guardados se pierden. Se acepta porque no tenían efecto y la casilla nueva tiene el sentido opuesto.
