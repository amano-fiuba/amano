# Implementation Plan: Ver historial de actividad del grupo

**Branch**: `feat/historial-actividad-grupal` | **Date**: 2026-11-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/014-group-activity-history/spec.md`

## Summary

La tarea "Ver historial de actividad del grupo" (Sprint 4, 2 SP) pide consultar altas, bajas, gastos y pagos del grupo. 010 dejó el endpoint, la tabla y la pantalla, pero el registro no decía a quién afectaba cada alta o baja y armaba el JSON concatenando texto. **US1**: los detalles se serializan con Jackson y guardan una copia del nombre y correo del afectado; la alta del miembro inicial se registra; la pantalla muestra frases completas agrupadas por día. **US2**: `ActivityEventService` expone `recordExpenseCreated` y `recordPaymentMade` para el futuro módulo de gastos, y la pantalla ya los muestra. **US3**: filtro por varios tipos (400 si alguno no existe), tope de 100 por página, actores en una consulta, filtro "Mostrar", estados de carga/vacío/error y "Volver al grupo". **Sin migración** ni dependencias nuevas.

## Technical Context

**Language/Version**: Java 25 (backend); JavaScript con React 19.2 (frontend)

**Primary Dependencies**: Spring Boot 4.1.1 (Web MVC, Data JPA, Security, Jackson 3); React 19.2, react-router 8.4, Vite 8.3. **Sin dependencias nuevas.**

**Storage**: PostgreSQL. **Cero migraciones**; escribe filas nuevas en `group_activity` (`V10`).

**Testing**: JUnit 5 + Mockito (`ActivityEventServiceTest`, `GroupActivityServiceTest` y ajustes en pruebas de 010), Spring Boot Test + Testcontainers (`GroupActivityControllerIT`), Vitest + Testing Library (`activityDescriptions.test.js`, `GroupHistoryView.test.jsx`, `groupsClient.test.js`). Cobertura JaCoCo y Vitest ≥ 85 %.

**Target Platform**: Servidor Linux vía Docker Compose; navegador web desktop y móvil.

**Project Type**: Aplicación web monorepo (backend REST + frontend SPA).

**Performance Goals**: 2 consultas por página (eventos + actores); SC-005 (< 2 s con 100 entradas).

**Constraints**: sin migraciones ni `UPDATE`; no tocar permisos, roles ni amistades; voseo; cobertura ≥ 85 %.

**Scale/Scope**: 0 migraciones, 1 endpoint modificado, 2 records nuevos y 6 clases modificadas en backend; 2 archivos nuevos y 3 modificados en frontend.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principio | Estado | Evidencia |
|---|-----------|--------|-----------|
| I | Arquitectura y separación de responsabilidades | PASS | Todo dentro de `groups/`; escritura en `ActivityEventService`, lectura en `GroupActivityService`; frases en un módulo puro del frontend |
| II | Desarrollo guiado por especificaciones | PASS | `specs/014-group-activity-history/` completa antes del código |
| III | Entrega incremental por historia | PASS | US1 → US2 → US3 con checkpoint (tasks.md) |
| IV | Alcance controlado | PASS | Solo la tarea del cronograma; sin tipos nuevos ni gastos de grupo (research.md D1) |
| V | Calidad, pruebas y cobertura | PASS | Unitarias, integración con Testcontainers y Vitest; umbral 85 % |
| VI | Integridad de las reglas de negocio | PASS | Eventos en la misma transacción; filas inmutables; monto > 0 |
| VII | Contratos REST y compatibilidad | PASS | `contracts/activity-api.md`; cambios compatibles documentados |
| VIII | Validación y manejo de errores | PASS | 400 con `ProblemDetail` y campo `eventType`; errores de UI con mensaje fijo |
| IX | Seguridad y control de acceso | PASS | Acceso de 010 sin cambios; JSON escapado por el serializador |
| X | Código mantenible y convenciones | PASS | Nombres en inglés, `*Test`/`*IT`, `*.test.js(x)` |
| XI | Entorno reproducible | PASS | Sin propiedades, dependencias ni migraciones nuevas |
| XII | Revisión por pares | PENDIENTE (posterior) | PR aún no abierto |
| XIII | Commits y trazabilidad | PENDIENTE (posterior) | Los commits los realiza el equipo |

**Gate resultado**: PASS.

## Project Structure

### Documentation (this feature)

```text
specs/014-group-activity-history/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/activity-api.md
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
backend/src/main/java/com/amano/backend/groups/
├── ActivityEventService.java        # M: record(Map) con Jackson; enganches de gasto y pago
├── ExpenseActivityDetails.java      # A: record del enganche de gasto
├── PaymentActivityDetails.java      # A: record del enganche de pago
├── GroupActivityService.java        # M: filtro múltiple, 400, tope, actores en lote, details objeto
├── GroupActivityRepository.java     # M: findByGroupIdAndEventTypeIn...
├── GroupMemberService.java          # M: detalles de altas y bajas con copia del nombre
├── GroupService.java                # M: alta del miembro inicial; detalles como Map
└── dto/ActivityHistoryResponse.java # M: details como JsonNode

backend/src/test/java/com/amano/backend/groups/
├── ActivityEventServiceTest.java    # A
├── GroupActivityServiceTest.java    # A
├── GroupActivityControllerIT.java   # M
└── (GroupMemberServiceTest, MemberRemovalServiceTest, GroupSettingsServiceTest, GroupServiceTest) # M: anyMap

frontend/src/
├── api/groupsClient.js                          # M: eventTypes, 5xx como red
├── api/groupsClient.test.js                     # M
├── pages/groups/activityDescriptions.js         # A: frases por tipo, filtros, formato
├── pages/groups/activityDescriptions.test.js    # A
├── pages/groups/GroupHistoryView.jsx            # M: reescrita
├── pages/groups/GroupHistoryView.test.jsx       # M
└── index.scss                                   # M: estilos .group-history
```

**Structure Decision**: aplicación web (backend + frontend) existente; no se crean módulos ni paquetes nuevos.

## Complexity Tracking

| Elemento | Detalle | Justificación / acción |
|----------|---------|------------------------|
| Enganche sin consumidor | `recordExpenseCreated`/`recordPaymentMade` no se llaman desde producción todavía | Es el entregable de "gastos y pagos" mientras no existan; probados por unitarias e integración (research.md D6) |
| Cambio de `details` a objeto | Cambia el tipo de un campo de 010 | Ningún consumidor lo usaba; documentado en el contrato (research.md D9) |
| Filas viejas con poco detalle | No se pueden completar sin `UPDATE` | La pantalla usa frases genéricas (data-model.md §Formatos anteriores) |
