# Contract: Gestión de gastos recurrentes de grupo (029)

**Feature**: specs/029-finalizar-y-ver-recurrentes | **Date**: 2026-11-13

Extiende el contrato de 028 (`specs/028-group-recurring-expenses/contracts/group-recurring-expenses-management.md`). Todo lo no mencionado acá se conserva igual (sesión requerida, 401 con `{"detail": "Debés iniciar sesión para continuar"}`, 404 neutros, RFC 7807 con `properties.fields`). Donde difieran, vale este contrato (autorizado por la spec 029).

## PATCH `/api/groups/{id}/recurring-expenses/{expenseId}`

Cambia el estado del ciclo de vida y/o la frecuencia de una serie. Cuerpo JSON con los campos opcionales `status` y `frequency`.

**Request**

```json
{ "status": "FINISHED" }
```

- `status`: `ACTIVE` | `PAUSED` | `FINISHED` (nuevo valor aceptado).
- `frequency`: `WEEKLY` | `MONTHLY` | `YEARLY` (sin cambios de 028).

**Semántica por estado actual (FR-002 a FR-007)**

| Serie está en | Request | Resultado |
|---------------|---------|-----------|
| `ACTIVE` | `status: "PAUSED"` | 200: serie `PAUSED`. La respuesta devuelve `nextOccurrenceDate` y `nextDueDate` `null` (FR-010: dejan de regir). Evento `RECURRENCE_PAUSED` con actor. |
| `PAUSED` | `status: "ACTIVE"` | 200: serie `ACTIVE`. **Reanudación (FR-003)**: se crea de inmediato la ocurrencia de hoy (`due_date = hoy + debt_due_days` vigente del grupo, `NULL` si el grupo no tiene plazo), `recurrence_anchor_date = hoy`, `recurrence_next_date = primer período posterior a hoy`. Evento `RECURRENCE_RESUMED` con actor y `EXPENSE_CREATED` de la ocurrencia a nombre del miembro. Si ya existía una ocurrencia de la serie fechada hoy, no se crea otra. |
| `ACTIVE`/`PAUSED` | `status: "FINISHED"` | 200: serie `FINISHED`, `next_date` `NULL`, sin acciones posteriores. Evento `RECURRENCE_FINISHED` con actor. |
| Cualquiera | Estado o frecuencia igual a la vigente | 200 sin cambios (no-op idempotente), **salvo sobre una serie `FINISHED`** (ver abajo). |
| Serie `ACTIVE` | `frequency: X` | 200: nueva frecuencia y `next_date` recalculada con la cadencia nueva desde hoy (028 FR-022, ahora anclada a `recurrence_anchor_date`). |

**Errores**

| Caso | HTTP | Cuerpo |
|------|------|--------|
| Serie `FINISHED` y se envía cualquier campo (`status` o `frequency`) | 409 | `detail: "El gasto recurrente ya está finalizado y no puede modificarse"`, `properties.code: "RECURRENCE_FINISHED"` (mismo mensaje y código que 009) |
| `status` fuera del catálogo (`ACTIVE`/`PAUSED`/`FINISHED`) | 400 | `properties.fields.status = "El estado no es válido"` (catálogo ampliado; `FINISHED` ya no es inválido) |
| `frequency` inválida | 400 | `properties.fields.frequency = "La frecuencia no es válida"` |
| Body vacío | 400 | `properties.fields.body = "Enviá al menos un campo para editar"` |
| Sin permiso (no creador, no pagador, no admin) | 403 | `detail: "No podés modificar este gasto recurrente"` |
| Serie inexistente, no es del grupo, o es una ocurrencia | 404 | `detail: "No existe el gasto solicitado en este grupo"` |
| No miembro / grupo inexistente | 404 | 404 neutral (sin revelar existencia) |
| Sin sesión | 401 | `{"detail": "Debés iniciar sesión para continuar"}` |

**Response 200** (`RecurringGroupExpenseResponse`): misma forma que 028 con dos campos nuevos:

```json
{
  "id": 42,
  "amount": "10000.00",
  "currency": "ARS",
  "date": "2026-08-05",
  "category": "VIVIENDA",
  "type": "RECURRING",
  "paymentMethod": "TRANSFERENCIA",
  "description": "Alquiler",
  "frequency": "MONTHLY",
  "status": "ACTIVE",
  "nextOccurrenceDate": "2026-11-24",
  "accumulatedAmount": "30000.00",
  "nextDueDate": "2026-11-24"
}
```

- `accumulatedAmount` (nuevo, FR-011): suma del gasto original y de sus ocurrencias generadas, en la moneda de la serie; `"0.00"` si no hay ocurrencias generadas.
- `nextDueDate` (nuevo, FR-010): vencimiento de la próxima ocurrencia según las reglas de 028 (`min(recurrence_next_date, min(due_date de ocurrencias con due_date >= hoy))`); `NULL` para `PAUSED`/`FINISHED` o si no hay ninguna fecha futura (p. ej. grupo sin `debt_due_days` y sin ocurrencias futuras).
- `nextOccurrenceDate`: `NULL` para `PAUSED` y `FINISHED` (FR-010; clarificación 2026-11-13). Para `ACTIVE` conserva el valor de 028.

## GET `/api/groups/{id}/recurring-expenses`

Sin cambios de contrato de acceso (cualquier miembro activo). Cada item de la lista usa la misma forma del `PATCH` (incluye `accumulatedAmount` y `nextDueDate`; `nextOccurrenceDate`/`nextDueDate` `null` para `PAUSED`/`FINISHED`). Orden y vacío igual que 028.
