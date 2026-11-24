# Data Model: Bandeja completa y configuración de notificaciones in-app

**Feature**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md) · **Contrato**: [contracts/notifications-api.md](./contracts/notifications-api.md) · **Research**: [research.md](./research.md)

## Visión general

```text
users (existente)
   ├── 1 ──< notifications            (historial por persona)
   └── 1 ── 1 user_notification_preferences  (configuración por persona)

notifications.target_*  -> ids planas (sin FK) a groups / debts / group_expenses / friendship_requests
```

Las entidades de destino pueden desaparecer: los campos `group_id`, `debt_id`, `expense_id` y `friend_request_id` se conservan como datos históricos, sin foreign key. La interfaz decide el destino con `NotificationResponse.target` (ver contrato), nunca con el texto del mensaje (FR-019, FR-020, SC-005).

---

## `notifications` (tabla nueva)

| Columna | Tipo | Nulo | Default | Descripción |
|---------|------|------|---------|-------------|
| `id` | `BIGINT GENERATED ALWAYS AS IDENTITY` | no | — | PK |
| `user_id` | `BIGINT` | no | — | FK a `users(id)` `ON DELETE CASCADE`; destinatario |
| `type` | `VARCHAR(40)` | no | — | `NotificationType` (ver enums) |
| `title` | `VARCHAR(150)` | no | — | Título breve en español |
| `message` | `TEXT` | no | — | Texto descriptivo en español |
| `target_kind` | `VARCHAR(20)` | no | `'NONE'` | `NotificationTargetKind` |
| `group_id` | `BIGINT` | sí | — | Grupo destino, si aplica (sin FK) |
| `debt_id` | `BIGINT` | sí | — | Deuda destino, si aplica (sin FK) |
| `expense_id` | `BIGINT` | sí | — | Gasto grupal de origen, si aplica (sin FK) |
| `friend_request_id` | `BIGINT` | sí | — | Solicitud de amistad de origen, si aplica (sin FK) |
| `created_at` | `TIMESTAMPTZ` | no | `now()` | Momento de generación |
| `read_at` | `TIMESTAMPTZ` | sí | `NULL` | `NULL` = no leída |

**Índices**
- `ix_notifications_user_created ON notifications (user_id, created_at DESC, id DESC)` — historial y popover.
- `ix_notifications_user_unread ON notifications (user_id) WHERE read_at IS NULL` — contador del badge y orden `unread-first`.

**Restricciones / reglas de negocio**
- Solo se inserta si el tipo de evento está activo en las preferencias del destinatario y el canal In-App está activo (FR-025, FR-026).
- `read_at` solo pasa de `NULL` a una marca de tiempo; nunca vuelve a `NULL` (no hay "desmarcar", spec Fuera de alcance).
- El marcado individual y global filtran por `user_id`: un id ajeno o inexistente no modifica filas y responde 404 (FR-031).
- No se eliminan filas desde la aplicación: el historial se conserva mientras exista la cuenta; `ON DELETE CASCADE` solo aplica si se borra el usuario.

**Transiciones de estado**

| Estado | `read_at` | Transición permitida | Actor |
|--------|-----------|----------------------|-------|
| No leída | `NULL` | → Leída al marcar individualmente, al marcar todas o al abrir la notificación | Persona autenticada |
| Leída | timestamp | Permanece leída; puede abrirse para navegar sin cambiar de estado | Persona autenticada |

---

## `user_notification_preferences` (tabla nueva)

| Columna | Tipo | Nulo | Default | Descripción |
|---------|------|------|---------|-------------|
| `user_id` | `BIGINT` | no | — | PK y FK a `users(id)` `ON DELETE CASCADE`; una sola fila por persona |
| `in_app` | `BOOLEAN` | no | `TRUE` | Canal In-App (bandeja) |
| `email` | `BOOLEAN` | no | `TRUE` | Canal de correo de notificación |
| `reminders_whistles` | `BOOLEAN` | no | `TRUE` | Recordatorios y silbatos de cobro |
| `debt_due_collections` | `BOOLEAN` | no | `TRUE` | Vencimiento de deudas y cobros pendientes |
| `group_activity` | `BOOLEAN` | no | `TRUE` | Alta de gastos y pagos que involucran a la persona |
| `friend_activity` | `BOOLEAN` | no | `TRUE` | Solicitudes de amistad y actividad social |
| `updated_at` | `TIMESTAMPTZ` | no | `now()` | Última modificación |

**Restricciones / reglas de negocio**
- Si no existe fila, `GET` crea una con todos los booleanos en `true` (FR-025); tampoco hay valores nulos en lectura.
- `PUT` exige los seis booleanos (dos canales + cuatro tipos); campos ausentes o de tipo inválido → 400 con `properties.fields` (FR-026, FR-027, constitución VIII).
- El `user_id` siempre se toma de la sesión: un `userId` enviado por el cliente se ignora (FR-028).
- Los cambios afectan solo a notificaciones generadas **después** del guardado (SC-006); no se aplican retroactivamente al historial.
- El interruptor de correo gobierna solo los correos de notificación nuevos; los correos de negocio existentes no cambian (spec, Clarificaciones; FR-026).

