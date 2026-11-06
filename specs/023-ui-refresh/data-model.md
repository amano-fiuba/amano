# Data Model: Renovación de la interfaz (023)

**Date**: 2026-11-06 | **Spec**: [spec.md](./spec.md)

**Sin migraciones ni tablas nuevas.**

## Representaciones

### FriendshipUserSummary (existente — 011)

Suma `avatarUrl`. No es una columna: vale `/api/users/{id}/avatar` si la cuenta tiene una fila en `user_avatars`, y nulo si no la tiene. Solo la bandeja de solicitudes recibidas lo completa.

### ExpenseResponse (existente — 007)

Sin cambios. `GET /api/expenses` lo reutiliza.

### UserSearchResult (existente — 008, 021)

Suma `avatarUrl`. No es una columna: se resuelve en cada búsqueda con la regla de visibilidad de abajo.

### CreateGroupRequest (existente — 010)

Suma `members`, una lista de `MemberInput`. No es una columna. `member` se conserva.

## Consultas nuevas

| Repositorio | Método | Uso |
|---|---|---|
| `ExpenseRepository` | `findTop200ByUserIdOrderByCreatedAtDescIdDesc` | Últimos 200 gastos de una persona |
| `FriendshipRequestRepository` | `existsBySenderIdAndRecipientIdAndStatus` | Solicitud pendiente, respetando el sentido |
| `UserRepository` | `findIdsWithAvatar` | Cuáles cuentas tienen foto, en una sola consulta |
| `GroupMemberRepository` | `findActiveMatesAmong` | Cuáles resultados comparten un grupo activo con quien busca |

## Regla de visibilidad del avatar

| Relación entre quien consulta y la cuenta dueña | ¿Ve la foto? |
|---|---|
| Es la misma cuenta | Sí |
| Solicitud `ACCEPTED`, en cualquier sentido | Sí |
| Grupo activo en común | Sí |
| Solicitud `PENDING` enviada por la dueña a quien consulta | Sí (nuevo) |
| Solicitud `PENDING` enviada por quien consulta a la dueña | No |
| Solicitud `REJECTED` o ninguna relación | No |

## Altas al crear un grupo

Cada miembro adicional genera una fila en `group_members` o en `simulated_profiles`, y un evento `MEMBER_ADDED` con `atCreation: true`. Las tablas no cambian.

## Datos pendientes

`GroupMemberView` no cambia en esta spec. Los campos que debe sumar para los saldos están en el [contrato](./contracts/ui-refresh-api.md#saldos-por-miembro-pendiente).

## Variables de diseño

No son datos persistidos, pero son el punto de ajuste de la feature.

| Variable | Archivo | Uso |
|---|---|---|
| `--radio-tarjeta` | `index.scss` | Esquinas de tarjetas (8px) |
| `--radio-control` | `index.scss` | Esquinas de controles (6px) |
| `--color-patron` | `detalles.scss` | Color e intensidad del patrón, por tema |
| `--color-naranja`, `--color-naranja-claro` | `detalles.scss` | Hover, presionado y detalles |
| `--color-sobre-naranja` | `detalles.scss` | Texto sobre naranja |
| `--color-turquesa` | `detalles.scss` | Base del patrón |
