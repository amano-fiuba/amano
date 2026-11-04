# Implementation Plan: groups-management

**Branch**: `010-groups-management` (feature branch `feat/groups-management`, creada desde `sprint-n`) | **Date**: 2026-10-27 | **Spec**: `specs/010-groups-management/spec.md`

**Input**: Feature specification from `specs/010-groups-management/spec.md` (10 historias de usuario, 29 FR, 10 SC; sesiones de aclaración integradas en `## Clarifications`).

## Summary

Módulo `groups-management`: gestión de grupos de gastos compartidos — creación de grupos, configuración de permisos por casillas, altas/bajas de miembros (registrados y perfiles simulados), abandono con protección de último admin, listado de miembros, descripción y foto del grupo, historial de actividad y gestión de roles ADMIN/MEMBER por grupo.

Enfoque técnico: **exclusivamente extensiones sobre el stack existente del repo** (Java 17 + Spring Boot, Maven, PostgreSQL con JPA/Flyway, JWT por cookie, React + Vite + Vitest). Nuevo package `com.amano.backend.groups` con entidades `Group`, `GroupMember`, `SimulatedProfile`, `GroupPermissions`, `GroupActivity`; 6 migraciones Flyway nuevas (V6–V11, sin tocar migraciones existentes); `GroupPermissionChecker` como único punto de decisión de rol+casillas; puertos `DebtPort` y `FriendshipPort` (con stubs) para las dependencias que todavía no existen en el repo (módulo de deudas, módulo de amistades). Los eventos de actividad de miembros se registran desde Foundational; gastos y pagos quedan como puntos de enganche **sin implementarse en este módulo**.

## Technical Context

