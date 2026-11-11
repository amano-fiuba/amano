# Contrato: Administración de series recurrentes de un grupo

**Spec**: specs/028-group-recurring-expenses/spec.md · **Generated**: 2026-11-09

## GET `/api/groups/{id}/recurring-expenses`

Cualquier miembro activo. Devuelve las series (`recurrence_frequency != null`) del
grupo, más nuevas primero.

```json
[
  {
    "id": 7,
    "amount": "50000.00",
    "currency": "ARS",
    "date": "2026-11-02",
    "category": "HOUSING",
    "type": "RECURRING",
    "paymentMethod": "TRANSFER",
    "description": "Alquiler",
    "frequency": "WEEKLY",
    "status": "ACTIVE",
    "nextOccurrenceDate": "2026-11-16"
  }
]
```

Errores: `401` sin sesión; `404` si no es miembro o el grupo no existe.

## PATCH `/api/groups/{id}/recurring-expenses/{expenseId}`

Solo el creador de la serie, el pagador o un admin. Body parcial; al menos un campo.

```json
{ "status": "PAUSED" }
{ "frequency": "MONTHLY" }
{ "status": "ACTIVE", "frequency": "WEEKLY" }
```

- `status`: `ACTIVE` o `PAUSED`. Reactivar recalcula `next_occurrence_date` con la
  frecuencia vigente desde hoy.
- `frequency`: `WEEKLY`, `MONTHLY` o `YEARLY`. En una serie activa recalcula la
  próxima fecha; en una pausada se conserva hasta reactivarla.
- Respuesta: la serie actualizada con la misma forma del GET.
- Errores: `400` sin campos o con valores fuera del catálogo (`fields.status` /
  `fields.frequency`); `403` para otro miembro; `404` si no es una serie del grupo.

## Efectos en otros contratos

- Historial (`GroupActivity`): las ocurrencias generadas registran `EXPENSE_CREATED`
  con `actor` nulo y la UI las muestra como "Sistema".
- Correo de recordatorio: agrega `Categoría`, `Tipo de gasto` y `Medio de pago`.
  `Medio de pago` se omite cuando la ocurrencia fue generada por el sistema
  (`recurrence_source_id != null`).
