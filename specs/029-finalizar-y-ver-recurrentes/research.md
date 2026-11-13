# Research: Finalizar y ver gastos recurrentes de grupo

**Feature**: specs/029-finalizar-y-ver-recurrentes | **Date**: 2026-11-13

Investigación sobre el código existente (backend Spring Boot + frontend React) para resolver todas las decisiones técnicas de la spec 029. La spec ya entró a esta fase sin `[NEEDS CLARIFICATION]`.

## D1 — Estado Finalizado de las series de grupo

**Decision**: reutilizar el enum `RecurrenceStatus` (`ACTIVE`, `PAUSED`, `FINISHED`, `com.amano.backend.expense`) y la infraestructura de BD existente.

**Rationale**: el enum ya incluye `FINISHED` (creado para 009) y la migración V32 (`V32__group_expense_recurrence.sql`) ya acepta `FINISHED` en el CHECK `ck_group_expenses_recurrence_status` y exige `recurrence_next_date IS NULL` cuando el estado es FINISHED (`ck_group_expenses_recurrence_next_date`) — exactamente lo que pide FR-005. No hace falta ninguna migración para el estado en `group_expenses`.

**Alternatives considered**: (a) tabla/entidad nueva de ciclo de vida — innecesaria, duplicaría el modelo de 009; (b) bandera booleana `finished` — pierde la máquina de estados de tres valores y rompería el CHECK vigente.

## D2 — Reanclaje del ciclo al reanudar (FR-003)

**Decision**: agregar la columna `recurrence_anchor_date DATE` a `group_expenses` (migración V35) con backfill `= expense_date` para las series, y usarla como inicio del ciclo en toda la aritmética de fechas. Al reanudar: `anchor = hoy`, se genera de inmediato la ocurrencia de hoy con `dueDate = hoy + groups.debt_due_days` vigente (si el grupo tiene plazo; sino `null`) y `recurrence_next_date = frequency.firstAfter(anchor, anchor)` (primer período posterior a hoy).

**Rationale**: el job actual (`GroupRecurringExpenseService.generateDueOccurrences`, línea 187) avanza las fechas con `frequency.firstAfter(series.getExpenseDate(), date)`: la secuencia está anclada a la fecha del gasto original. Si al reanudar solo se fijara `recurrence_next_date` (p. ej. 24/11 para una serie del 05/08), el siguiente avance volvería a derivar hacia el 05/12: el anclaje original "gana". FR-003 exige que el ciclo empiece de 0 en la fecha de reanudación y FR-006 prohíbe mutar el gasto original (cuya fila es la propia serie), de modo que el anclaje necesita un campo propio. Con `anchor`, los llamados existentes que usan `expenseDate` se reemplazan 1:1 por `anchor` (comportamiento idéntico tras el backfill) y solo la reanudación cambia el valor.

**Alternatives considered**: (a) actualizar `expense_date` de la serie al reanudar — corrompe el gasto histórico y su historial; (b) avanzar fechas por pasos (`date.plus(1, unit)`) sin anclaje — rompe el clamp de meses cortos (una serie del 31/01 deriva al 28) que FR-013 conserva de 009; (c) guardar el anclaje en una tabla auxiliar — fragmenta el modelo y exige joins para el caso común.

**Detalle de coherencia**: la CHECK vigente `ck_group_expenses_recurrence_next_date` (next date NOT NULL si ACTIVE/PAUSED) se mantiene: al pausar la fecha guardada deja de regir pero se conserva en la fila (la UI la oculta, FR-010); al reanudar se recalcula; al finalizar pasa a NULL. La CHECK de la serie (`frequency IS NULL) = (status IS NULL)`) se replica para el anclaje.

## D3 — Ocurrencia inmediata al reanudar

**Decision**: refactor mínimo de `createOccurrence(series, date)` a `createOccurrence(series, date, dueDate, actorUserId)`.

**Rationale**: la ocurrencia generada por el job usa `dueDate = date` (vence en su propia fecha, 028 FR-016) y actor `null` ("Sistema"). La ocurrencia de reanudación, en cambio, es una "nueva primera ocurrencia": `dueDate = hoy + debt_due_days` vigente del grupo (misma regla de `GroupExpenseService.dueDateFor`) y `EXPENSE_CREATED` a nombre del miembro que reanudó (FR-018). Reutilizar el método parametrizado evita duplicar la copia de shares/deudas; el actor pasa como parámetro (`null` = job, id del miembro = reanudación).

**Alternatives considered**: (a) duplicar la lógica en un método aparte — divergencia y doble mantenimiento; (b) esperar al job del día siguiente — contradice FR-003 ("se genera de inmediato") y el ejemplo de la clarificación (gasto para pagar en 7 días).

## D4 — Eventos de historial con actor (FR-018)

**Decision**: tres tipos de evento nuevos — `RECURRENCE_PAUSED`, `RECURRENCE_RESUMED`, `RECURRENCE_FINISHED` — con `actorUserId` del miembro, y un DTO de detalles `RecurringActivityDetails { expenseId, description, status }`. Migración V36 amplía el CHECK `group_activity_event_type_check` (patrón V28). `ActivityEventService` agrega `recordRecurrenceChange(groupId, type, actorUserId, details)`.

**Rationale**: `GroupActivity` ya modela actor (`actorUserId`, null = "Sistema") y el CHECK de `event_type` es un catálogo cerrado que solo se amplía por migración nueva (V20/V22/V28). Eventos separados por acción permiten descripciones directas en el feed ("Ana pausó el gasto recurrente…") sin acoplar la UI al estado anterior.