**Language/Version**: Java 17 (backend) — según `backend/pom.xml`; código e identificadores en inglés, artefactos SDD y textos de UI en español (constitución, sección convenciones).
**Backend framework**: Spring Boot 3 (Web, Security, Data JPA, Validation) — existente, sin dependencias nuevas.
**Build backend**: Maven (`backend/pom.xml`, `mvnw`).
**Storage**: PostgreSQL (via Docker Compose `compose.yaml`) con JPA/Hibernate y migraciones gestionadas por **Flyway** (`backend/src/main/resources/db/migration/`, versiones V6–V11 para este módulo; las migraciones integradas no se editan).
**Auth existente (reuse)**: JWT en cookie (`com.amano.backend.auth`) — no se incorpora mecanismo de autenticación nuevo; los endpoints de grupos reutilizan la resolución del usuario autenticado.
**Frontend**: React (según `frontend/package.json`) con Vite, Vitest + React Testing Library; UI responsive (mismo React para escritorio y móvil; no hay codebase mobile separada).
**Testing**: backend: JUnit 5 + Mockito (`*Test` rápidos) y Testcontainers con PostgreSQL real (`*IT` de integración) bajo `backend/src/test/java/`; frontend: `*.test.jsx` colocados junto a los componentes. Cobertura mínima 85% en backend (JaCoCo integrado en `mvn verify`) y frontend (`npm run test:coverage`).
**Target Platform**: Servidor Spring Boot en Docker Compose (exposición con Caddy en `Caddyfile`); aplicación web responsive (desktop + mobile).
**Project Type**: Monorepo web app (`backend/` + `frontend/`).
**Performance Goals**: La spec no define metas de rendimiento medibles para este módulo (SC-001 define en tiempo humano "<2 minutos", no técnico). No se fijan números inventados.
**Constraints**: Foto del grupo: solo JPG/JPEG/PNG, máximo 2 MB (mismo límite content-based del `AvatarValidator` del usuario; si se supera se responde 413); los bytes viven en `group_photos` (migración V11, PK = `group_id`, BYTEA ≤ 2.097.152 octetos, mismo patrón que `user_avatars`) y `groups.photo` guarda solo la URL de servicio (`/api/groups/{id}/photo?v=<fingerprint>`, NULLable = avatar gris por defecto); validación reutilizando el mecanismo existente de avatares; nombre del grupo solo obligatorio/no vacío (sin unicidad, sin límite de longitud, columna VARCHAR(255)); eventos de actividad standalone (preservación); último admin protegido al abandonar y al quitar el rol; no hay disolución de grupos en esta versión.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Veredicto | Evidencia / Justificación |
|---|---|---|
| I. Arquitectura y separación de responsabilidades | **PASS** | Se reutilizan `User`, la validación de avatar existente, la infraestructura JWT y las convenciones existentes (`com.amano.backend.*`, `*Test`/`*IT`). El package nuevo `groups` está justificado por el requisito (no hay entidad Group previa); módulos ajenos al alcance no se modifican |
| II. SDD y trazabilidad | **PASS** (post-plan) | spec/plan/tasks/contratos completos y trazables (cada FR mapea a tareas en `tasks.md`); este plan y los artefactos de Fase 1 completan el circuito SDD |
| III. Entrega incremental por historia | **PASS** | `tasks.md` organiza 10 historias en fases con checkpoint independiente; MVP = US1; Foundational define lo compartido antes de implementar historias |
| IV. Alcance controlado | **PASS** | Sin features no solicitadas; los elementos pendientes están documentados explícitamente: gastos/pagos son puntos de enganche (no implementados), `DebtPort`/`FriendshipPort` son stubs. El conflicto sobre `searchVerified()` vs amistad quedó **resuelto por decisión del usuario** (solo amigos, task T020) y `research.md` fue corregido en esta pasada |
| V. Calidad, pruebas y cobertura ≥85% | **PASS** | Tests por historia (`*Test`/`*IT`, `*.test.jsx`) con casos esperados, límite (2 MB con 413 al superarlo, tipo de archivo, último admin, deudas simuladas con el stub) y verificación de cobertura JaCoCo/Vitest en T088/T089 |
| VI. Integridad de reglas de negocio | **PASS** | Reglas según spec (deudas, último admin, perfiles simulados sin autobaja, rol por grupo). **Nota documentada**: `spec.md` no define defaults de las casillas → decisión explícita del usuario: todas en **false** (registrada en el análisis previo; no se inventó un valor sin aprobarlo) |
| VII. Contratos REST y compatibilidad | **PASS** (post-sync) | Los contratos `contracts/api-contracts.md` se corrigieron en esta pasada para reflejar las decisiones aprobadas: `DELETE` para baja de miembro, asignar/quitar admin como `POST`/`DELETE` separados, nombre sin unicidad, sin límite de longitud inventado. Son cambios identificados y justificados (no incompatibilidades accidentales); no hay funcionalidad existente que consuma estos endpoints (módulo nuevo) |
| VIII. Validación y manejo de errores | **PASS** | Validación en frontera backend (DTO + servicio) y mensajes comprensibles en UI (confirmaciones y motivos en el frontend, tareas T026-T064); los errores informan sin modificar estado |
| IX. Seguridad y control de acceso | **PASS** | `GroupPermissionChecker` (T022) centraliza rol+casillas **server-side**; no se confía en que el frontend oculte opciones; 401 para no autenticados |
| X. Código mantenible | **PASS** | Sin dependencias nuevas; convenciones siguiendo `User`/`Expense`; sin código de depuración |
| XI. Entorno reproducible e IC | **PASS** | `mvn -B verify` en `backend/` y `npm ci && npm run lint && npm run test:coverage && npm run build` en `frontend/`; migraciones Flyway V6–V11 nuevas sin editar las existentes |
| XII. PR e integración controlada | **PASS** | Feature → PR a `sprint-n`; promoción a `main` por PR (flujo de constitución) |
| XIII. Commits y trazabilidad | **PASS** | Conventional Commits; no se exige un commit por tarea |

**Post-design re-evaluation (después de Fase 1)**: los artefactos (`research.md`, `data-model.md`, `contracts/`, `quickstart.md`) fueron corregidos para alinearse con las decisiones aprobadas; no quedan contradicciones spec↔plan↔tasks. Se identificó un único punto de alcance externo: **los comportamientos de gastos y pagos que algunas casillas gobiernan pertenecen a módulos posteriores** — documentado como punto de enganche, no como omisión.

