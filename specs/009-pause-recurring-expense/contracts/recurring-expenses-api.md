# Contrato: API de gastos recurrentes (009)

**Fecha**: 2026-10-24 | **Spec**: [spec.md](../spec.md)

Cambios sobre `POST /api/expenses` (contrato de 007, `specs/007-register-expense/contracts/expenses-api.md`) y dos endpoints nuevos. Todos requieren una sesión de cuenta **verificada** (`/api/expenses/**`, regla de 003); sin sesión válida responden `401` con el ProblemDetail "No autorizado".

**Convenciones comunes**: éxito `application/json`; error `application/problem+json` (RFC 7807) con `title`, `status`, `detail` y, cuando aplica, `properties`. Nunca se exponen trazas, SQL, ids de otros usuarios ni datos ajenos.

---

## Cambio: `POST /api/expenses`

### Petición — campo nuevo

| Campo | Tipo | Reglas |
|-------|------|--------|
| `frequency` | string | `WEEKLY` \| `MONTHLY` \| `YEARLY`. **Obligatorio** si `type = RECURRING`; **prohibido** si `type = ONE_TIME` |

```json
{
  "amount": "250000.00", "currency": "ARS", "date": "2026-10-01",
  "category": "HOUSING", "type": "RECURRING", "frequency": "MONTHLY",
  "paymentMethod": "TRANSFER", "description": "Alquiler", "deviceTimeZone": "America/Argentina/Buenos_Aires"
}
```

### Respuesta 201 / 200 — campos nuevos (omitidos cuando son `null`)

| Campo | Presente en | Valor |
|-------|-------------|-------|
| `frequency` | series | `WEEKLY` \| `MONTHLY` \| `YEARLY` |
| `status` | series | `ACTIVE` \| `PAUSED` \| `FINISHED` |
| `nextOccurrenceDate` | series `ACTIVE`/`PAUSED` | `aaaa-mm-dd` |

```json
{
  "id": 81, "amount": "250000.00", "currency": "ARS", "date": "2026-10-01",
  "category": "HOUSING", "type": "RECURRING", "paymentMethod": "TRANSFER",
  "description": "Alquiler", "createdAt": "2026-10-24T18:02:11.204-03:00",
  "frequency": "MONTHLY", "status": "ACTIVE", "nextOccurrenceDate": "2026-11-01"
}
```

Un gasto `ONE_TIME` responde exactamente como en 007 (sin campos nuevos).

### Errores 400 nuevos (`properties.fields.frequency`)

| Condición | Mensaje |
|-----------|---------|
| `RECURRING` sin `frequency` (o vacía) | "La frecuencia es obligatoria para un gasto recurrente" |
| `frequency` fuera del catálogo | "La frecuencia no es válida" |
| `ONE_TIME` con `frequency` | "Un gasto único no puede tener frecuencia" |

Se acumulan con los errores de campo de 007 en una sola respuesta.

### Idempotencia

`frequency` se suma a los campos comparados en el replay: misma clave + misma frecuencia → `200` (replay); misma clave + otra frecuencia → `422 IDEMPOTENCY_KEY_REUSED` (contrato de 007).

---

## Nuevo: `GET /api/expenses/recurring`

Lista las **series** del usuario de la sesión (gastos `RECURRING` con frecuencia), más recientes primero (`created_at` desc, `id` desc). No incluye repeticiones generadas, gastos únicos ni recurrentes de 007 sin frecuencia.

### Respuesta 200

```json
[
  {
    "id": 81, "amount": "250000.00", "currency": "ARS", "date": "2026-10-01",
    "category": "HOUSING", "type": "RECURRING", "paymentMethod": "TRANSFER",
    "description": "Alquiler", "createdAt": "2026-10-24T18:02:11.204-03:00",
    "frequency": "MONTHLY", "status": "ACTIVE", "nextOccurrenceDate": "2026-11-01"
  },
  {
    "id": 64, "amount": "9999.00", "currency": "ARS", "date": "2026-09-15",
    "category": "LEISURE", "type": "RECURRING", "paymentMethod": "CREDIT_CARD",
    "createdAt": "2026-09-15T10:00:00-03:00",
    "frequency": "MONTHLY", "status": "FINISHED"
  }
]
```

Sin series: `[]`.

---

## Nuevo: `PATCH /api/expenses/{id}/status`

Cambia el estado de una serie del usuario de la sesión.

### Petición (`application/json`)

```json
{ "status": "PAUSED" }
```

| Campo | Tipo | Reglas |
|-------|------|--------|
| `status` | string | `ACTIVE` (reanudar) \| `PAUSED` (pausar) \| `FINISHED` (finalizar) |

### Respuestas

