# Contrato: Extensión de grupos y buscador (021)

Autenticación: sesión verificada. Errores en `ProblemDetail` (RFC 7807).

## `GET /api/users/search` (008, cambio aditivo)

```json
[{ "id": 3, "firstName": "Ana", "lastName": "Pérez", "email": "ana@example.com", "friendshipStatus": "PENDING_RECEIVED" }]
```

`friendshipStatus`: `NONE`, `PENDING_SENT`, `PENDING_RECEIVED` o `FRIENDS`.

## `PUT /api/groups/{id}` (010, cambia la autorización)

| Campos enviados | Permitido a | 403 `detail` |
|---|---|---|
| `permissions` | Solo ADMIN | "Solo un administrador puede editar los permisos del grupo" |
| `name`, `description` | ADMIN, o MEMBER con casilla | "No podés editar los datos de este grupo" |

`permissions` acepta `membersCanEditGroupData`. La respuesta y `GET /api/groups/{id}` lo incluyen.

**Cambio no aditivo**: `membersCannotEditSettings` deja de existir. No aparece en las respuestas y, si se envía, se ignora. El frontend del repo ya no lo usa.

## `DELETE /api/groups/{id}/photo` (nuevo)

Sin cuerpo.

| Código | Caso |
|---|---|
| 204 | Foto eliminada, o el grupo no tenía foto |
| 401 | Sin sesión |
| 403 | Sin permiso: "No tenés permiso para cambiar la foto del grupo" |
| 404 | Grupo inexistente o sin membresía activa |

## `POST /api/groups/{id}/photo` (010, cambia la autorización)

Permitido también a un MEMBER con la casilla activa. Sin permiso responde 403 con el mismo `detail` que `DELETE`.
