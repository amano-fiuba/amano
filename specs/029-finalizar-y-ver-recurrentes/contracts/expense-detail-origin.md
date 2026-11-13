# Contract: Origen de recurrencia en el detalle de gasto de grupo (029)

**Feature**: specs/029-finalizar-y-ver-recurrentes | **Date**: 2026-11-13

Extiende el detalle de gasto de grupo existente (`GET /api/groups/{id}/expenses/{expenseId}`, contratos de 017/022) con la información de origen que exige FR-013.

## Cambio en `GroupExpenseResponse`

Campo nuevo, aditivo y compatible (los consumidores que no lo lean no se ven afectados):

```json
"recurrence": { "seriesId": 42, "origin": "SISTEMA" }
```

- `NULL` cuando el gasto no pertenece a ninguna serie recurrente (gasto normal o de amigo): no aparece en la respuesta (`NON_NULL`).
- `seriesId`: id de la serie a la que pertenece el gasto.
- `origin`:
- `"SERIE"` — el gasto consultado es el que dio origen a la serie (la fila con `recurrence_frequency`).
- `"SISTEMA"` — ocurrencia con `recurrence_created_via = 'JOB'` (generada por el job; columna de V35, backfill `'JOB'` para las existentes).
- `"MIEMBRO"` — ocurrencia con `recurrence_created_via = 'RESUME'`, creada al reanudar la serie (FR-003); el creador es el miembro que reanudó.

El valor se lee de los datos persistidos en la fila (`recurrence_created_via`), no se infiere del histórico de actividad.

El resto de la respuesta (payer, createdBy, shares, debts, conversión) no cambia. El frontend usa este campo para mostrar el bloque de origen en `GroupExpenseDetailView`:

- `SERIE` → "Este gasto dio origen a la serie recurrente".
- `SISTEMA` → "Generado automáticamente por el sistema · parte de la serie {descripción}".
- `MIEMBRO` → "Generado al reanudar la serie {descripción} · creado por {createdBy.name}".

En el feed de actividad del grupo el actor de las ocurrencias del job sigue siendo "Sistema" (028) y el de la ocurrencia de reanudación es el miembro que reanudó (FR-018); los nuevos eventos `RECURRENCE_PAUSED` / `RECURRENCE_RESUMED` / `RECURRENCE_FINISHED` se describen en `activityDescriptions.js` (p. ej. "Ana pausó el gasto recurrente Alquiler").
