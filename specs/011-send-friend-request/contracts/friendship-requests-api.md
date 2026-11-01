# Contract: Friendship Requests API

**Feature**: `011-send-friend-request` | **Spec**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md)

Endpoints autenticados con la cookie `amano_auth` (definida en `001-user-registration`).
Formato de errores: RFC 7807 ProblemDetail (`application/problem+json`), reutilizando
`ProblemDetailExceptionHandler`. El frontend los consume por el proxy unificado `/api/*`.

Ambos endpoints viven bajo `/api/users/**`, por lo que heredan la regla de cuenta
**verificada** de `003` aplicada por `SecurityConfig`; no se modifica la configuración de
seguridad.

## `POST /api/users/me/friendship-requests`

Crea una solicitud de amistad dirigida a otra cuenta existente y verificada. El emisor se
identifica **exclusivamente** por el principal de la sesión.

### Autorización

- Requiere sesión válida de una cuenta **verificada**.
- Sin cookie, cookie inválida/expirada o cuenta no verificada → `401` (regla de `003`).
- El cuerpo nunca lleva el identificador del emisor.

### Cuerpo de la petición

```json
{ "recipientId": 12 }
```

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `recipientId` | number | Sí | Entero positivo; distinto del usuario de la sesión; la cuenta debe existir y estar verificada. |

### Respuesta 201 — solicitud creada

