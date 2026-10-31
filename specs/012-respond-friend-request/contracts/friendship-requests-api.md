# Contract: Friendship Requests API (respuesta)

**Feature**: `012-respond-friend-request` | **Spec**: [spec.md](../spec.md) | **Plan**: [plan.md](./plan.md)

Este contrato **extiende** el de 011 ([`specs/011-send-friend-request/contracts/friendship-requests-api.md`](../../011-send-friend-request/contracts/friendship-requests-api.md)): los endpoints `POST /api/users/me/friendship-requests` y `GET /api/users/me/friendship-requests/received` no cambian. Se agrega una única operación de respuesta.

La operación autentica con la cookie `amano_auth` (definida en `001-user-registration`) y vive bajo `/api/users/**`, por lo que hereda la regla de cuenta **verificada** de `003` aplicada por `SecurityConfig`; no se modifica la configuración de seguridad. Formato de errores: RFC 7807 ProblemDetail (`application/problem+json`), reutilizando `ProblemDetailExceptionHandler`. El frontend lo consume por el proxy unificado `/api/*`.

## `PUT /api/users/me/friendship-requests/{id}`

Registra la respuesta de la persona destinataria sobre una solicitud pendiente: aceptación (`ACCEPTED`) o rechazo (`REJECTED`). El respondedor se identifica **exclusivamente** por el principal de la sesión; el id de la solicitud sale de la ruta y la acción del cuerpo.

### Autorización

- Requiere sesión válida de una cuenta **verificada**.
- Sin cookie, cookie inválida/expirada o cuenta no verificada → `401` (regla de `003`).
- El cuerpo nunca lleva ids de respondedor, emisor ni destinataria; solo la acción.

### Parámetro de ruta y cuerpo de la petición

| Parte | Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|---|
| Ruta | `{id}` | number | Sí | Entero positivo; es el id de la solicitud. No numérico → `400` del framework. `<= 0` → `400` con `properties.fields.id`. |
| Cuerpo | `action` | string | Sí | Exactamente `"ACCEPT"` (aceptar) o `"REJECT"` (rechazar); comparación exacta, sin normalización de mayúsculas. Cualquier otro valor → `400` con `properties.fields.action`. |

```json
{ "action": "ACCEPT" }
```

### Respuesta 200 — respuesta registrada

```json
{
  "id": 40,
  "status": "ACCEPTED",
  "createdAt": "2026-10-30T14:05:00Z",
  "respondedAt": "2026-10-30T15:12:00Z",
  "sender": {
    "id": 31,
    "firstName": "Bruno",
    "lastName": null,
    "email": "bruno@example.com"
  }
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | number | Identificador de la solicitud. |
| `status` | string | `ACCEPTED` cuando la acción confirmada es aceptar; `REJECTED` cuando es rechazar. |
| `createdAt` | string (ISO-8601) | Fecha de creación original (sin cambios). |
| `respondedAt` | string (ISO-8601) | Fecha de la respuesta recién registrada. |
| `sender.id` | number | Identificador de la cuenta emisora. |
| `sender.firstName` | string | Nombre del emisor. |
| `sender.lastName` | string \| null | Apellido del emisor; puede ser `null`. |
| `sender.email` | string | Correo del emisor. |

**Nunca** se incluyen CBU/CVU, monedas, contraseña, hash de contraseña, avatar, fecha de verificación ni otros datos privados.

La aceptación deja una fila `ACCEPTED` (la amistad, cuyo listado pertenece a otra historia); el rechazo deja `REJECTED` y no crea relación alguna. En ambos casos la solicitud deja de aparecer en `GET /api/users/me/friendship-requests/received`.

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
      "action": "La acción no es válida"
    }
  }
}
```

Casos:

| Situación | Error |
|---|---|
| `id` de ruta no numérico o `<= 0` | `400` con `properties.fields.id` = "La solicitud no es válida", validado en el servicio **sin consultar ninguna fila** |
| `action` ausente, `null`, vacío o distinto de `ACCEPT`/`REJECT` | `400` con `properties.fields.action` = "La acción no es válida", sin consultar la base |