| Código | Condición | Cuerpo |
|--------|-----------|--------|
| **200 OK** | Transición válida, o el estado pedido es el actual (sin cambios) | La serie actualizada (forma de `GET /api/expenses/recurring`) |
| **400 Bad Request** | `status` ausente o fuera del catálogo | ProblemDetail "Validación fallida" con `properties.fields.status` |
| **404 Not Found** | `id` inexistente, de otro usuario, gasto único, repetición o recurrente sin frecuencia | ProblemDetail neutro |
| **409 Conflict** | La serie está `FINISHED` y se pide `ACTIVE` o `PAUSED` | ProblemDetail con `properties.code = "RECURRENCE_FINISHED"` |
| **401 Unauthorized** | Sin sesión o cuenta no verificada | ProblemDetail "No autorizado" |

Efecto sobre `nextOccurrenceDate`:

| Transición | `nextOccurrenceDate` |
|------------|----------------------|
| `ACTIVE → PAUSED` | se conserva |
| `PAUSED → ACTIVE` | primera fecha de la serie que sea hoy o posterior (no se recuperan las de la pausa) |
| `* → FINISHED` | se elimina (campo ausente) |

**400**:

```json
{
  "title": "Validación fallida", "status": 400,
  "detail": "La solicitud contiene campos inválidos",
  "properties": { "fields": { "status": "El estado no es válido" } }
}
```

Mensajes de campo: "El estado es obligatorio" / "El estado no es válido".

**404**:

```json
{ "title": "Gasto recurrente no encontrado", "status": 404, "detail": "No se encontró el gasto recurrente" }
```

**409**:

```json
{
  "title": "Gasto recurrente finalizado", "status": 409,
  "detail": "El gasto recurrente ya está finalizado y no puede modificarse",
  "properties": { "code": "RECURRENCE_FINISHED" }
}
```

---

## Proceso interno: generación diaria

No es un endpoint. `RecurringExpenseScheduler` corre según `recurrence.cron` (por defecto `0 15 0 * * *`, 00:15) en la zona `recurrence.zone` (por defecto `America/Argentina/Buenos_Aires`) y registra las repeticiones vencidas de las series `ACTIVE`. Cada repetición es un gasto común: `type = RECURRING`, sin `frequency`/`status`/`nextOccurrenceDate`, con los datos de la serie y su fecha.

---

## Comportamiento del cliente (contrato de interfaz)

### `expenseClient`

| Función | Resultado |
|---------|-----------|
| `createExpense(payload, key)` | Sin cambios de forma (007); el payload incluye `frequency` solo si `type = RECURRING` |
| `listRecurringExpenses()` | `{ ok: true, expenses }` en 200; `{ ok: false, status: 401 }`; `{ ok: false, network: true }` en 5xx o red |
| `updateRecurringExpenseStatus(id, status)` | `{ ok: true, expense }` en 200; `{ ok: false, status, detail, code }` en 4xx; `{ ok: false, network: true }` en 5xx o red |

### `ExpenseForm` (`/expenses/new`)

| Situación | Comportamiento |
|-----------|----------------|
| Tipo "Recurrente" | Aparece "Frecuencia" (Semanal / Mensual / Anual), obligatoria |
| Tipo "Único" o sin elegir | "Frecuencia" oculta y no se envía |
| Error de servidor en `frequency` | Se muestra junto al campo |
| Guardado exitoso | Reset de 007 + frecuencia sin selección |

### `RecurringExpensesPage` (`/expenses/recurring`)

| Situación | Comportamiento |
|-----------|----------------|
| Cargando | "Cargando tus gastos recurrentes…" |
| Sin series | "Todavía no tenés gastos recurrentes" + acceso a "Registrar gasto" |
| Serie `ACTIVE` | Botones "Pausar" y "Finalizar" |
| Serie `PAUSED` | Botones "Reanudar" y "Finalizar" |
| Serie `FINISHED` | Sin botones |
| "Finalizar" | Confirmación inline: "Finalizar es definitivo: no se generarán más gastos de esta serie." con "Sí, finalizar" / "Cancelar" |
| Cambio en curso | Botones de esa serie deshabilitados; el pulsado muestra "Pausando…" / "Reanudando…" / "Finalizando…" |
| 200 | La serie se reemplaza por la respuesta; `role="status"` con "El gasto recurrente se pausó" / "…se reanudó" / "…se finalizó" |
| 401 | "Tu sesión expiró. Iniciá sesión nuevamente." + enlace al login |
| 404 / 409 | El `detail` del servidor y se recarga la lista |
| Red / 5xx al cambiar | "No se pudo actualizar el gasto recurrente. Intentá nuevamente"; la serie conserva su estado |
| Red / 5xx al cargar | "No se pudieron cargar tus gastos recurrentes. Intentá nuevamente" + botón "Reintentar" |

### `SiteNav`

Nuevo acceso "Gastos recurrentes" → `/expenses/recurring`.