**Tabla de decisión de generación**

| ¿Tipo activo? | ¿In-App activo? | ¿Correo activo? | `emailPolicy` | Resultado |
|---------------|-----------------|-----------------|---------------|-----------|
| No | — | — | — | No se genera nada |
| Sí | Sí | Sí | `SUBSCRIBABLE` | Fila en `notifications` + correo asíncrono |
| Sí | Sí | No | — | Solo fila en `notifications` |
| Sí | No | Sí | `SUBSCRIBABLE` | Solo correo asíncrono |
| Sí | Sí/No | Sí/No | `ALWAYS` | Correo asíncrono aunque el toggle esté apagado; fila si In-App está activo |
| Sí | Sí/No | — | `NONE` | Solo fila (si In-App activo); el correo de negocio ya lo envió el flujo originador |

Un fallo en cualquier rama se captura y registra sin propagar (FR-032).

---

## Enums

### `NotificationType`

| Valor | Preferencia que lo gobierna | Destino por defecto | `emailPolicy` |
|-------|-----------------------------|---------------------|---------------|
| `REMINDER` | `reminders_whistles` | `GROUP` | `NONE` |
| `DEBT_WHISTLE` | `reminders_whistles` | `DEBT` | `NONE` |
| `DEBT_OVERDUE` | `debt_due_collections` | `DEBT` | `ALWAYS` |
| `PAYER_DUE_NOTICE` | `debt_due_collections` | `DEBT` | `NONE` |
| `GROUP_EXPENSE` | `group_activity` | `GROUP` | `SUBSCRIBABLE` |
| `PAYMENT_RECORDED` | `group_activity` | `DEBT` | `SUBSCRIBABLE` |
| `FRIEND_REQUEST` | `friend_activity` | `FRIEND_REQUEST` | `SUBSCRIBABLE` |
| `FRIEND_ACTIVITY` | `friend_activity` | `FRIEND_REQUEST` | `SUBSCRIBABLE` |

Se persisten como `VARCHAR` con `@Enumerated(EnumType.STRING)`, igual que el resto del proyecto.

### `NotificationTargetKind`

| Valor | Campos esperados | Ruta resuelta |
|-------|------------------|---------------|
| `GROUP` | `groupId` | `/groups/{groupId}` |
| `DEBT` | `groupId` (opcional), `debtId` | `/groups/{groupId}/debts?debt={debtId}` o `/activity?debt={debtId}` |
| `FRIEND_REQUEST` | `friendRequestId` (opcional) | `/friends` |
| `NONE` | ninguno | Sección general según el tipo, con aviso de destino no disponible |

---

## DTOs (contrato → código)

| DTO | Campos | Uso |
|-----|--------|-----|
| `NotificationTargetResponse` | `kind`, `groupId?`, `debtId?`, `expenseId?`, `friendRequestId?` | Dentro de `NotificationResponse` |
| `NotificationResponse` | `id`, `type`, `title`, `message`, `createdAt`, `readAt`, `target` | Listado y respuesta de marcado individual |
| `NotificationListResponse` | `notifications[]`, `unreadCount`, `nextCursor` | `GET /api/v1/notifications` |
| `NotificationPreferencesResponse` | `channels{inApp,email}`, `eventTypes{remindersAndWhistles,debtDueAndPendingCollections,groupActivity,friendActivity}` | `GET` y respuesta de `PUT` |
| `NotificationPreferencesRequest` | Los mismos seis booleanos, todos obligatorios | Cuerpo de `PUT` |

`NotificationEvent` (interno, no se persiste): `userId`, `type`, `title`, `message`, `target`, `emailPolicy`.

---

## Relación con entidades existentes

| Entidad existente | Relación | Observación |
|-------------------|----------|-------------|
| `users` | 1:N con `notifications`, 1:1 con preferencias | FK con `ON DELETE CASCADE`; usuario desde `SecurityContext` |
| `groups` / `Debt` / `GroupExpense` / `FriendshipRequest` | Referenciadas por ids planos en `target_*` | Sin FK: la notificación sobrevive a su eliminación |
| `reminders` / `debt_whistles` / `group_activity` | Solo origen del evento | No se modifican; sus reglas (025/030) siguen vigentes |

---

## Trazabilidad

| Regla / campo | Origen |
|---------------|--------|
| `user_notification_preferences` y sus 8 booleanos | FR-024, FR-025, FR-030; backlog |
| `read_at` y marcado idempotente | FR-011, FR-012, FR-014; SC-004 |
| `target_*` sin FK | FR-017, FR-019; SC-005 |
| `unreadCount` | FR-003, FR-008; SC-003 |
| `emailPolicy` | FR-026, FR-032; supuesto de no duplicación (spec) |
| Índices y orden | FR-007, FR-010; SC-001 |
| Aislamiento por `user_id` | FR-028, FR-031; SC-008 |
