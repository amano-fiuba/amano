# Implementation Plan: Abandonar grupo

**Branch**: `fix/ui-grupos` | **Date**: 2026-11-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/020-leave-group/spec.md`

## Summary

"Abandonar grupo" pasa a un modal de tres modos: confirmación, único administrador (solo equis) y último miembro. En el servidor no se borra nada: todos abandonan con baja lógica y el grupo deja de mostrarse porque los listados exigen una membresía activa.

La regla del último miembro se publicó en el commit `551a2f0`; el modal, el dato `isLastAdmin` del servidor y las pruebas se agregaron después.

## Technical Context

**Language/Version**: Java 25 (backend); JavaScript con React 19.2 (frontend)

**Primary Dependencies**: Spring Boot 4.1.1; React, react-router, Vite. **Sin dependencias nuevas.**

**Storage**: PostgreSQL. **Sin migraciones.**

**Testing**: JUnit 5 + Mockito, Spring Boot Test + Testcontainers, Vitest + Testing Library. Umbrales JaCoCo y Vitest ≥ 85 %; no se bajan.

**Target Platform**: Servidor Linux vía Docker Compose; navegador web desktop y móvil.

**Project Type**: Aplicación web monorepo (backend REST + frontend SPA).

**Constraints**: mensajes de 010 sin cambios; cambios de contrato solo aditivos; voseo.

**Scale/Scope**: backend con 2 clases modificadas; frontend con 1 componente nuevo y 4 archivos modificados.

## Constitution Check

| # | Principio | Estado | Evidencia |
|---|-----------|--------|-----------|
| I | Arquitectura | PASS | Sin capas ni clases de dominio nuevas |
| II | Desarrollo guiado por especificaciones | PASS con nota | La regla del último miembro se publicó antes que esta spec; 020 la documenta |
| III | Entrega incremental | PASS | US1 → US2 → US3 → US4 |
| IV | Alcance controlado | PASS | Sin disolución ni borrado |
| V | Calidad y cobertura | PASS | Unitarias, IT y Vitest; umbral 85 % |
| VI | Integridad de reglas | PASS con nota | Excepción acotada al bloqueo de último administrador de 010 (research.md D2), para revisión en el PR |
| VII | Contratos REST | PASS | Solo campos aditivos |
| VIII | Validación y errores | PASS | 403/404 con `ProblemDetail` existentes |
| IX | Seguridad | PASS | El servidor decide siempre; 404 neutral |
| X | Código mantenible | PASS | Convenciones existentes |
| XI | Entorno reproducible | PASS | Sin migraciones ni propiedades nuevas |
| XII | Revisión por pares | PENDIENTE (posterior) | PR aún no abierto |
| XIII | Commits y trazabilidad | PENDIENTE (posterior) | Los commits los realiza el equipo |

**Gate resultado**: PASS.

## Project Structure

```text
specs/020-leave-group/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md, tasks.md
├── contracts/leave-group-api.md
└── checklists/requirements.md

backend/src/main/java/com/amano/backend/groups/
├── GroupService.java                  # toDetail: isLastAdmin; comentario de leaveGroup
└── dto/GroupDetailResponse.java       # isLastAdmin

frontend/src/
├── components/groups/LeaveGroupModal.jsx   # nuevo
├── components/groups/LeaveGroupPanel.jsx   # usa el modal
├── components/groups/MemberListView.jsx    # sin el aviso de último administrador
└── pages/groups/{GroupInfoView.jsx,groups.scss}
```

## Diseño del frontend

```text
GroupInfoView
└── LeaveGroupPanel (groupId, isLastAdmin, isLastMember)
    └── LeaveGroupModal (mode, onConfirm, onClose, busy, error)
```

- `LeaveGroupPanel` calcula `mode`: `isLastMember ? 'last-member' : isLastAdmin ? 'last-admin' : 'confirm'`, y llama a `leaveGroup`.
- `LeaveGroupModal` renderiza el texto, la equis (`aria-label="Cerrar"`) y, salvo en `last-admin`, "Confirmar" y "Cancelar".
- `GroupInfoView` usa los flags del detalle y elimina la deducción sobre `memberCount`.

## Validación manual

Ver [quickstart.md](./quickstart.md).

## Complexity Tracking

Sin desvíos.
