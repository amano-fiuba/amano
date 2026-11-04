# Implementation Plan: Ver listado de amigos

**Branch**: `feat/visualizar-listado-amigos` | **Date**: 2026-11-01 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/013-friends-list/spec.md`

## Summary

La tarea "Ver listado de amigos" (Sprint 3, 1 SP) pide visualizar el catálogo completo de amigos. 011 y 012 dejaron el dominio listo: una amistad es una solicitud `ACCEPTED`. **US1**: `GET /api/users/me/friends` devuelve, con una sola consulta JPQL, la otra cuenta de cada amistad de la sesión (id, nombre, apellido, correo, fecha de aceptación) en orden alfabético; la pantalla `/friends` muestra la nueva sección "Mis amigos" con contador. **US2**: la consulta excluye pendientes, rechazadas y amistades de terceros; el estado vacío invita a "Buscar usuarios". **US3**: aceptar una solicitud recarga la lista; 401 heredado de `/api/users/**`; error con reintento independiente de "Solicitudes". **Sin migración** ni dependencias nuevas; grupos (010) no cambia.

## Technical Context

**Language/Version**: Java 25 (backend); JavaScript con React 19.2 (frontend)

**Primary Dependencies**: Spring Boot 4.1.1 (Web MVC, Data JPA, Security); React 19.2, react-router 8.4, Vite 8.3. **Sin dependencias nuevas.**

**Storage**: PostgreSQL. **Cero migraciones**; solo lectura de `friendship_requests` (`V12`) y `users`.

**Testing**: JUnit 5 + Mockito (`FriendServiceTest`), Spring Boot Test + Testcontainers (`FriendListIT`), Vitest + Testing Library (`FriendsList.test.jsx`, ampliaciones de `friendsClient.test.js` y `FriendsPage.test.jsx`). Cobertura JaCoCo y Vitest ≥ 85 %.

**Target Platform**: Servidor Linux vía Docker Compose; navegador web desktop y móvil.

**Project Type**: Aplicación web monorepo (backend REST + frontend SPA).

**Performance Goals**: una consulta con `join` por id; volumen bajo por persona. SC-001 (< 2 s) sin optimización adicional.

**Constraints**: no tocar 008/010/011/012 salvo la recarga tras aceptar; sin migraciones; sesión verificada; sin datos privados; voseo; cobertura ≥ 85 %.

**Scale/Scope**: 0 migraciones, 1 endpoint, 3 archivos backend nuevos y 1 modificado, 2 archivos frontend nuevos y 4 modificados.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principio | Estado | Evidencia |
|---|-----------|--------|-----------|
| I | Arquitectura y separación de responsabilidades | PASS | Paquete `friendship/` existente; controlador y servicio propios; reutiliza repositorio, `SecurityConfig`, `ProtectedRoute`, estilos de la pantalla "Amigos" |
| II | Desarrollo guiado por especificaciones | PASS | `specs/013-friends-list/` completa; FR citados en contrato, tareas y pruebas |
| III | Entrega incremental por historia | PASS | US1 → US2 → US3 con checkpoint (tasks.md) |
| IV | Alcance controlado | PASS | Sin eliminar amistades, sin búsqueda, sin paginación, sin grupos (research.md D8) |
| V | Calidad, pruebas y cobertura | PASS | Unitarios, integración con Testcontainers y Vitest; umbral 85 % |
| VI | Integridad de las reglas de negocio | PASS | Solo lectura; la unicidad de amistades la garantiza `uq_friendship_requests_accepted_pair` |
| VII | Contratos REST y compatibilidad | PASS | `contracts/friends-api.md`; endpoint aditivo |
| VIII | Validación y manejo de errores | PASS | Sin entradas del cliente; 401 con ProblemDetail; errores de UI con mensaje fijo |
| IX | Seguridad y control de acceso | PASS | Usuario solo de la sesión; proyección sin datos privados |
| X | Código mantenible y convenciones | PASS | Nombres en inglés, `*Test`/`*IT`, `*.test.jsx` |
| XI | Entorno reproducible | PASS | Sin propiedades, dependencias ni migraciones nuevas |
| XII | Revisión por pares | PENDIENTE (posterior) | PR aún no abierto |
| XIII | Commits y trazabilidad | PENDIENTE (posterior) | Los commits los realiza el equipo |

**Gate resultado**: PASS.

## Project Structure

### Documentation (this feature)

```text
specs/013-friends-list/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/friends-api.md
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
backend/src/main/java/com/amano/backend/friendship/
├── FriendController.java            # A: GET /api/users/me/friends
├── FriendService.java               # A: lista de amigos de la sesión
├── FriendResponse.java              # A: id, nombre, apellido, email, friendsSince
└── FriendshipRequestRepository.java # M: findFriendsOf(userId)

backend/src/test/java/com/amano/backend/friendship/
├── FriendServiceTest.java           # A: unitario
└── FriendListIT.java                # A: contrato HTTP con Testcontainers

frontend/src/
├── api/friendsClient.js             # M: listFriends + FRIENDS_NETWORK_ERROR_MESSAGE
├── api/friendsClient.test.js        # M
├── components/FriendsList.jsx       # A: sección "Mis amigos"
├── components/FriendsList.test.jsx  # A
├── pages/FriendsPage.jsx            # M: renderiza FriendsList y la recarga al aceptar
├── pages/FriendsPage.test.jsx       # M
└── index.scss                       # M: estilos de la sección
```

**Structure Decision**: aplicación web (backend + frontend) existente; no se crean módulos ni paquetes nuevos.

## Complexity Tracking

| Elemento | Detalle | Justificación / acción |
|----------|---------|------------------------|
| `join ... on` sin relación JPA | La entidad `FriendshipRequest` no tiene relaciones a `User` (decisión de 011) | Una consulta con proyección evita N+1 y datos privados (research.md D3) |
| Recarga en vez de inserción local | Aceptar vuelve a pedir la lista | Mantiene orden y fecha del servidor con una línea de código (research.md D7) |
| Grupos sin integrar | `NoOpFriendshipPort` sigue activo (reemplazado el 2026-11-04 por `AcceptedFriendshipPort`, fix de 010) | Cambiarlo afecta 010 y sus pruebas; historia aparte (research.md D8) |