**Alternatives considered**: (a) un solo evento `RECURRENCE_STATUS_CHANGED` con el estado en `details` — obliga a la UI a reconstruir el verbo y acepta estados ilegales en el historial; (b) no registrar cambios de estado — viola FR-018 y la trazabilidad de la constitución.

**Nota de alcance**: los textos de `activityDescriptions.js` (frontend) se agregan como parte de US1/US2 para que el feed muestre los eventos nuevos.

## D5 — Acumulado gastado por serie (FR-011)

**Decision**: agregar a `GroupExpenseRepository` una consulta agregada por serie (`SELECT COALESCE(SUM(amount), 0) FROM group_expenses WHERE id = :seriesId OR recurrence_source_id = :seriesId`) y devolverla en `RecurringGroupExpenseResponse.accumulatedAmount` (texto decimal canónico, como `amount`).

**Rationale**: todas las ocurrencias copian `amount` y `currency` de la serie (028 FR-007), así que la suma en la moneda de la serie es directa y evita conversiones. Un solo agregado por listado (en batch para todas las series del grupo) mantiene el costo de la consulta en una pasada extra.

**Alternatives considered**: (a) calcular en el frontend sumando gastos — requeriría listar todas las ocurrencias (paginación, N+1) y duplicaría la regla de negocio; (b) endpoint separado de acumulados — una llamada más por pantalla sin beneficio.

## D6 — Próximo vencimiento y ocultamiento de la próxima fecha (FR-010)

**Decision**: `RecurringGroupExpenseResponse` agrega `nextDueDate`; `nextOccurrenceDate` y `nextDueDate` se envían `null` para series `PAUSED` y `FINISHED`. Para series `ACTIVE`: `nextDueDate = min(recurrence_next_date, min(due_date de las ocurrencias de la serie con due_date >= hoy))`.

**Rationale**: según 028 FR-016 las ocurrencias generadas vencen en su propia fecha, pero una serie reanudada tiene una ocurrencia inmediata de hoy que vence en `hoy + debt_due_days`, anterior a su `recurrence_next_date`; el mínimo cubre ambos casos y coincide con la regla general cuando no hay ocurrencias futuras creadas. Ocultar la próxima fecha de las series pausadas implementa la clarificación de 2026-11-13 (opción C) y prevalece sobre 028 FR-022 según la cláusula de precedencia de la spec.

**Alternatives considered**: (a) mostrar la fecha guardada de la serie pausada — confunde ("próxima" vencida), descartado en clarificación; (b) calcular el vencimiento solo en el frontend — duplicaría las reglas de `debt_due_days`/V33.

## D7 — Contrato PATCH y rechazo de series finalizadas (FR-005, FR-007)

**Decision**: `PATCH /api/groups/{id}/recurring-expenses/{expenseId}` acepta `status: FINISHED`; toda modificación (status o frequency) sobre una serie `FINISHED` responde **409** con `properties.code = "RECURRENCE_FINISHED"` y `detail = "El gasto recurrente ya está finalizado y no puede modificarse"` (mismo mensaje y código que 009 usa en `RecurringExpenseService`). Pedir el estado que la serie ya tiene sigue respondiendo 200 sin cambios (incluye finalizar una finalizada… con la excepción: sobre una Finalizada, cualquier campo responde 409).

**Rationale**: alinea el ciclo de vida de grupo con el personal (009 FR-009/FR-010), reutiliza el código de error existente y mantiene la semántica idempotente solo para estados no finales.

**Alternatives considered**: (a) 400 por estado inválido para FINISHED (estado actual) — contradice FR-004/FR-005; (b) 422 — el proyecto ya usa 409 con `code` para conflictos de estado (patrón 009).

## D8 — Frontend: matriz de acciones y confirmaciones

**Decision**: `GroupRecurringExpensesPage.jsx` adopta los patrones de `RecurringExpensesPage.jsx` (009): `ACTIONS_BY_STATUS` (ACTIVE → Pausar/Finalizar; PAUSED → Reanudar/Finalizar; FINISHED → ninguna), confirmación inline por acción (`.recurring-item__confirm`), avisos de éxito por snackbar y ocultamiento de la próxima fecha cuando el API la manda null. La confirmación de reanudación incluye el aviso de reinicio de FR-017 y el aviso post-acción lo reitera. `GroupExpenseDetailView.jsx` muestra el bloque de origen (FR-013) cuando la respuesta incluye recurrencia.

**Rationale**: la pantalla de grupo hoy solo alterna ACTIVE↔PAUSED con un botón único y sin confirmación; la pantalla personal ya resuelve exactamente los tres estados, la confirmación de Finalizar, los busy labels y el manejo de errores/401. Copiar la matriz evita inventar una segunda interacción para el mismo problema (constitución I y X).

**Alternatives considered**: (a) modal `ConfirmModal` — existe pero la convención en pantallas de recurrencia es la confirmación inline; (b) acciones sin confirmación — viola FR-002/FR-004.

## D9 — Observaciones fuera de alcance

- `OverdueDebtJob` compara contra `expense_date` y no contra la nueva `due_date` (V33): limitación preexistente de 028, no se corrige acá (fuera de alcance de la spec 029).
- El acumulado no filtra ocurrencias "anuladas" (no existe el concepto de gasto anulado en el modelo).
- El job no testea el `@Scheduled`; las pruebas lo invocan con fecha explícita (patrón `GroupExpenseControllerIT`, `RecurringExpenseServiceTest` con `Clock.fixed`).
