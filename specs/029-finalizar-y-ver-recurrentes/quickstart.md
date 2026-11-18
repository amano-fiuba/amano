# Quickstart: Finalizar y ver gastos recurrentes de grupo

**Feature**: specs/029-finalizar-y-ver-recurrentes | **Date**: 2026-11-13

Guía de validación end-to-end. Referencias: [contracts/group-recurring-expenses-management.md](contracts/group-recurring-expenses-management.md), [contracts/expense-detail-origin.md](contracts/expense-detail-origin.md), [data-model.md](data-model.md).

## Prerrequisitos

- Docker (Testcontainers) y PostgreSQL disponible vía Docker Compose del proyecto.
- Java 21 + Maven; Node con `npm ci` ejecutado en `frontend/`.
- Stack del repositorio: backend Spring Boot + frontend React (ver `plan.md`).

## Verificaciones automatizadas

```bash
# Backend (unitarias + integración + cobertura JaCoCo ≥ 85 %)
cd backend && mvn -B verify

# Frontend (lint + pruebas + cobertura Vitest ≥ 85 % + build)
cd frontend && npm ci && npm run lint && npm run test:coverage && npm run build
```

Pruebas clave que deben existir y pasar (ver `tasks.md`):

- `GroupRecurringExpenseServiceTest` (unitario, `Clock.fixed`): reanudación reancla a hoy, ocurrencia inmediata con `due_date = hoy + debt_due_days`, `next_date` un período después; finalizar limpia `next_date`; 409 sobre finalizadas.
- `GroupRecurringExpenseIT` (Testcontainers + MockMvc): PATCH con `FINISHED`, 409 `RECURRENCE_FINISHED`, permisos 403, 404 de ocurrencias, `accumulatedAmount` y `nextDueDate` en el listado, eventos de historial con actor, ocurrencia de reanudación como `EXPENSE_CREATED` del miembro.
- `GroupRecurringExpensesPage.test.jsx`: matriz de acciones por estado, confirmaciones (cancelar no llama al API), aviso de reinicio al reanudar, acumulado y ocultamiento de próxima fecha.
- `GroupExpenseDetailView.test.jsx`: bloque de origen (SERIE/SISTEMA/MIEMBRO) y ausencia en gastos no recurrentes.

## Escenarios manuales (docker compose up + backend + frontend)

Preparación: grupo "Depto" con dos miembros (Ana admin, Juan miembro), `debt_due_days = 7`; Ana registra el gasto "Alquiler" $10.000, mensual, tipo Recurrente.

1. **Pausar con confirmación (US1)**: Ana entra a "Gastos" → "Gastos recurrentes". En la fila Activa pulsa "Pausar" → aparece la confirmación; "Cancelar" no cambia nada; "Sí, pausar" → badge "Pausado", snackbar "El gasto recurrente se pausó", la fila ya no muestra próxima fecha ni vencimiento. En el feed: "Ana pausó el gasto recurrente Alquiler".
2. **Sin generación en pausa (US1/SC-001)**: esperar (o forzar la corrida del job con fecha posterior a la próxima fecha) → no aparece ninguna ocurrencia nueva de la serie.
3. **Reanudar con reinicio (US1)**: pulsa "Reanudar" → la confirmación explica que el ciclo empieza de nuevo desde hoy ("se generará un gasto para pagar en 7 días y el siguiente en un mes"). Confirmar → aparece de inmediato el gasto de hoy en los gastos del grupo, con deuda de Juan pendiente venciendo en 7 días; snackbar con el aviso de reinicio; la fila muestra "Próxima fecha: dentro de un mes" y el nuevo acumulado ($20.000 si ya había una ocurrencia). El feed muestra el gasto a nombre de Ana y "Ana reanudó…".
4. **Finalizar (US2)**: pulsa "Finalizar" → confirmación "Finalizar es definitivo…". Confirmar → estado "Finalizado", sin acciones ni fechas; el job ya no genera nada; intentar reanudar por API responde 409 `RECURRENCE_FINISHED`. El gasto original y las ocurrencias (con sus deudas) siguen en el grupo y en el detalle.
5. **Acumulado y próximo vencimiento (US3)**: en la fila de una serie Activa con dos ocurrencias de $10.000: importe $10.000, acumulado $20.000, próximo vencimiento = la menor fecha futura entre la ocurrencia ya generada y la próxima fecha.
6. **Origen (US3)**: abrir el detalle del gasto original → "Este gasto dio origen a la serie recurrente". Abrir el detalle de una ocurrencia generada → "Generado automáticamente por el sistema · parte de la serie Alquiler". Un gasto no recurrente no muestra el bloque de origen.
7. **Permisos (US1/US2)**: Juan (miembro sin permiso) ve la lista y los detalles, pero no puede pausar/reanudar/finalizar: la API responde 403 y la UI informa el error sin cambios.
8. **Errores (SC/edge)**: sin conexión, la lista muestra el mensaje de reintento y las acciones no inventan éxitos; sesión expirada informa y ofrece login.

## Resultado esperado

- Ninguna serie `PAUSED` o `FINISHED` genera ocurrencias (SC-001).
- 100 % de las modificaciones sobre series finalizadas rechazadas con 409 (SC-002).
- Pausar/reanudar/finalizar pasan siempre por confirmación y actualizan la fila sin recargar (SC-003).
- Acumulado y saldos reflejan cada ocurrencia sin acción manual (SC-004).
- El detalle identifica el origen en un solo paso (SC-005).
