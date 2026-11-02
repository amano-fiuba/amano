# Contrato REST: Lista de amigos

**Feature**: `013-friends-list` | **Spec**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md)

## `GET /api/users/me/friends`

Devuelve las amistades de la persona autenticada (FR-001..FR-005).

- **Autenticación**: cookie `amano_auth` de una cuenta verificada (regla de 003, heredada de `/api/users/**`).
- **Parámetros**: ninguno. El usuario sale de la sesión (FR-014).
- **Cuerpo**: ninguno.

### Respuesta 200

Arreglo ordenado por nombre y apellido (sin distinguir mayúsculas, apellido vacío al final, id como desempate). Vacío si no hay amistades.

```json
[
  {
    "id": 12,
    "firstName": "Ana",
    "lastName": "Pérez",
    "email": "ana@example.com",
    "friendsSince": "2026-10-31T14:05:00Z"
  },
  {
    "id": 8,
    "firstName": "Bruno",
    "lastName": null,
    "email": "bruno@example.com",
    "friendsSince": "2026-10-30T09:00:00Z"
  }
]
```

Nunca incluye `cbu`, `cbuCvu`, `currencies`, `passwordHash` ni otros datos de la cuenta (FR-004).

### Respuesta 401 — sin sesión o cuenta no verificada

```json
{
  "type": "about:blank",
  "title": "No autorizado",
  "status": 401,
  "detail": "Necesitas iniciar sesión para continuar"
}
```

## Contrato del frontend

`frontend/src/api/friendsClient.js`, misma convención que el resto del cliente (nunca lanza):

- **`listFriends()`**: `GET /api/users/me/friends` con `credentials: 'include'`.
  - 2xx → `{ ok: true, friends }`.
  - 4xx → `{ ok: false, status, detail }`.
  - 5xx o fallo de red → `{ ok: false, network: true }`.

Mensajes fijos:

| Constante | Texto |
|-----------|-------|
| `FRIENDS_NETWORK_ERROR_MESSAGE` | "No se pudo cargar tu lista de amigos. Intentá nuevamente" |
| `NO_FRIENDS_MESSAGE` (en `FriendsList.jsx`) | "Todavía no tenés amigos" |

## Compatibilidad

- No cambia ningún endpoint de 008, 011 ni 012.
- Endpoint nuevo, aditivo.
