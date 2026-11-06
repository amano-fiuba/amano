# Contrato: Renovación de la interfaz (023)

Autenticación: sesión verificada (cookie `amano_auth`). Errores en `ProblemDetail` (RFC 7807).

## `GET /api/users/me/friendship-requests/received` (011, cambio aditivo)

`sender` suma `avatarUrl`. Es nulo si la cuenta no tiene foto.

```json
[{
  "id": 8,
  "status": "PENDING",
  "createdAt": "2026-11-06T18:30:00Z",
  "sender": {
    "id": 4,
    "firstName": "Ana",
    "lastName": "Pérez",
    "email": "ana@example.com",
    "avatarUrl": "/api/users/4/avatar"
  }
}]
```

## `GET /api/users/{id}/avatar` (002, amplía quién puede leer)

| Quién consulta | Resultado |
|---|---|
| La persona dueña | 200 |
| Una cuenta amiga (solicitud `ACCEPTED`, en cualquier sentido) | 200 |
| Una cuenta que comparte un grupo activo | 200 |
| **Nuevo:** quien recibió una solicitud `PENDING` de esa cuenta | 200 |
| Quien envió una solicitud `PENDING` a esa cuenta, sin otra relación | 404 |
| Cualquier otra cuenta | 404 |

Una cuenta sin foto responde 404 en todos los casos.

## `GET /api/users/search` (008, cambio aditivo)

Cada resultado suma `avatarUrl`. Es nulo si la cuenta no tiene foto o si quien busca no puede verla, con la misma regla de `GET /api/users/{id}/avatar`.

```json
[{
  "id": 3,
  "firstName": "Ana",
  "lastName": "Pérez",
  "email": "ana@example.com",
  "friendshipStatus": "FRIENDS",
  "avatarUrl": "/api/users/3/avatar"
}]
```

## `GET /api/expenses` (nuevo)

Sin parámetros. Devuelve los últimos 200 gastos de la persona logueada, de más nuevo a más viejo. Cada elemento tiene la forma de `GET /api/expenses/recent` (`ExpenseResponse`).

```json
[{
  "id": 44,
  "amount": "1500.00",
  "currency": "ARS",
  "date": "2026-11-06",
  "category": "FOOD",
  "type": "ONE_TIME",
  "paymentMethod": "CASH",
  "description": "Almuerzo",
  "createdAt": "2026-11-06T18:30:00Z"
}]
```

| Código | Caso |
|---|---|
| 200 | Lista, vacía si no hay gastos |
| 401 | Sin sesión |

## `POST /api/groups` (010, cambio aditivo)

Suma `members`, una lista de miembros adicionales. Cada elemento tiene la forma de `member`. Para un amigo, `identifier` es su correo, que la pantalla toma de `GET /api/users/me/friends`.

```json
{
  "name": "Viaje",
  "description": "",
  "members": [
    { "memberType": "REGISTERED", "identifier": "ana@example.com" },
    { "memberType": "REGISTERED", "identifier": "bruno@example.com" },
    { "memberType": "SIMULATED", "identifier": "invitado@example.com", "name": "Invitado" }
  ]
}
```

- `member` sigue siendo válido. Si llegan los dos, vale `members`.
- La respuesta no cambia. `membersCount` cuenta a la persona creadora y a todos los miembros adicionales.
- Si cualquier miembro es inválido, no se crea nada.

| Código | Caso | `detail` o campo |
|---|---|---|
| 201 | Grupo creado | — |
| 400 | Sin miembros adicionales | `member`: "Agregá al menos un miembro adicional (amigo registrado o perfil simulado)" |
| 400 | Correo de perfil simulado repetido | `member`: "No podés repetir el correo de un perfil simulado" |
| 404 | Alguien no está entre tus amigos | "La persona que querés agregar no está entre tus amigos" |

Los demás errores no cambian (010).

## Saldos por miembro (pendiente)

**Todavía no implementado en el backend.** `GroupMemberView` no tiene estos campos. El frontend ya los lee: `MemberBalance.jsx` está conectado a la lista de miembros y no muestra nada mientras no lleguen.

Para activarlos, cada miembro de `GET /api/groups/{id}/members`, incluidos los perfiles simulados, debe sumar:

```json
{
  "balances": [{ "amount": "500.00", "currency": "ARS" }],
  "payUrl": "/groups/3/expenses/44/debts/7/payment"
}
```

| Campo | Regla |
|---|---|
| `balances` | Lista con un saldo por moneda. Si falta o está vacía, no se muestra nada. |
| `balances[].amount` | Positivo: "Te debe". Negativo: "Le debés". Cero: no se muestra. |
| `balances[].currency` | Código de moneda. Con conversión alcanza con enviar un solo saldo. |
| `payUrl` | Opcional. Si viene, se muestra el botón "Pagar". |

El cálculo, incluidos saldos cruzados y cotizaciones, corresponde al backend. A qué pantalla lleva `payUrl` cuando un saldo neto agrupa varias deudas queda por definir (spec.md §Fuera de alcance).
