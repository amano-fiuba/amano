# Research: Abandonar grupo (020)

**Date**: 2026-11-06 | **Spec**: [spec.md](./spec.md)

## D1 — Baja lógica también para el último miembro

010 define la salida como baja lógica y deja la disolución fuera de esta versión. El último miembro sigue el mismo camino: se completa `left_at` y se registra `MEMBER_LEFT`. No hace falta nada más para que el grupo desaparezca: `findActiveGroupsOfUser` y `activeMembership` exigen una membresía activa.

Descartado: borrado físico con conservación de movimientos. Exigía una migración sobre `group_expenses`, `debts` y `payments`, y contradecía 010.

## D2 — Excepción al bloqueo de último administrador

010 bloquea siempre al último administrador. Aplicado sin matices, quien queda solo en un grupo no podría salir nunca: no tiene a quién asignarle el rol. Por eso el bloqueo se limita a cuando quedan otros miembros registrados activos. Es la única regla de 010 que cambia y ya está en el código publicado.

## D3 — "Último miembro" = único miembro registrado activo

`memberCount` suma perfiles simulados y no sirve para decidirlo. El servidor calcula `isLastMember` con `countActiveByGroupId(groupId) <= 1`.

## D4 — `isLastAdmin` lo calcula el servidor

Hoy `GroupInfoView` deduce `isLastAdmin` con `role === 'ADMIN' && memberCount === 1`. Eso es falso justo en el caso que importa: un único administrador con otros miembros tiene `memberCount >= 2`, ve la confirmación y recién al confirmar recibe el rechazo. El servidor pasa a informar `isLastAdmin` (rol ADMIN y `countActiveAdminsByGroupId <= 1`).

## D5 — Orden de las reglas

1. 404 neutral si no es miembro activo.
2. Saldos pendientes → 403 `PENDING_DEBTS`.
3. Quedan otros miembros y es el único administrador → 403 `LAST_ADMIN`.
4. Baja lógica y `MEMBER_LEFT`; `groupDeleted` según corresponda.

Es el orden del código publicado.

## D6 — Modal real, sin librería

`LeaveGroupPanel` muestra hoy un bloque en línea con `role="dialog"`, sin equis ni Escape, y el modo de último administrador tiene botón "Cancelar". Se crea `LeaveGroupModal` con los tres modos, sin dependencias nuevas.

## D7 — Sin bloqueo del grupo

Dos salidas simultáneas de los últimos miembros dejan el grupo sin activos, sin pérdida de datos: solo puede variar el mensaje. No justifica agregar `SELECT … FOR UPDATE`. El riesgo de que dos únicos administradores se vayan a la vez ya existe en 010 y no se aborda acá.

## D8 — Pruebas

- Backend: `GroupLeaveServiceTest`, `LeaveGroupIT`, `MembersListIT` o equivalente para los flags.
- Frontend: `LeaveGroupModal`, `LeaveGroupPanel`, `GroupInfoView`.

`LeaveGroupIT.memberLeavesTheGroupAndItPersistsForTheOthers` todavía espera el bloqueo del último administrador cuando queda solo: hay que actualizarlo a la nueva regla.