```json
{
  "id": 40,
  "status": "PENDING",
  "createdAt": "2026-10-30T14:05:00Z",
  "recipient": {
    "id": 12,
    "firstName": "Ana",
    "lastName": "Pérez",
    "email": "ana.perez@example.com"
  }
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | number | Identificador de la solicitud. |
| `status` | string | Siempre `PENDING` en esta feature. |
| `createdAt` | string (ISO-8601) | Fecha de creación. |
| `recipient.id` | number | Identificador de la cuenta destinataria. |
| `recipient.firstName` | string | Nombre del destinatario. |
| `recipient.lastName` | string \| null | Apellido del destinatario; puede ser `null`. |
| `recipient.email` | string | Correo del destinatario. |

**Nunca** se incluyen CBU/CVU, monedas, contraseña, hash de contraseña, avatar, fecha de
verificación ni otros datos privados.

### Respuesta 400 — datos inválidos

Se usa `FieldValidationException`; el cuerpo sigue el formato de errores del proyecto.

```json
{
  "type": "about:blank",
  "title": "Validación fallida",
  "status": 400,
  "detail": "La solicitud contiene campos inválidos",
  "properties": {
    "fields": {
      "recipientId": "El destinatario es obligatorio"
    }
  }
}
```

Casos:

| Situación | Mensaje en `properties.fields.recipientId` |
|---|---|
| `recipientId` ausente, `null`, no numérico o `<= 0` | "El destinatario es obligatorio" o "El destinatario no es válido" |
| `recipientId` igual al usuario de la sesión | "No podés enviarte una solicitud de amistad a vos mismo" |

No se crea ninguna fila.

### Respuesta 401 — sin sesión o cuenta no verificada

Mismo cuerpo que el resto del ámbito protegido, definido por `001`/`003`:

```json
{
  "type": "about:blank",
  "title": "No autorizado",
  "status": 401,
  "detail": "Necesitas iniciar sesión para continuar"
}
```

### Respuesta 404 — destinatario inexistente o no verificado

Mensaje neutro: no revela si la cuenta existe.

```json
{
  "type": "about:blank",
  "title": "Recurso no encontrado",
  "status": 404,
  "detail": "No se encontró el usuario"
}
```

### Respuesta 409 — conflicto con el estado actual

```json
{
  "type": "about:blank",
  "title": "Conflicto",
  "status": 409,
  "detail": "Ya existe una solicitud de amistad pendiente entre ambos usuarios"
}
```

Casos:

| Situación | `detail` |
|---|---|
| Ya hay una solicitud `PENDING` entre ambos (misma dirección o inversa) | "Ya existe una solicitud de amistad pendiente entre ambos usuarios" |
| Ya existe una amistad (solicitud `ACCEPTED`) | "Ya son amigos" |
| Carrera de dos envíos concurrentes detectada por el índice único | "Ya existe una solicitud de amistad pendiente entre ambos usuarios" |

No se crea ni modifica ninguna fila.

### Respuesta 500 — error inesperado

ProblemDetail genérico del manejador global, sin detalles internos. El frontend lo trata
como fallo de red con el mensaje fijo de reintento.

---

## `GET /api/users/me/friendship-requests/received`

Devuelve las solicitudes **pendientes recibidas** por la persona autenticada. Es la
"notificación" in-app de 011.

### Autorización

- Requiere sesión válida de una cuenta **verificada**; en caso contrario → `401`.
- El destinatario se identifica por la sesión; no se acepta un id por URL ni por query.

### Respuesta 200

Arreglo JSON con las solicitudes `PENDING` cuyo `recipient_id` es el de la sesión, ordenadas
por `createdAt` descendente y, a igual fecha, por `id` descendente. Sin paginación en 011.

```json
[
  {
    "id": 41,
    "status": "PENDING",
    "createdAt": "2026-10-30T14:06:00Z",
    "sender": {
      "id": 31,
      "firstName": "Bruno",
      "lastName": null,
      "email": "bruno@example.com"
    }
  }
]
```

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | number | Identificador de la solicitud. |
| `status` | string | Siempre `PENDING` en esta feature. |
| `createdAt` | string (ISO-8601) | Fecha de creación. |
| `sender.id` | number | Identificador de la cuenta emisora. |
| `sender.firstName` | string | Nombre del emisor. |
| `sender.lastName` | string \| null | Apellido del emisor; puede ser `null`. |
| `sender.email` | string | Correo del emisor. |

Sin solicitudes pendientes → `200` con `[]`. El mensaje "No tenés solicitudes de amistad
pendientes" es responsabilidad de la interfaz.

### Respuesta 401 / 500

Mismos cuerpos que `POST` (401 sin sesión o no verificada; 500 genérico).

---

## Contrato del frontend

### Cliente

`frontend/src/api/friendsClient.js`, con la convención de `authClient`/`usersClient` (nunca
lanza, siempre devuelve un resultado discriminado):

- **`sendFriendshipRequest(recipientId)`**: `POST /api/users/me/friendship-requests` con
  `credentials: 'include'`, `Content-Type: application/json` y
  `{ recipientId }`.
  - 2xx → `{ ok: true, request }`.
  - 4xx → `{ ok: false, status, detail, fields }` (400, 401, 404, 409).
  - 5xx o fallo de red → `{ ok: false, network: true }`.
- **`listReceivedFriendshipRequests()`**: `GET /api/users/me/friendship-requests/received`
  con `credentials: 'include'`.
  - 2xx → `{ ok: true, requests }`.
  - 4xx → `{ ok: false, status, detail }`.
  - 5xx o fallo de red → `{ ok: false, network: true }`.

Mensajes fijos del cliente:

- `SEND_NETWORK_ERROR_MESSAGE = "No se pudo enviar la solicitud. Intentá nuevamente"`
- `RECEIVED_NETWORK_ERROR_MESSAGE = "No se pudieron cargar tus solicitudes. Intentá nuevamente"`

### Buscador (`UsersSearchPage`)

- El botón "Agregar" de cada resultado queda habilitado y envía la solicitud con el `id` de
  la fila.
- Mientras se envía, esa fila muestra "Enviando…" y el botón queda deshabilitado. Con éxito,
  la fila muestra "Solicitud enviada" deshabilitado y un aviso con "Solicitud de amistad
  enviada".
- Con 409, 400, 404 o error de red se muestra el `detail`/mensaje fijo correspondiente y la
  fila **no** queda marcada como enviada.
- Con 401 se limpia la sesión (`setUser(null)`) y `ProtectedRoute` redirige a `/login`.
- No se modifican el formulario de búsqueda ni la respuesta de `GET /api/users/search`.

### Pantalla "Amigos"

- Ruta protegida `/friends` (`ProtectedRoute`), enlazada desde `SiteNav` como "Amigos".
- Sección "Solicitudes" que carga `listReceivedFriendshipRequests()` al montar.
- Estados:
  - Cargando: indicador de carga.
  - Lista: cada solicitud muestra avatar `/avatar-default.png`, nombre y apellido (si
    `lastName` es `null`, solo el nombre), correo del emisor y la etiqueta "Pendiente".
  - Vacío: exactamente "No tenés solicitudes de amistad pendientes".
  - Error de red/servidor: exactamente "No se pudieron cargar tus solicitudes. Intentá
    nuevamente" con botón "Reintentar".
  - 401: se limpia la sesión y se redirige a `/login`.
- En 011 la pantalla no muestra acciones "Aceptar" ni "Rechazar" ni una lista de amigos; esas
  funciones pertenecen a otra historia.
