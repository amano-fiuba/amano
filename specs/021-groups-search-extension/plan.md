# Implementation Plan: Extensión de grupos y buscador

**Branch**: `fix/ui-grupos` | **Date**: 2026-11-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/021-groups-search-extension/spec.md`

## Summary

Spec retroactiva de lo implementado en `551a2f0`, más la eliminación de la casilla reemplazada: extiende 008, 010 y 011. El buscador informa el estado de la relación y el grupo suma la casilla "Pueden editar datos del grupo", el modo edición, la eliminación de foto y la etiqueta "Simulado". Solo quedan abiertas pruebas faltantes ([tasks.md](./tasks.md)).

## Technical Context

**Language/Version**: Java 25 (backend); JavaScript con React 19.2 (frontend)

**Primary Dependencies**: Spring Boot 4.1.1, Flyway; React, react-router, Vite. **Sin dependencias nuevas.**

**Storage**: PostgreSQL. Dos migraciones: `V15` (agrega la columna) y `V16` (elimina la casilla vieja).

**Testing**: JUnit 5 + Mockito, Spring Boot Test + Testcontainers, Vitest + Testing Library. Umbrales ≥ 85 %.

**Target Platform**: Servidor Linux vía Docker Compose; navegador web desktop y móvil.

**Project Type**: Aplicación web monorepo (backend REST + frontend SPA).

**Constraints**: cambios de contrato aditivos, salvo la autorización de `PUT /api/groups/{id}` y la baja de `membersCannotEditSettings`; voseo.

## Constitution Check

| # | Principio | Estado | Evidencia |
|---|-----------|--------|-----------|
| I | Arquitectura | PASS | La decisión de permisos sigue en `GroupPermissionChecker` |
| II | Desarrollo guiado por especificaciones | **Desvío** | El código se publicó antes que la spec; esta spec lo regulariza |
| III | Entrega incremental | PASS | Cuatro historias independientes |
| IV | Alcance controlado | PASS | Sección "Fuera de alcance" |
| V | Calidad y cobertura | PASS con nota | Umbrales cumplidos, con pruebas faltantes en tasks.md |
| VI | Integridad de reglas | PASS con nota | Cambio de comportamiento por defecto en grupos existentes (spec.md §Assumptions), para revisión en el PR |
| VII | Contratos REST | PASS con nota | `PUT` restringe permisos y se elimina `membersCannotEditSettings`: documentado en el contrato |
| VIII | Validación y errores | PASS | `ProblemDetail` existentes |
| IX | Seguridad | PASS | Autorización en el servidor por campo |
| X | Código mantenible | PASS | Se elimina la casilla reemplazada (research.md D7) |
| XI | Entorno reproducible | PASS | `V15` y `V16` corren en el arranque y en los IT |
| XII | Revisión por pares | PENDIENTE (posterior) | PR aún no abierto |
| XIII | Commits y trazabilidad | PENDIENTE (posterior) | Los commits los realiza el equipo |

**Gate resultado**: PASS con desvío en II, justificado arriba.

## Project Structure

```text
backend/src/main/
├── resources/db/migration/V15__group_permissions_edit_group_data.sql
├── resources/db/migration/V16__drop_members_cannot_edit_settings.sql
└── java/com/amano/backend/
    ├── user/{UserSearchResult,UserSearchService}.java
    ├── friendship/FriendshipRequestRepository.java          # findActiveBetween
    └── groups/{GroupPermissions,GroupPermissionChecker,GroupService,GroupController}.java
        └── dto/UpdateGroupRequest.java

frontend/src/
├── api/groupsClient.js                                      # deleteGroupPhoto
├── components/NavIcons.jsx                                  # PencilIcon
├── components/groups/{GroupEditForm,GroupPhotoPanel,GroupPermissionsPanel,MemberListView}.jsx
└── pages/{UsersSearchPage.jsx,groups/GroupInfoView.jsx,groups/groups.scss}
```

## Complexity Tracking

| Desvío | Por qué | Alternativa descartada |
|---|---|---|
| Spec posterior al código | Los cambios surgieron como arreglos | Editar 008, 010 y 011: pierde el historial y rompe la costumbre de 018 |
| Guardado en dos pasos | La foto y los datos son endpoints distintos | Endpoint único: cambia contratos existentes |