## Project Structure

### Documentation (this feature)

```text
specs/010-groups-management/
├── plan.md                     # Este archivo
├── spec.md                     # Especificación (10 historias, 29 FR)
├── research.md                 # Fase 0: análisis del repo y decisiones
├── data-model.md               # Fase 1: entidades y relaciones
├── contracts/api-contracts.md  # Fase 1: contratos REST
├── quickstart.md               # Fase 1: guia de validación (12 escenarios)
├── checklists/requirements.md  # Checklist de calidad (25/25)
└── tasks.md                    # Fase 2: 91 tareas por historia
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/amano/backend/groups/       # módulo nuevo
│   ├── Group.java / GroupMember.java / SimulatedProfile.java
│   ├── GroupPermissions.java / GroupActivity.java
│   ├── GroupRepository.java / GroupMemberRepository.java
│   ├── SimulatedProfileRepository.java / GroupPermissionsRepository.java
│   ├── GroupActivityRepository.java
│   ├── GroupPermissionChecker.java                # rol + casillas (única decisión authz)
│   ├── ActivityEventService.java                   # eventos de miembros; gastos/pagos = hook points
│   ├── GroupService.java / GroupMemberService.java
│   ├── GroupPhoto.java + GroupPhotoRepository.java  # foto del grupo: bytes en group_photos (V11), servida desde GroupController (US8)
│   ├── GroupController.java
│   ├── port/
│   │   ├── DebtPort.java + NoOpDebtPort.java       # stub: "sin deudas"
│   │   └── FriendshipPort.java + AcceptedFriendshipPort.java  # amistades ACCEPTED reales (fix 2026-11-04; antes NoOpFriendshipPort, stub)
│   └── dto/                                        # CreateGroupRequest, GroupResponse, etc.
├── src/main/resources/db/migration/                # V6__create_groups … V10__create_group_activity, V11__create_group_photos (nuevas)
└── src/test/java/com/amano/backend/groups/         # *Test (rápidos) + *IT (Testcontainers)

frontend/
├── src/pages/groups/        # CreateGroupView, GroupListView, GroupInfoView, AddMemberView, GroupHistoryView
├── src/components/groups/   # GroupPermissionsPanel, RemoveMemberDialog, LeaveGroupPanel, AdminRolePanel, MemberListView, GroupDescriptionPanel, GroupPhotoPanel
└── src/…                    # *.test.jsx colocados junto a cada componente

specs/, compose.yaml, Caddyfile, .specify/   # según repo existente (sin cambios estructurales)
```

**Structure Decision**: Monorepo web existente (backend + frontend). El módulo nuevo vive en `com.amano.backend.groups` con un sub-package `port/` para las dos dependencias externas pendientes (deudas, amistades); no se crean nuevos proyectos ni apps.

## Complexity Tracking

| Violation (justified) | Why needed | Simpler alternative rejected because |
|---|---|---|
| Puertos `DebtPort` / `FriendshipPort` con stubs (`FriendshipPort` ya usa amistades reales desde el fix del 2026-11-04; `DebtPort` sigue siendo stub) (patrón de puerto/adapter en el sub-package `port/`, inédito en el repo) | El spec (FR-010/FR-013 y FR-006) requiere reglas de deudas y de amistad cuyos módulos **todavía no existen** en el repo; el principio I prohíbe duplicar infraestructura o inventar entidades ajenas al scope | Acceso directo a un módulo inexistente rompería la construcción; duplicar lógica de amistad/deudas internamente crearía soluciones paralelas para un mismo problema (principio I) — el puerto concentra el acople futuro en un punto |
| Sin unicidad de nombre de grupo (contrato previo decía unique 2-50) | Decisión explícita del usuario (2026-10-27): el spec solo exige obligatorio/no vacío; asumir unicidad/longitud inventa un requisito | Constraint DB unique habría impuesto un error (409/400) ante nombres repetidos sin justificación funcional en el spec |
