# Contratos de API: 015

Ambos endpoints requieren sesión y cuenta verificada (`/api/groups/**`, `/api/expenses/**`).

## `GET /api/groups` (cambio aditivo)

Cada elemento agrega:

| Campo | Tipo | Descripción |
|---|---|---|
| `lastActivityAt` | ISO-8601 con offset | Fecha del evento más reciente en `group_activity`, o `createdAt` si el grupo no tiene eventos. Nunca es nulo. |

El orden de la lista y los campos existentes no cambian. El frontend ordena por este campo.

## `GET /api/expenses/recent`

`200 OK`: arreglo de hasta 5 `ExpenseResponse` (misma forma que 007/009) del usuario de la sesión, ordenados por `createdAt` y luego `id`, ambos descendentes.

| Código | Caso |
|---|---|
| `200` | Lista, vacía si no hay gastos |
| `401` | Sin sesión |
| `403` | Cuenta no verificada |
