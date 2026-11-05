# Contrato REST: Eliminar amigo

**Feature**: `016-delete-friend` | **Spec**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md)

Todos los endpoints viven bajo `/api/users/**`, por lo que heredan la regla de sesión verificada de 003. La persona que elimina sale siempre de la sesión (`amano_auth`); el amigo sale de la ruta.

## `GET /api/users/me/friends/{friendId}/deletion-eligibility`

Consulta si la persona autenticada puede eliminar a `friendId` (FR-003, FR-022).

- **Autenticación**: cookie `amano_auth` de una cuenta verificada.
- **Parámetros de ruta**: `friendId` (entero positivo).
- **Cuerpo**: ninguno.

### Respuesta 200 — puede eliminar

```json
{
  "canDelete": true,
  "reason": null
}
```

### Respuesta 200 — bloqueado por deuda directa

```json
{
  "canDelete": false,
  "reason": "No podés eliminar a este amigo: tenés una deuda pendiente. Saldá la deuda e intentá nuevamente."
}
```

La UI muestra `reason` y no abre el diálogo de confirmación (FR-004).

### Respuesta 400 — identificador inválido

No numérico, vacío, cero o negativo. Se valida antes de consultar la base (FR-024).

```json
{
  "type": "about:blank",
  "title": "Validación fallida",
  "status": 400,
  "detail": "La solicitud contiene campos inválidos",
  "properties": {
    "fields": {
      "friendId": "El identificador del amigo no es válido"
    }
  }
}
```

### Respuesta 401 — sin sesión o cuenta no verificada

```json
{
  "type": "about:blank",
  "title": "No autorizado",
  "status": 401,
  "detail": "Necesitas iniciar sesión para continuar"
}
```

### Respuesta 404 — no es amigo

Amistad inexistente, ajena, ya eliminada o solo solicitudes `PENDING`/`REJECTED`. Mensaje neutro, sin revelar otras relaciones (FR-025).

```json
{
  "type": "about:blank",
  "title": "Recurso no encontrado",
  "status": 404,
  "detail": "No sos amigo de ese usuario"
}
```

## `DELETE /api/users/me/friends/{friendId}`

Elimina físicamente la amistad `ACCEPTED` entre la sesión y `friendId` (FR-023).

- **Autenticación**: cookie `amano_auth` de una cuenta verificada.
- **Parámetros de ruta**: `friendId` (entero positivo).
- **Cuerpo**: ninguno.

### Respuesta 204 — eliminado

Sin cuerpo. La fila `ACCEPTED` ya no existe y ambos usuarios dejan de verse como amigos (FR-007).

### Respuesta 400 — identificador inválido

Mismo formato que la consulta de elegibilidad (FR-024).

### Respuesta 401 — sin sesión o cuenta no verificada

Mismo formato que la consulta de elegibilidad (FR-021).

### Respuesta 403 — deuda directa pendiente

La amistad no se modifica (FR-012/FR-013). Código de máquina `PENDING_DIRECT_DEBT`, coherente con las deudas pendientes de grupos (010).

```json
{
  "type": "about:blank",
  "title": "Operación bloqueada por deuda pendiente",
  "status": 403,
  "detail": "No podés eliminar a este amigo: tenés una deuda pendiente. Saldá la deuda e intentá nuevamente.",
  "properties": {
    "code": "PENDING_DIRECT_DEBT"
  }
}
```

### Respuesta 404 — no es amigo

Amistad inexistente, ajena o ya eliminada; también cubre la eliminación concurrente que pierde la carrera (FR-025/FR-030).

```json
{
  "type": "about:blank",
  "title": "Recurso no encontrado",
  "status": 404,
  "detail": "No sos amigo de ese usuario"
}
```

## Mensajes de la interfaz

| Constante | Texto exacto |
|-----------|--------------|
| `DELETE_FRIEND_LABEL` | "Eliminar amigo" |
| `DELETE_DIALOG_TITLE` | "Eliminar amigo" |
| `DELETE_DIALOG_MESSAGE` | "¿Querés eliminar a {nombre} de tus amigos?" |
| `DELETE_CANCEL_LABEL` | "Cancelar" |
| `DELETE_CONFIRM_LABEL` | "Eliminar" |
| `DELETING_LABEL` | "Eliminando…" |
| `DELETE_SUCCESS_MESSAGE` | "Amigo eliminado correctamente" |
| `DELETE_NETWORK_ERROR_MESSAGE` | "No se pudo eliminar al amigo. Intentá nuevamente" |
| `NOT_FRIEND_MESSAGE` | "No sos amigo de ese usuario" |
| `PENDING_DIRECT_DEBT_MESSAGE` | "No podés eliminar a este amigo: tenés una deuda pendiente. Saldá la deuda e intentá nuevamente." |

## Contrato del frontend

`frontend/src/api/friendsClient.js`, misma convención que el resto del cliente (nunca lanza):

- **`checkFriendDeletion(friendId)`**: `GET /api/users/me/friends/{friendId}/deletion-eligibility` con `credentials: 'include'`.
  - 2xx → `{ ok: true, canDelete, reason }`.
  - 4xx → `{ ok: false, status, detail }`.
  - 5xx o fallo de red → `{ ok: false, network: true }`.
- **`deleteFriend(friendId)`**: `DELETE /api/users/me/friends/{friendId}` con `credentials: 'include'`.
  - 204 (o cualquier 2xx) → `{ ok: true }`.
  - 4xx → `{ ok: false, status, detail }`.
  - 5xx o fallo de red → `{ ok: false, network: true }`.

## Comportamiento de la interfaz

| Situación | Resultado |
|-----------|-----------|
| Elegibilidad `canDelete: true` | Abre el diálogo de confirmación |
| Elegibilidad `canDelete: false` | Muestra `reason`; no abre el diálogo |
| "Cancelar" | Cierra el diálogo sin petición |
| `DELETE` 204 | Retira la tarjeta y muestra `DELETE_SUCCESS_MESSAGE` |
| `DELETE` 403 | Cierra el diálogo, muestra el motivo y conserva la tarjeta |
| `DELETE` 404 | Retira la tarjeta y muestra `NOT_FRIEND_MESSAGE` |
| Error de red / 5xx | Mantiene la tarjeta, muestra `DELETE_NETWORK_ERROR_MESSAGE` y permite reintentar |
| `401` | Cierra la sesión local y redirige al acceso |

## Compatibilidad

- No cambia `GET /api/users/me/friends` de 013.
- No cambia ningún endpoint de 008, 010, 011 ni 012.
- Endpoints nuevos, aditivos.
- No modifica el esquema de base de datos.
