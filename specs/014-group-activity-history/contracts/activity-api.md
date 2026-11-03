# Contrato REST: Historial de actividad del grupo

**Feature**: `014-group-activity-history` | **Spec**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md)

Reemplaza a la sección "Contract: Group Activity History" de `specs/010-groups-management/contracts/api-contracts.md`, con cambios compatibles.

## `GET /api/groups/{id}/activity`

- **Autenticación**: cookie `amano_auth`.
- **Acceso**: miembros activos del grupo (010). Otros: 404 neutral.

### Parámetros

| Parámetro | Tipo | Defecto | Notas |
|-----------|------|---------|-------|
| `page` | int, desde 1 | 1 | Valores < 1 se tratan como 1 |
| `size` | int | 20 | Valores < 1 → 20; > 100 → 100 (FR-011) |
| `eventType` | texto | — | Uno o más tipos separados por coma, sin distinguir mayúsculas (FR-010) |

Tipos válidos: `MEMBER_ADDED`, `MEMBER_REMOVED`, `MEMBER_LEFT`, `PERMISSION_CHANGED`, `PHOTO_CHANGED`, `EXPENSE_CREATED`, `PAYMENT_MADE`.

### Respuesta 200

```json
{
  "content": [
    {
      "id": 31,
      "eventType": "PAYMENT_MADE",
      "actor": { "id": 7, "firstName": "Ana", "lastName": "López" },
      "details": {
        "paymentId": 3,
        "amount": "500.00",
        "currency": "ARS",
        "fromName": "Juan Pérez",
        "toName": "Ana López"
      },
      "createdAt": "2026-11-03T15:20:00Z"
    },
    {
      "id": 30,
      "eventType": "MEMBER_ADDED",
      "actor": { "id": 7, "firstName": "Ana", "lastName": "López" },
      "details": {
        "type": "REGISTERED",
        "userId": 15,
        "name": "Juan Pérez",
        "email": "juan@example.com",
        "atCreation": true
      },
      "createdAt": "2026-11-03T15:00:00Z"
    }
  ],
  "totalElements": 2,
  "totalPages": 1,
  "page": 1,
  "size": 20
}
```

- `details` es un **objeto JSON** (antes, texto). Formatos por tipo en [data-model.md](../data-model.md). `null` si no hay detalles válidos.
- `actor` es `null` si la cuenta no existe.

### Respuesta 400 — tipo de evento inexistente

```json
{
  "type": "about:blank",
  "title": "Validación fallida",
  "status": 400,
  "detail": "La solicitud contiene campos inválidos",
  "properties": { "fields": { "eventType": "El tipo de evento no es válido" } }
}
```

### Respuesta 401 / 404

Sin cambios respecto de 010: 401 sin sesión; 404 "No existe el grupo solicitado o no sos miembro".

## Contrato interno: enganche para gastos y pagos

`com.amano.backend.groups.ActivityEventService`, siempre dentro de una transacción existente (`Propagation.MANDATORY`):

```java
GroupActivity recordExpenseCreated(Long groupId, Long actorUserId, ExpenseActivityDetails details);
GroupActivity recordPaymentMade(Long groupId, Long actorUserId, PaymentActivityDetails details);
```

- `IllegalArgumentException` si `details` es `null`, `amount` es `null` o ≤ 0, o `currency` está vacía.
- La moneda se guarda en mayúsculas y sin espacios.

## Contrato del frontend

`frontend/src/api/groupsClient.js` (nunca lanza):

- **`getActivity(groupId, { page, size, eventType, eventTypes })`**: `GET /api/groups/{id}/activity` con `credentials: 'include'`. `eventTypes` (arreglo) se envía como `eventType=A,B`.
  - 2xx → `{ ok: true, content, totalElements, totalPages, page, size }`.
  - 4xx → `{ ok: false, status, detail }`.
  - 5xx o red → `{ ok: false, network: true }`.

`frontend/src/pages/groups/activityDescriptions.js`:

- **`describeActivity(entry)`** → `{ actor, text }`.

| Tipo | Frase (`text`) |
|------|----------------|
| `MEMBER_ADDED` registrado | "agregó a {name}" (+ " al crear el grupo" si `atCreation`) |
| `MEMBER_ADDED` simulado | "agregó el perfil simulado {name}" (+ " al crear el grupo") |
| `MEMBER_REMOVED` registrado | "quitó a {name} del grupo" |
| `MEMBER_REMOVED` simulado | "quitó el perfil simulado {name}" |
| `MEMBER_LEFT` | "abandonó el grupo" |
| `PERMISSION_CHANGED` | "cambió los permisos del grupo" |
| `PHOTO_CHANGED` | "cambió la foto del grupo" |
| `EXPENSE_CREATED` | "registró un gasto de {monto}" (+ " «{description}»") |
| `PAYMENT_MADE` | "registró un pago de {monto}" (+ " de {fromName}") (+ " a {toName}") |
| Desconocido | "realizó una acción en el grupo" |

Sin nombre: "agregó a un miembro", "quitó a un miembro del grupo", "registró un gasto", "registró un pago". Actor ausente: "Alguien".

Filtro "Mostrar":

| Opción | `eventTypes` |
|--------|--------------|
| Toda la actividad | — |
| Altas | `MEMBER_ADDED` |
| Bajas | `MEMBER_REMOVED`, `MEMBER_LEFT` |
| Gastos | `EXPENSE_CREATED` |
| Pagos | `PAYMENT_MADE` |
| Cambios del grupo | `PERMISSION_CHANGED`, `PHOTO_CHANGED` |

Mensajes fijos:

| Texto | Cuándo |
|-------|--------|
| "Todavía no hay actividad en este grupo" | Vacío sin filtro |
| "No hay actividad de este tipo en el grupo" | Vacío con filtro |
| "No se pudo cargar el historial. Intentá nuevamente" | Red o 5xx, con "Reintentar" |
| "No existe el grupo solicitado o no sos miembro" | 404 o 403 sin detalle |

## Compatibilidad

- Un solo `eventType` funciona igual que en 010.
- `details` cambia de texto a objeto; ningún consumidor lo usaba.
- Un `eventType` inexistente pasa de lista vacía a 400.