No se consulta ni modifica ninguna fila en el caso de `id`; para `action` la validación ocurre antes de cualquier consulta.

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

### Respuesta 404 — solicitud inexistente o no dirigida a la persona de la sesión

Mensaje único y neutro: no revela si la solicitud existe, si pertenece a otra persona ni su estado. Cubre: id inexistente, intentar responder la solicitud que uno mismo envió, y solicitud borrada por la cascada de una cuenta eliminada.

```json
{
  "type": "about:blank",
  "title": "Recurso no encontrado",
  "status": 404,
  "detail": "No se encontró la solicitud de amistad"
}
```

No se crea ni modifica ninguna fila.

### Respuesta 409 — solicitud ya respondida

```json
{
  "type": "about:blank",
  "title": "Conflicto",
  "status": 409,
  "detail": "Esta solicitud de amistad ya fue respondida"
}
```

Casos:

| Situación | `detail` |
|---|---|
| La solicitud ya está `ACCEPTED` | "Esta solicitud de amistad ya fue respondida" |
| La solicitud ya está `REJECTED` | "Esta solicitud de amistad ya fue respondida" |
| Doble respuesta concurrente (el `UPDATE` condicional no afectó filas) | "Esta solicitud de amistad ya fue respondida" |

El mensaje es único y no revela el resultado anterior. No se modifica nada: estado y `respondedAt` previos quedan intactos.

### Respuesta 500 — error inesperado

ProblemDetail genérico del manejador global, sin detalles internos. El frontend lo trata como fallo de red con el mensaje fijo de reintento.

---

## Contrato del frontend

### Cliente

`frontend/src/api/friendsClient.js` (creado en 011) agrega una función, con la misma convención (nunca lanza, siempre devuelve un resultado discriminado):

- **`respondFriendshipRequest(id, action)`**: `PUT /api/users/me/friendship-requests/{id}` con
  `credentials: 'include'`, `Content-Type: application/json` y `{ action }`
  (`action` es `"ACCEPT"` o `"REJECT"`).
  - 2xx → `{ ok: true, request }`.
  - 4xx → `{ ok: false, status, detail, fields }` (400, 401, 404, 409).
  - 5xx o fallo de red → `{ ok: false, network: true }`.

Mensaje fijo nuevo del cliente:

- `RESPOND_NETWORK_ERROR_MESSAGE = "No se pudo responder la solicitud. Intentá nuevamente"`

Los mensajes y funciones de 011 no cambian.

### Pantalla "Amigos" (`FriendsPage`)

Extiende la sección "Solicitudes" de 011; el listado, el orden, los datos por fila, el estado vacío y los estados de carga/error/reintento no cambian.

- Cada fila agrega dos botones: "Aceptar" y "Rechazar".
- Mientras una fila responde, **sus** dos botones quedan deshabilitados y muestran "Aceptando…" o "Rechazando…"; no se alteran las demás filas ni se permiten dos acciones simultáneas sobre la misma fila.
- Con éxito (`status` `ACCEPTED`/`REJECTED`): la fila se retira de la lista y aparece la confirmación exacta "Solicitud de amistad aceptada" o "Solicitud de amistad rechazada". Si la lista queda vacía, la confirmación permanece visible; el texto "No tenés solicitudes de amistad pendientes" corresponde solo a la carga inicial sin pendientes.
- Con 409 o 404: la fila se retira (ya no es ni estará pendiente) y se muestra el `detail` del servidor ("Esta solicitud de amistad ya fue respondida" / "No se encontró la solicitud de amistad").
- Con error de red o del servidor: se muestra exactamente "No se pudo responder la solicitud. Intentá nuevamente" y la fila conserva sus botones reactivos; nunca se marca la acción como hecha ni se retira la fila.
- Con 401: se limpia la sesión (`setUser(null)`) y `ProtectedRoute` redirige a `/login`.
- No se muestran listas de amigos ni botones que 012 dejó fuera de alcance.
