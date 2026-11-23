# Contrato: API de notificaciones y preferencias

**Feature**: [spec.md](../spec.md) · **Plan**: [plan.md](../plan.md)

**Prefijo**: `/api/v1/notifications` (exigido literalmente por el backlog; el resto del proyecto usa `/api/...` sin versión — ver decisión D2 del plan).

**Autenticación**: cookie de sesión `amano_auth`. Todas las operaciones exigen cuenta verificada. El usuario se toma siempre del `SecurityContext`; cualquier parámetro `userId` enviado por el cliente se ignora.

**Formato de errores**: `ProblemDetail` (RFC 9457) del manejo existente (`ProblemDetailExceptionHandler`), con `detail` en español y `properties.fields` cuando corresponda. Ningún error expone identifiers ajenos ni detalles internos.

---

## Modelo de datos transferidos

### `NotificationResponse`

```json
{
  "id": 123,
  "type": "GROUP_EXPENSE",
  "title": "Nuevo gasto en Viaje de verano",
  "message": "Ana registró un gasto en el grupo.",
  "createdAt": "2026-11-23T10:15:00Z",
  "readAt": null,
  "target": {
    "kind": "GROUP",
    "groupId": 42
  }
}
```

| Campo | Tipo | Observaciones |
|-------|------|---------------|
| `id` | `number` | Identificador de la notificación |
| `type` | `string` | Uno de `REMINDER`, `DEBT_WHISTLE`, `DEBT_OVERDUE`, `PAYER_DUE_NOTICE`, `GROUP_EXPENSE`, `PAYMENT_RECORDED`, `FRIEND_REQUEST`, `FRIEND_ACTIVITY` |
| `title` | `string` | Título breve en español |
| `message` | `string` | Texto descriptivo en español |
| `createdAt` | `string` | ISO-8601 con zona horaria; momento de generación |
| `readAt` | `string \| null` | ISO-8601 con zona horaria; `null` mientras esté no leída |
| `target.kind` | `string` | `GROUP`, `DEBT`, `FRIEND_REQUEST` o `NONE` |
| `target.groupId` | `number?` | Presente en `GROUP` y en `DEBT` de grupo |
| `target.debtId` | `number?` | Presente en `DEBT` |
| `target.expenseId` | `number?` | Presente cuando el aviso viene de un gasto grupal |
| `target.friendRequestId` | `number?` | Presente en `FRIEND_REQUEST` |

La interfaz **no interpreta el texto**: resuelve el destino a partir de `target`. Un `target` con campos faltantes o inválidos se trata como `NONE`.

### `NotificationPreferencesResponse` / `NotificationPreferencesRequest`

```json
{
  "channels": {
    "inApp": true,
    "email": true
  },
  "eventTypes": {
    "remindersAndWhistles": true,
    "debtDueAndPendingCollections": true,
    "groupActivity": true,
    "friendActivity": true
  }
}
```

Los seis campos (dos canales + cuatro tipos de evento) son booleanos obligatorios en `PUT`. Si no existe configuración para la persona, `GET` crea y devuelve los valores por defecto (`true` en todos).

Correspondencia entre tipos de notificación y preferencias:

| Preferencia | Tipos que controla |
|-------------|--------------------|
| `remindersAndWhistles` | `REMINDER`, `DEBT_WHISTLE` |
| `debtDueAndPendingCollections` | `DEBT_OVERDUE`, `PAYER_DUE_NOTICE` |
| `groupActivity` | `GROUP_EXPENSE`, `PAYMENT_RECORDED` |
| `friendActivity` | `FRIEND_REQUEST`, `FRIEND_ACTIVITY` |

---

## Endpoints

### 1. Listado

```http
GET /api/v1/notifications?limit=20&before=<cursor>&order=history
```

| Parámetro | Obligatorio | Valores | Default |
|-----------|-------------|---------|---------|
| `limit` | No | 1–100 | `20` |
| `before` | No | Cursor opaco devuelto por `nextCursor` | — (primera página) |
| `order` | No | `history`, `unread-first` | `history` |

- `history`: `createdAt DESC, id DESC` (bandeja).
- `unread-first`: `readAt IS NULL DESC, createdAt DESC, id DESC` (popover, con `limit=5`).

**200**:

```json
{
  "notifications": [ /* NotificationResponse[] */ ],
  "unreadCount": 4,
  "nextCursor": "MTcwMDczMzYwMDAwfDxEyMw=="
}
```

`unreadCount` es el total de no leídas del usuario, **independiente del `limit`**: con él se pinta el badge del encabezado. `nextCursor` es `null` cuando no hay más páginas. Un `limit` fuera de rango se normaliza al rango permitido (no produce error). Un cursor inválido se trata como "primera página". El parámetro `before` se ignora cuando se envía `order=unread-first`.

**401**: sin sesión o cuenta no verificada.

---

### 2. Marcar una como leída

```http
PATCH /api/v1/notifications/{id}/read
```

**200**: devuelve el `NotificationResponse` actualizado. Operación idempotente: si ya estaba leída, devuelve el mismo objeto sin cambios.

**404**: el identificador no existe o pertenece a otra persona. Mismo `ProblemDetail` para ambos casos, sin distinguirlos.

---

### 3. Marcar todas como leídas

```http
PATCH /api/v1/notifications/read-all
```

**200**:

```json
{ "updated": 4 }
```

`updated` es la cantidad de notificaciones que cambiaron de estado en esta llamada (0 si ya no había no leídas). Solo afecta al usuario autenticado.

---

### 4. Obtener preferencias

```http
GET /api/v1/notifications/preferences
```

**200**: `NotificationPreferencesResponse` con los valores vigentes (crea los valores por defecto si aún no existen). **401**: sin sesión o cuenta no verificada.

---

### 5. Actualizar preferencias

```http
PUT /api/v1/notifications/preferences
Content-Type: application/json
```

Cuerpo: `NotificationPreferencesRequest` completo (todos los campos obligatorios).

**200**: preferencias guardadas.

**400**: cuerpo ausente, campo faltante o tipo inválido; `ProblemDetail` con `properties.fields` indicando los nombres de los campos. **401**: sin sesión o cuenta no verificada. No se acepta `userId` en el cuerpo: se ignora.

---

## Generación de notificaciones (interno, no REST)

Los eventos de negocio existentes publican un evento interno que se procesa **después de confirmar la transacción de negocio**:

1. Se consultan las preferencias del destinatario.
2. Si el tipo de evento está desactivado, no se hace nada.
3. Si el canal In-App está activo, se inserta la fila en `notifications`.
4. Si el canal de correo está activo **y** el evento trae `emailPolicy = SUBSCRIBABLE`, se envía el correo de forma asíncrona. Los eventos `REMINDER` y `DEBT_WHISTLE` traen `emailPolicy = NONE`: esos flujos ya envían su correo de negocio con sus propias reglas (025/030) y no se envía una segunda copia.

Un fallo en el paso 3 o 4 se captura y se registra; **nunca** propaga a la operación de negocio ni la revierte. Los eventos originadores son: recordatorios y silbatos de deuda, vencimiento/aviso de cobro, alta de gasto grupal, pago registrado, solicitud de amistad y actividad social. El interruptor de correo de las preferencias gobierna solo los correos de notificación nuevos, no los correos de negocio existentes (verificación, recuperación, recordatorios y silbatos). Los textos y asuntos son los ya definidos en 025 y 030 cuando existen; para los demás se usa texto en español y voseo consistente con el resto de la aplicación.
